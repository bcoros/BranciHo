package com.branciho.citiesinlife.sim;

import com.branciho.citiesinlife.city.City;
import com.branciho.citiesinlife.city.CityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Infinite money for anybody building in creative.
 *
 * <p>Creative mode already says "stop asking me for materials". Asking the same player for two and
 * a half thousand a chunk is the mod insisting on a currency the rest of the game has already
 * waved away, and it is what made laying a city out just to see how it looks such a chore.
 *
 * <p>Done by topping the treasury up rather than by exempting each purchase. Every price in the mod
 * ends up going through the same treasury, including the ones written after this, so filling it is
 * the one change that cannot be forgotten about later.
 *
 * <p>It follows the player rather than being a switch on the world: leave creative and the city goes
 * straight back to the money it actually earned, with everything it made while you were away already
 * banked. Shift+I turns it off without leaving creative, for anybody who would rather play properly
 * with the blocks to hand.
 */
public final class CreativeFunding {

    /** How often the check runs. Once a second, so switching game mode is felt immediately. */
    private static final int INTERVAL_TICKS = 20;

    private CreativeFunding() {
    }

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % INTERVAL_TICKS == 0) {
            sync(server);
        }
    }

    /**
     * Bring every city's funding into line with what its owner is doing right now.
     *
     * <p>Also called the moment somebody presses the key, so the answer is on screen before they
     * have let go of it.
     */
    public static void sync(MinecraftServer server) {
        CityData data = CityData.get(server);
        boolean changed = false;
        for (City city : data.cities()) {
            boolean funded = shouldFund(server, data, city);
            changed |= city.setCreativeFunded(funded);
            if (funded) {
                changed |= city.refillCreative();
            }
        }
        if (changed) {
            data.setDirty();
        }
    }

    /**
     * A city is funded while its owner is here, in creative, and has not turned it off.
     *
     * <p>Offline counts as not funded on purpose. A city whose owner has logged out should be
     * spending its own money, not a billion nobody is watching.
     */
    private static boolean shouldFund(MinecraftServer server, CityData data, City city) {
        if (!data.creativeMoneyEnabled(city.owner())) {
            return false;
        }
        ServerPlayer owner = server.getPlayerList().getPlayer(city.owner());
        return owner != null && owner.isCreative();
    }
}
