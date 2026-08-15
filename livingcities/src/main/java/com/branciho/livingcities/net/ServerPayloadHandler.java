package com.branciho.livingcities.net;

import com.branciho.livingcities.blockentity.CityHallCoreBlockEntity;
import com.branciho.livingcities.city.City;
import com.branciho.livingcities.city.CityRegistry;
import com.branciho.livingcities.city.CityRole;
import com.branciho.livingcities.config.LivingCitiesConfig;
import com.branciho.livingcities.net.payload.CitySummaryPayload;
import com.branciho.livingcities.net.payload.ClaimChunkPayload;
import com.branciho.livingcities.net.payload.CreateCityPayload;
import com.branciho.livingcities.net.payload.OpenCityScreenPayload;
import com.branciho.livingcities.net.payload.RequestCityDataPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.util.Locale;
import java.util.UUID;

/**
 * Every client request lands here, and every one of them is re-validated from scratch.
 *
 * <p>The rule is simple: the packet says what the player <em>wants</em>. This class decides what
 * actually happens, using only server state. Nothing in a payload is trusted - not the position, not
 * the name, not the fact that the client thought the button should be clickable.
 */
public final class ServerPayloadHandler {

    /** How far a player may be from a block they claim to be interacting with. */
    private static final double MAX_INTERACT_DISTANCE_SQR = 64.0D;

    private ServerPayloadHandler() {
    }

    // ------------------------------------------------------------------ create city

    public static void createCity(ServerPlayer player, CreateCityPayload payload) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        BlockPos pos = payload.corePos();

        if (player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) > MAX_INTERACT_DISTANCE_SQR) {
            deny(player, "message.livingcities.too_far");
            return;
        }
        if (!level.isLoaded(pos)) {
            deny(player, "message.livingcities.too_far");
            return;
        }
        if (!(level.getBlockEntity(pos) instanceof CityHallCoreBlockEntity core)) {
            deny(player, "message.livingcities.no_core");
            return;
        }

        String name = sanitiseName(payload.cityName());
        if (name.isEmpty()) {
            deny(player, "message.livingcities.bad_name");
            return;
        }

        CityRegistry registry = CityRegistry.get(server);
        if (core.cityId() != null && registry.byId(core.cityId()) != null) {
            deny(player, "message.livingcities.core_in_use");
            return;
        }
        if (registry.nameTaken(name)) {
            deny(player, "message.livingcities.name_taken");
            return;
        }

        ChunkPos chunk = new ChunkPos(pos);
        City existingOwner = registry.byChunk(level.dimension(), chunk);
        if (existingOwner != null) {
            deny(player, "message.livingcities.chunk_owned");
            return;
        }

        City city = new City(
                UUID.randomUUID(),
                name,
                player.getUUID(),
                level.dimension(),
                pos,
                LivingCitiesConfig.SERVER.startingTreasury.get());
        registry.addCity(city);
        core.setCityId(city.id());

        player.displayClientMessage(
                Component.translatable("message.livingcities.city_founded", name).withStyle(ChatFormatting.GREEN), false);
        LivingCitiesNetwork.sendTo(player,
                new OpenCityScreenPayload(OpenCityScreenPayload.Screen.MANAGEMENT, pos));
        sendSummary(player, city);
    }

    // ------------------------------------------------------------------ claim chunk

    public static void claimChunk(ServerPlayer player, ClaimChunkPayload payload) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        ChunkPos target = payload.chunkPos();

        // A player may only alter territory near themselves; this kills remote map-painting exploits.
        ChunkPos playerChunk = player.chunkPosition();
        if (Math.abs(playerChunk.x - target.x) > 8 || Math.abs(playerChunk.z - target.z) > 8) {
            deny(player, "message.livingcities.too_far");
            return;
        }

        CityRegistry registry = CityRegistry.get(server);
        City city = registry.citiesOf(player.getUUID()).stream()
                .filter(c -> c.dimension().equals(level.dimension()))
                .findFirst()
                .orElse(null);
        if (city == null) {
            deny(player, "message.livingcities.no_city");
            return;
        }
        if (!hasPermission(player, city, CityRole.ADMINISTRATOR)) {
            deny(player, "message.livingcities.no_permission");
            return;
        }

        if (!payload.claim()) {
            if (city.owns(target)) {
                registry.unclaim(city, target);
                sendSummary(player, city);
            }
            return;
        }

        City currentOwner = registry.byChunk(level.dimension(), target);
        if (currentOwner != null) {
            deny(player, currentOwner.id().equals(city.id())
                    ? "message.livingcities.already_claimed"
                    : "message.livingcities.chunk_owned");
            return;
        }

        int maxClaims = LivingCitiesConfig.SERVER.maxClaimsPerCity.get();
        if (maxClaims > 0 && city.claimCount() >= maxClaims) {
            deny(player, "message.livingcities.claim_limit");
            return;
        }
        if (LivingCitiesConfig.SERVER.requireAdjacentClaims.get() && !city.isAdjacentToTerritory(target)) {
            deny(player, "message.livingcities.not_adjacent");
            return;
        }

        long cost = claimCost(city.claimCount());
        boolean free = LivingCitiesConfig.SERVER.creativeBypassesCost.get() && player.isCreative();
        if (!free && !city.withdraw(cost)) {
            player.displayClientMessage(Component.translatable("message.livingcities.cannot_afford",
                    formatMoney(cost)).withStyle(ChatFormatting.RED), true);
            return;
        }

        if (!registry.claim(city, target)) {
            // Lost a race with another claim; refund rather than silently charging.
            if (!free) {
                city.deposit(cost);
            }
            deny(player, "message.livingcities.chunk_owned");
            return;
        }

        player.displayClientMessage(Component.translatable("message.livingcities.chunk_claimed",
                target.x, target.z, free ? "0" : formatMoney(cost)).withStyle(ChatFormatting.GREEN), true);
        sendSummary(player, city);
    }

    /** Price escalates with territory size so a city cannot cheaply swallow the map. */
    public static long claimCost(int alreadyOwned) {
        long base = LivingCitiesConfig.SERVER.baseChunkClaimCost.get();
        double growth = LivingCitiesConfig.SERVER.claimCostGrowth.get();
        double scaled = base * Math.pow(growth, Math.max(0, alreadyOwned));
        return (long) Math.min(scaled, Long.MAX_VALUE / 4.0D);
    }

    // ------------------------------------------------------------------ request data

    /**
     * Resolve which city this player may look at: the one they are standing in if they belong to it,
     * otherwise their own. Standing in someone else's territory shows nothing, so the hotkey cannot be
     * used to scout a rival's finances.
     */
    public static void requestCityData(ServerPlayer player, RequestCityDataPayload payload) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        CityRegistry registry = CityRegistry.get(server);
        ServerLevel level = player.serverLevel();

        City here = registry.byChunk(level.dimension(), player.chunkPosition());
        City visible = here != null && hasPermission(player, here, CityRole.CITIZEN)
                ? here
                : registry.citiesOf(player.getUUID()).stream().findFirst().orElse(null);

        if (visible == null) {
            if (payload.openScreen()) {
                LivingCitiesNetwork.sendTo(player,
                        new OpenCityScreenPayload(OpenCityScreenPayload.Screen.MANAGEMENT, player.blockPosition()));
            }
            return;
        }

        sendSummary(player, visible);
        if (payload.openScreen()) {
            LivingCitiesNetwork.sendTo(player,
                    new OpenCityScreenPayload(OpenCityScreenPayload.Screen.MANAGEMENT, visible.corePos()));
        }
    }

    // ------------------------------------------------------------------ helpers

    public static void sendSummary(ServerPlayer player, City city) {
        LivingCitiesNetwork.sendTo(player, new CitySummaryPayload(
                city.id(),
                city.name(),
                city.stats().population(),
                city.stats().housingCapacity(),
                city.stats().jobs(),
                city.stats().employed(),
                city.treasuryCents(),
                city.stats().dailyIncomeCents(),
                city.stats().dailyExpenseCents(),
                city.stats().happinessPermille(),
                city.stats().powerSatisfactionPermille(),
                city.claimCount(),
                city.buildingIds().size()));
    }

    /** Operators bypass city roles; everyone else needs the rank. */
    public static boolean hasPermission(ServerPlayer player, City city, CityRole required) {
        return player.hasPermissions(2) || city.hasPermission(player.getUUID(), required);
    }

    private static void deny(ServerPlayer player, String translationKey) {
        player.displayClientMessage(Component.translatable(translationKey).withStyle(ChatFormatting.RED), true);
    }

    /**
     * Names arrive from a client text box, so they are length-capped and stripped of control and
     * formatting characters before they can ever be stored or shown to other players.
     */
    private static String sanitiseName(String raw) {
        String trimmed = raw.trim();
        StringBuilder cleaned = new StringBuilder(trimmed.length());
        for (int i = 0; i < trimmed.length() && cleaned.length() < CreateCityPayload.MAX_NAME_LENGTH; i++) {
            char c = trimmed.charAt(i);
            if (c >= ' ' && c != 127 && c != '§') {
                cleaned.append(c);
            }
        }
        return cleaned.toString().trim();
    }

    public static String formatMoney(long cents) {
        return String.format(Locale.ROOT, "$%,d.%02d", cents / 100L, Math.abs(cents % 100L));
    }
}
