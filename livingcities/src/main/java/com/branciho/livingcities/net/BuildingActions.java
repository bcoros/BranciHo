package com.branciho.livingcities.net;

import com.branciho.livingcities.building.Building;
import com.branciho.livingcities.building.Floor;
import com.branciho.livingcities.building.FloorFlag;
import com.branciho.livingcities.building.ZoneUse;
import com.branciho.livingcities.city.City;
import com.branciho.livingcities.city.CityRegistry;
import com.branciho.livingcities.city.CityRole;
import com.branciho.livingcities.config.LivingCitiesConfig;
import com.branciho.livingcities.net.payload.AssignBuildingPayload;
import com.branciho.livingcities.net.payload.BuildingDetailPayload;
import com.branciho.livingcities.net.payload.RescanBuildingPayload;
import com.branciho.livingcities.net.payload.SetFloorZonePayload;
import com.branciho.livingcities.scan.BuildingScanService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side handling for registering and editing buildings.
 *
 * <p>This is the heart of the mod's premise: a player points at something they built and it becomes a
 * functional city building. It is also the most abusable surface in the mod, because it accepts two
 * arbitrary coordinates from a client and turns them into work. Every request is therefore re-derived
 * from server state, and the expensive part (a geometry scan) is rate limited per player.
 */
public final class BuildingActions {

    /** How far a player may be from a corner they claim to have selected. */
    private static final double MAX_CORNER_DISTANCE_SQR = 128.0D * 128.0D;

    /**
     * Minimum gap between scan-triggering requests from one player.
     *
     * <p>A scan walks up to half a million blocks. Without this, a modified client could queue them in
     * a loop and stall the server without ever doing anything the vanilla protocol would flag.
     */
    private static final long SCAN_COOLDOWN_MS = 3_000L;

    private static final Map<UUID, Long> LAST_SCAN_REQUEST = new HashMap<>();

    private BuildingActions() {
    }

    /** Clear per-player rate limiting; called from the server lifecycle hooks. */
    public static void reset() {
        LAST_SCAN_REQUEST.clear();
    }

    // ------------------------------------------------------------------ register

    public static void assignBuilding(ServerPlayer player, AssignBuildingPayload payload) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        CityRegistry registry = CityRegistry.get(server);

        BlockPos a = payload.cornerA();
        BlockPos b = payload.cornerB();

        if (tooFar(player, a) || tooFar(player, b)) {
            deny(player, "message.livingcities.too_far");
            return;
        }

        Building candidate = Building.fromCorners(UUID.randomUUID(), null, "", a, b);

        long volume = candidate.volume();
        if (volume > LivingCitiesConfig.SERVER.maxSelectionVolume.get().longValue()) {
            deny(player, "message.livingcities.scan_too_large");
            return;
        }
        if (candidate.sizeY() > LivingCitiesConfig.SERVER.maxSelectionHeight.get()) {
            deny(player, "message.livingcities.scan_too_tall");
            return;
        }

        // Which city owns the ground this selection sits on? Derived from the chunk index, never from
        // anything the client said, and it must be one city for the whole footprint.
        City owner = null;
        for (ChunkPos chunk : candidate.occupiedChunks()) {
            City chunkOwner = registry.byChunk(level.dimension(), chunk);
            if (chunkOwner == null) {
                deny(player, "message.livingcities.building_unclaimed");
                return;
            }
            if (owner == null) {
                owner = chunkOwner;
            } else if (!owner.id().equals(chunkOwner.id())) {
                deny(player, "message.livingcities.building_spans_cities");
                return;
            }
        }
        if (owner == null) {
            deny(player, "message.livingcities.building_unclaimed");
            return;
        }
        if (!ServerPayloadHandler.hasPermission(player, owner, CityRole.BUILDER)) {
            deny(player, "message.livingcities.no_permission");
            return;
        }

        candidate.setCityId(owner.id());
        Building overlap = registry.findOverlap(level.dimension(), candidate);
        if (overlap != null) {
            player.displayClientMessage(Component.translatable("message.livingcities.building_overlaps",
                    overlap.name()).withStyle(ChatFormatting.RED), true);
            return;
        }

        if (!allowScan(player)) {
            deny(player, "message.livingcities.scan_cooldown");
            return;
        }

        candidate.setName(chooseName(payload.name(), registry, owner));
        registry.addBuilding(owner, candidate);

        player.displayClientMessage(Component.translatable("message.livingcities.building_registered",
                candidate.name()).withStyle(ChatFormatting.GREEN), false);

        // Capacity stays zero until the scan lands; the panel shows it as still being measured.
        BuildingScanService.get(server).enqueue(level, candidate, player);
        sendDetail(player, candidate);
    }

    // ------------------------------------------------------------------ edit

    public static void setFloorZone(ServerPlayer player, SetFloorZonePayload payload) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        CityRegistry registry = CityRegistry.get(server);
        Building building = registry.building(payload.buildingId());
        if (building == null) {
            deny(player, "message.livingcities.building_missing");
            return;
        }
        City city = registry.byId(building.cityId());
        if (city == null || !ServerPayloadHandler.hasPermission(player, city, CityRole.BUILDER)) {
            deny(player, "message.livingcities.no_permission");
            return;
        }

        List<Floor> floors = building.floors();
        int index = payload.floorIndex();
        if (index < 0 || index >= floors.size()) {
            deny(player, "message.livingcities.floor_missing");
            return;
        }

        // An unknown id means an older or newer client, not a licence to guess; leave the floor alone.
        ZoneUse requested = ZoneUse.byId(payload.zoneId(), null);
        if (requested == null) {
            deny(player, "message.livingcities.zone_unknown");
            return;
        }

        Floor floor = floors.get(index);
        if (floor.has(FloorFlag.ROOF) && requested.providesHousing()) {
            // Not a hard error: the flag is a measurement, and the player may have roofed it since.
            player.displayClientMessage(Component.translatable("message.livingcities.zone_roof_warning")
                    .withStyle(ChatFormatting.YELLOW), true);
        }
        floor.setUse(requested);
        registry.setDirty();
        sendDetail(player, building);
    }

    public static void rescanBuilding(ServerPlayer player, RescanBuildingPayload payload) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        CityRegistry registry = CityRegistry.get(server);
        Building building = registry.building(payload.buildingId());
        if (building == null) {
            deny(player, "message.livingcities.building_missing");
            return;
        }
        City city = registry.byId(building.cityId());
        if (city == null || !ServerPayloadHandler.hasPermission(player, city, CityRole.BUILDER)) {
            deny(player, "message.livingcities.no_permission");
            return;
        }
        if (!allowScan(player)) {
            deny(player, "message.livingcities.scan_cooldown");
            return;
        }
        BuildingScanService.get(server).enqueue(player.serverLevel(), building, player);
    }

    // ------------------------------------------------------------------ detail

    public static void sendDetail(ServerPlayer player, Building building) {
        List<Floor> floors = building.floors();
        int shown = Math.min(floors.size(), BuildingDetailPayload.MAX_FLOOR_ROWS);
        List<BuildingDetailPayload.FloorRow> rows = new ArrayList<>(shown);

        for (int i = 0; i < shown; i++) {
            Floor floor = floors.get(i);
            StringBuilder flags = new StringBuilder();
            for (FloorFlag flag : floor.flags()) {
                if (flags.length() > 0) {
                    flags.append(',');
                }
                flags.append(flag.name());
            }
            rows.add(new BuildingDetailPayload.FloorRow(
                    i, floor.floorY(), floor.yMin(), floor.yMax(),
                    floor.usableCells(), floor.ceilingHeight(),
                    floor.use().id(), flags.toString(),
                    floor.residents(), floor.jobs()));
        }

        LivingCitiesNetwork.sendTo(player, new BuildingDetailPayload(
                building.id(),
                building.name(),
                building.isMixedUse(),
                building.needsRescan(),
                building.totalUsableCells(),
                building.housingCapacity(),
                building.residents(),
                building.jobCapacity(),
                building.workers(),
                Math.max(0, floors.size() - shown),
                rows));
    }

    // ------------------------------------------------------------------ helpers

    private static boolean tooFar(ServerPlayer player, BlockPos pos) {
        return player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D)
                > MAX_CORNER_DISTANCE_SQR;
    }

    private static boolean allowScan(ServerPlayer player) {
        long now = System.currentTimeMillis();
        Long last = LAST_SCAN_REQUEST.get(player.getUUID());
        if (last != null && now - last < SCAN_COOLDOWN_MS) {
            return false;
        }
        LAST_SCAN_REQUEST.put(player.getUUID(), now);
        return true;
    }

    /** Use the player's name if they gave a usable one, otherwise number it within the city. */
    private static String chooseName(String requested, CityRegistry registry, City city) {
        String cleaned = requested == null ? "" : requested.trim();
        StringBuilder safe = new StringBuilder(cleaned.length());
        for (int i = 0; i < cleaned.length() && safe.length() < AssignBuildingPayload.MAX_NAME_LENGTH; i++) {
            char c = cleaned.charAt(i);
            if (c >= ' ' && c != 127 && c != '§') {
                safe.append(c);
            }
        }
        String name = safe.toString().trim();
        return name.isEmpty() ? "Building " + (registry.buildingsOf(city).size() + 1) : name;
    }

    private static void deny(ServerPlayer player, String translationKey) {
        player.displayClientMessage(Component.translatable(translationKey).withStyle(ChatFormatting.RED), true);
    }
}
