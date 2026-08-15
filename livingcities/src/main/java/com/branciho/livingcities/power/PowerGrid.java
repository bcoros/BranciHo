package com.branciho.livingcities.power;

import com.branciho.livingcities.LivingCities;
import com.branciho.livingcities.building.Building;
import com.branciho.livingcities.city.CityRegistry;
import com.branciho.livingcities.config.LivingCitiesConfig;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Discovers and maintains the electrical grids in a level.
 *
 * <p>Grids are walked from substations outward, never from cables, so a stretch of wire nobody has
 * connected to anything costs nothing to have lying around.
 *
 * <p>The walk is the expensive part, so it does not happen on a tick. It happens when the world says
 * something electrical changed, and at most once every {@link #MIN_REBUILD_INTERVAL_TICKS} - a player
 * laying a cable run places a hundred blocks in a few seconds, and rebuilding per block would be a
 * hundred full walks.
 */
public final class PowerGrid {

    /** Hard ceiling on blocks visited per grid, so a pathological wire maze cannot hang the server. */
    private static final int MAX_NETWORK_SIZE = 20_000;

    /** Minimum ticks between rebuilds, however often the world reports a change. */
    private static final int MIN_REBUILD_INTERVAL_TICKS = 40;

    private static final Map<MinecraftServer, PowerGrid> INSTANCES = new IdentityHashMap<>();

    private final Map<ResourceKey<Level>, List<PowerNetwork>> networks = new HashMap<>();
    private final Map<ResourceKey<Level>, Boolean> dirty = new HashMap<>();

    private long lastRebuildTick = Long.MIN_VALUE;

    private PowerGrid() {
    }

    public static PowerGrid get(MinecraftServer server) {
        return INSTANCES.computeIfAbsent(server, ignored -> new PowerGrid());
    }

    public static void shutdown(MinecraftServer server) {
        INSTANCES.remove(server);
    }

    public static void resetAll() {
        INSTANCES.clear();
    }

    /** Called when an electrical block is placed or broken. */
    public void markDirty(ResourceKey<Level> dimension) {
        dirty.put(dimension, Boolean.TRUE);
    }

    public List<PowerNetwork> networks(ResourceKey<Level> dimension) {
        return networks.getOrDefault(dimension, List.of());
    }

    /**
     * The grid covering this position, or null if no substation reaches it.
     *
     * <p>Coverage is by distance from a substation rather than by wiring, which is the whole point of
     * a substation: the spec is explicit that nobody should be running a cable into every apartment.
     */
    public PowerNetwork networkCovering(ServerLevel level, BlockPos pos) {
        for (PowerNetwork network : networks(level.dimension())) {
            for (BlockPos substation : network.substations()) {
                BlockState state = level.getBlockState(substation);
                if (!(state.getBlock() instanceof PowerComponent component)) {
                    continue;
                }
                int radius = component.coverageRadius();
                if (substation.distSqr(pos) <= (double) radius * radius) {
                    return network;
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ rebuild

    public void tick(MinecraftServer server, CityRegistry registry) {
        long tick = server.overworld().getGameTime();
        boolean anyDirty = dirty.values().stream().anyMatch(Boolean::booleanValue);
        boolean due = tick - lastRebuildTick >= MIN_REBUILD_INTERVAL_TICKS;
        if (!anyDirty || !due) {
            return;
        }
        lastRebuildTick = tick;

        for (ServerLevel level : server.getAllLevels()) {
            if (!Boolean.TRUE.equals(dirty.get(level.dimension()))) {
                continue;
            }
            dirty.put(level.dimension(), Boolean.FALSE);
            networks.put(level.dimension(), rebuild(level));
        }
        applyDemand(server, registry);
    }

    /**
     * Walk every loaded substation's connected component.
     *
     * <p>Only loaded blocks participate: reading an unloaded chunk here would force generation from a
     * background bookkeeping pass, which is exactly the kind of hidden cost that ruins a server.
     */
    private List<PowerNetwork> rebuild(ServerLevel level) {
        final List<PowerNetwork> result = new ArrayList<>();
        final LongSet visited = new LongOpenHashSet();

        for (BlockPos substation : SubstationIndex.get(level.getServer()).substations(level.dimension())) {
            if (visited.contains(substation.asLong()) || !level.isLoaded(substation)) {
                continue;
            }
            if (!(level.getBlockState(substation).getBlock() instanceof PowerComponent component)
                    || component.powerRole() != PowerRole.SUBSTATION) {
                continue;
            }
            PowerNetwork network = walk(level, substation, visited);
            if (network != null) {
                result.add(network);
            }
        }
        return result;
    }

    private PowerNetwork walk(ServerLevel level, BlockPos start, LongSet visited) {
        final PowerNetwork network = new PowerNetwork();
        final Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        visited.add(start.asLong());

        int examined = 0;
        while (!queue.isEmpty()) {
            if (++examined > MAX_NETWORK_SIZE) {
                LivingCities.LOGGER.warn("Living Cities: power network from {} exceeded {} blocks; truncating",
                        start, MAX_NETWORK_SIZE);
                break;
            }
            BlockPos pos = queue.poll();
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof PowerComponent component)) {
                continue;
            }

            network.addMember(pos);
            switch (component.powerRole()) {
                case SUBSTATION -> network.addSubstation(pos);
                case GENERATOR -> network.addGeneration(component.generationKw(level, pos, state));
                default -> { }
            }

            for (Direction direction : Direction.values()) {
                enqueue(level, queue, visited, pos.relative(direction));
            }
            if (component.powerRole() == PowerRole.PYLON) {
                enqueuePylons(level, queue, visited, pos, component.linkRange());
            }
        }
        return network.substations().isEmpty() ? null : network;
    }

    private void enqueue(ServerLevel level, Deque<BlockPos> queue, LongSet visited, BlockPos pos) {
        if (visited.contains(pos.asLong()) || !level.isLoaded(pos)) {
            return;
        }
        if (level.getBlockState(pos).getBlock() instanceof PowerComponent) {
            visited.add(pos.asLong());
            queue.add(pos.immutable());
        }
    }

    /**
     * Link this pylon to others in range.
     *
     * <p>Scanned as a cube rather than traced as a line: a real wire would need an entity or a block
     * per metre, and the point of a pylon is to span ground the player has not built on.
     */
    private void enqueuePylons(ServerLevel level, Deque<BlockPos> queue, LongSet visited,
                               BlockPos from, int range) {
        if (range <= 0) {
            return;
        }
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -range; dx <= range; dx++) {
            for (int dy = -range; dy <= range; dy++) {
                for (int dz = -range; dz <= range; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    cursor.set(from.getX() + dx, from.getY() + dy, from.getZ() + dz);
                    if (visited.contains(cursor.asLong()) || !level.isLoaded(cursor)) {
                        continue;
                    }
                    if (level.getBlockState(cursor).getBlock() instanceof PowerComponent other
                            && other.powerRole() == PowerRole.PYLON) {
                        visited.add(cursor.asLong());
                        queue.add(cursor.immutable());
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ demand

    /** Attribute every registered building's demand to whichever grid covers it. */
    private void applyDemand(MinecraftServer server, CityRegistry registry) {
        for (List<PowerNetwork> perLevel : networks.values()) {
            for (PowerNetwork network : perLevel) {
                network.resetDemand();
            }
        }

        for (Building building : registry.buildings()) {
            var city = registry.byId(building.cityId());
            if (city == null) {
                continue;
            }
            ServerLevel level = server.getLevel(city.dimension());
            if (level == null) {
                continue;
            }
            int demand = demandOf(building);
            building.setPowerDemandKw(demand);

            PowerNetwork network = networkCovering(level, building.centre());
            if (network == null) {
                building.setPowerSatisfaction(0.0F);
                continue;
            }
            network.addDemand(demand);
        }

        // Satisfaction is only known once every building on a grid has contributed its demand.
        for (Building building : registry.buildings()) {
            var city = registry.byId(building.cityId());
            if (city == null) {
                continue;
            }
            ServerLevel level = server.getLevel(city.dimension());
            if (level == null) {
                continue;
            }
            PowerNetwork network = networkCovering(level, building.centre());
            building.setPowerSatisfaction(network == null ? 0.0F : network.satisfaction());
        }
    }

    /** Households and workplaces both draw power; empty floor area does not. */
    public static int demandOf(Building building) {
        double perResident = LivingCitiesConfig.SERVER.kwPerResident.get();
        double perJob = LivingCitiesConfig.SERVER.kwPerJob.get();
        return (int) Math.ceil(building.housingCapacity() * perResident + building.jobCapacity() * perJob);
    }

}
