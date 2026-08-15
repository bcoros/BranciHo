package com.branciho.livingcities.net;

import com.branciho.livingcities.building.Building;
import com.branciho.livingcities.city.City;
import com.branciho.livingcities.city.CityRegistry;
import com.branciho.livingcities.city.CityRole;
import com.branciho.livingcities.net.payload.CityOverlayPayload;
import com.branciho.livingcities.net.payload.RequestOverlayPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import com.branciho.livingcities.utility.UtilityComponent;
import com.branciho.livingcities.utility.UtilityGrid;
import com.branciho.livingcities.utility.UtilityKind;
import com.branciho.livingcities.utility.UtilityNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Builds the overlay snapshot for the area around a player.
 *
 * <p>Scoped to a radius around the requester rather than sending a whole city, for two reasons: a
 * mature city has thousands of chunks and hundreds of buildings, and territory is information a rival
 * should have to walk to rather than read off a packet.
 */
public final class OverlayActions {

    /** How far around the player overlay data is gathered, in chunks. */
    private static final int RADIUS_CHUNKS = 8;

    private OverlayActions() {
    }

    public static void requestOverlay(ServerPlayer player, RequestOverlayPayload payload) {
        if (!payload.enabled()) {
            // Nothing to do: the client drops its own cache when it turns the overlay off.
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        send(player, CityRegistry.get(server));
    }

    public static void send(ServerPlayer player, CityRegistry registry) {
        ServerLevel level = player.serverLevel();
        MinecraftServer server = level.getServer();
        ChunkPos centre = player.chunkPosition();

        final List<Long> chunks = new ArrayList<>();
        final List<CityOverlayPayload.BuildingBox> boxes = new ArrayList<>();
        final Set<UUID> seenBuildings = new HashSet<>();

        // Territory the player is entitled to see: their own city's. Standing in someone else's
        // borders shows you their buildings' outlines but not the shape of their whole claim.
        City ownCity = registry.citiesOf(player.getUUID()).stream()
                .filter(city -> city.dimension().equals(level.dimension()))
                .findFirst()
                .orElse(null);

        for (int dx = -RADIUS_CHUNKS; dx <= RADIUS_CHUNKS; dx++) {
            for (int dz = -RADIUS_CHUNKS; dz <= RADIUS_CHUNKS; dz++) {
                ChunkPos chunk = new ChunkPos(centre.x + dx, centre.z + dz);

                City owner = registry.byChunk(level.dimension(), chunk);
                if (owner != null && ownCity != null && owner.id().equals(ownCity.id())
                        && chunks.size() < CityOverlayPayload.MAX_CHUNKS) {
                    chunks.add(chunk.toLong());
                }

                for (Building building : registry.buildingsInChunk(level.dimension(), chunk)) {
                    // A building spanning several chunks appears once per chunk it touches.
                    if (!seenBuildings.add(building.id())) {
                        continue;
                    }
                    if (boxes.size() >= CityOverlayPayload.MAX_BUILDINGS) {
                        continue;
                    }
                    boxes.add(new CityOverlayPayload.BuildingBox(
                            building.id(),
                            building.name(),
                            building.dominantUse().id(),
                            building.isMixedUse(),
                            building.needsRescan(),
                            building.isPowered(),
                            building.hasWater(),
                            building.min().getX(), building.min().getY(), building.min().getZ(),
                            building.max().getX(), building.max().getY(), building.max().getZ()));
                }
            }
        }

        LivingCitiesNetwork.sendTo(player, new CityOverlayPayload(
                ownCity != null, chunks, boxes, gatherCoverage(server, level, centre)));
    }

    /**
     * Distributors near the player and how far each reaches.
     *
     * <p>The single most useful thing the overlay can show: a substation's radius is invisible, so
     * "why is this block dark" is otherwise unanswerable without counting blocks by hand.
     */
    private static List<CityOverlayPayload.Coverage> gatherCoverage(MinecraftServer server,
                                                                    ServerLevel level, ChunkPos centre) {
        final List<CityOverlayPayload.Coverage> coverage = new ArrayList<>();
        final UtilityGrid grid = UtilityGrid.get(server);
        final int maxDistance = (RADIUS_CHUNKS + 4) * 16;

        for (UtilityKind kind : UtilityKind.values()) {
            for (UtilityNetwork network : grid.networks(kind, level.dimension())) {
                for (BlockPos distributor : network.distributors()) {
                    if (coverage.size() >= CityOverlayPayload.MAX_COVERAGE) {
                        return coverage;
                    }
                    int dx = distributor.getX() - centre.getMiddleBlockX();
                    int dz = distributor.getZ() - centre.getMiddleBlockZ();
                    if (Math.abs(dx) > maxDistance || Math.abs(dz) > maxDistance) {
                        continue;
                    }
                    if (!(level.getBlockState(distributor).getBlock() instanceof UtilityComponent component)) {
                        continue;
                    }
                    coverage.add(new CityOverlayPayload.Coverage(
                            kind.id(),
                            distributor.getX(), distributor.getY(), distributor.getZ(),
                            component.coverageRadius(),
                            network.isOverloaded()));
                }
            }
        }
        return coverage;
    }

    /** Push a refresh to every online member of a city, e.g. after a building is added or removed. */
    public static void refreshFor(MinecraftServer server, City city) {
        CityRegistry registry = CityRegistry.get(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (city.hasPermission(player.getUUID(), CityRole.CITIZEN) || player.hasPermissions(2)) {
                send(player, registry);
            }
        }
    }
}
