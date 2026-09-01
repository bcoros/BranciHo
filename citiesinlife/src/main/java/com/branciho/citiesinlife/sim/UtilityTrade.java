package com.branciho.citiesinlife.sim;

import com.branciho.citiesinlife.city.City;
import com.branciho.citiesinlife.city.CityData;
import com.branciho.citiesinlife.city.Diplomacy;
import com.branciho.citiesinlife.city.Pact;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Power and water crossing a border, for money.
 *
 * <p>Run as its own pass after every city has worked out what it makes and what it needs, because
 * a trade is the one thing in this simulation that cannot be decided from inside a single city's
 * update. Both halves of the answer have to already exist.
 *
 * <p>What is sold is <em>surplus</em>, and only surplus. An exporter never goes short to supply
 * somebody else and never has its own numbers reduced by a sale — a city with a nuclear plant is
 * selling the four hundred units a step it was throwing away, which is exactly why the plant is
 * worth building on a server and why the whole thing needed to exist. What it costs the seller is
 * nothing; what it earns them is a running income, and the buyer's alternative was building their
 * own plant.
 *
 * <p>Money is settled every step and never owed. A city that cannot pay simply stops receiving,
 * that step, with the supply resuming the moment it can afford it again — no debt, no bailiff, and
 * no way for an export deal to quietly bankrupt somebody who stopped paying attention.
 */
public final class UtilityTrade {

    /** Told at most this often, so a broke city is warned rather than shouted at. */
    private static final int WARN_INTERVAL_TICKS = CitySimulation.INTERVAL_TICKS * 6;

    private UtilityTrade() {
    }

    public static void run(MinecraftServer server, CityData data) {
        // How much each seller still has left to sell this step. Tracked here rather than read off
        // the city each time, because two neighbours buying from one plant must not both be sold
        // the same spare four hundred units.
        Map<UUID, int[]> spare = new HashMap<>();

        for (City seller : data.cities()) {
            for (City buyer : data.cities()) {
                if (seller.id().equals(buyer.id())) {
                    continue;
                }
                if (!Diplomacy.pactActive(seller, buyer, Pact.UTILITIES)) {
                    continue;
                }
                // Utilities do not cross a dimension. The grids they come off cannot either, and a
                // deal that quietly worked between worlds would be the only thing in the mod that
                // did.
                if (!seller.dimension().equals(buyer.dimension())) {
                    continue;
                }
                trade(server, seller, buyer, spare);
            }
        }
    }

    private static void trade(MinecraftServer server, City seller, City buyer,
                              Map<UUID, int[]> spare) {
        int[] left = spare.computeIfAbsent(seller.id(), id -> new int[]{
                Math.max(0, seller.powerProduced() - seller.powerNeeded()),
                Math.max(0, seller.waterSupplied() - seller.waterNeeded())});

        int wantPower = Math.max(0, buyer.powerNeeded() - buyer.powerProduced());
        int wantWater = Math.max(0, buyer.waterNeeded() - buyer.waterSupplied());

        int power = Math.min(left[0], wantPower);
        int water = Math.min(left[1], wantWater);
        if (power <= 0 && water <= 0) {
            return;
        }

        long powerPrice = seller.powerPrice(buyer.id());
        long waterPrice = seller.waterPrice(buyer.id());

        // Trim the order to what the buyer can actually pay for, rather than refusing the lot. Half
        // a city's power is worth having, and an all-or-nothing rule would make a price rise read
        // as the supply being cut off.
        long budget = buyer.treasury();
        if (powerPrice > 0) {
            power = (int) Math.min(power, budget / powerPrice);
            budget -= (long) power * powerPrice;
        }
        if (waterPrice > 0) {
            water = (int) Math.min(water, Math.max(0L, budget) / waterPrice);
        }

        long bill = (long) power * powerPrice + (long) water * waterPrice;
        if (bill > 0 && !buyer.withdraw(bill)) {
            return;
        }
        if (power <= 0 && water <= 0) {
            warn(server, buyer, seller);
            return;
        }
        seller.deposit(bill);

        // Added to the buyer, not taken off the seller. They are selling what they were not using;
        // showing a plant as producing less because somebody bought its spare would be a lie about
        // the plant.
        buyer.setPower(buyer.powerProduced() + power, buyer.powerNeeded());
        buyer.setWater(buyer.waterSupplied() + water, buyer.waterNeeded());
        buyer.addImports(power, water);
        left[0] -= power;
        left[1] -= water;

        // Short of what they asked for and out of money for the rest: worth saying, because from
        // inside the buyer's city this is indistinguishable from the seller having cut them off.
        if ((power < wantPower || water < wantWater) && bill > 0 && !buyer.canAfford(bill)) {
            warn(server, buyer, seller);
        }
    }

    private static void warn(MinecraftServer server, City buyer, City seller) {
        if (server.getTickCount() % WARN_INTERVAL_TICKS != 0) {
            return;
        }
        ServerPlayer owner = server.getPlayerList().getPlayer(buyer.owner());
        if (owner != null) {
            owner.sendSystemMessage(Component.translatable(
                    "message.citiesinlife.cannot_pay_utilities", seller.name()));
        }
    }
}
