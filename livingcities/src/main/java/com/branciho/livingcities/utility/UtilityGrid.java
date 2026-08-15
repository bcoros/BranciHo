package com.branciho.livingcities.utility;

import com.branciho.livingcities.LivingCities;
import com.branciho.livingcities.building.Building;
import com.branciho.livingcities.city.City;
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
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Discovers and maintains every utility network on a server.
 *
 * <p>Networks are walked outward from distributors, never from pipes or cables, so infrastructure
 * nobody has connected to anything costs nothing to leave lying around.
 *
 * <p>The walk is the expensive part, so it does not run on a tick. It runs when the world reports an
 * electrical or plumbing change, and at most once every {@link #MIN_REBUILD_INTERVAL_TICKS} - laying a
 * pipe run places a hundred blocks in seconds, and rebuilding per block would be a hundred full walks.
 */
public final class UtilityGrid {

    /** Hard ceiling on blocks visited per network, so a pathological maze cannot hang the server. */
    private static final int MAX_NETWORK_SIZE = 20_000;

    /** Minimum ticks between rebuilds, however often the world reports a change. */
    private static final int MIN_REBUILD_INTERVAL_TICKS = 40;

    private static final Map<MinecraftServer, UtilityGrid> INSTANCES = new IdentityHashMap<>();

    private final Map<UtilityKind, Map<ResourceKey<Level>, List<UtilityNetwork>>> networks =
            new EnumMap<>(UtilityKind.class);
    private final Map<UtilityKind, Map<ResourceKey<Level>, Boolean>> dirty = new EnumMap<>(UtilityKind.class);

    private long lastRebuildTick = Long.MIN_VALUE;

    private UtilityGrid() {
    }

    public static UtilityGrid get(MinecraftServer server) {
        return INSTANCES.computeIfAbsent(server, ignored -> new UtilityGrid());
    }

    public static void shutdown(MinecraftServer server) {
        INSTANCES.remove(server);
    }

    public static void resetAll() {
        INSTANCES.clear();
    }

    public void markDirty(UtilityKind kind, ResourceKey<Level> dimension) {
        dirty.computeIfAbsent(kind, key -> new HashMap<>()).put(dimension, Boolean.TRUE);
    }

    /** Flag every kind, for cases where the caller does not know which network a block belonged to. */
    public void markAllDirty(ResourceKey<Level> dimension) {
        for (UtilityKind kind : UtilityKind.values()) {
            markDirty(kind, dimension);
        }
    }

    public List<UtilityNetwork> networks(UtilityKind kind, ResourceKey<Level> dimension) {
        Map<ResourceKey<Level>, List<UtilityNetwork>> perLevel = networks.get(kind);
        if (perLevel == null) {
            return List.of();
        }
        return perLevel.getOrDefault(dimension, List.of());
    }

    /**
     * The network serving this position, or null if no distributor reaches it.
     *
     * <p>Coverage is by distance from a distributor rather than by wiring, which is the whole point of
     * a substation or a pumping station: the brief is explicit that nobody should be running a cable or
     * a pipe into every apartment.
     */
    public @Nullable UtilityNetwork networkCovering(UtilityKind kind, ServerLevel level, BlockPos pos) {
        for (UtilityNetwork network : networks(kind, level.dimension())) {
            for (BlockPos distributor : network.distributors()) {
                if (!(level.getBlockState(distributor).getBlock() instanceof UtilityComponent component)) {
                    continue;
                }
                int radius = component.coverageRadius();
                if (distributor.distSqr(pos) <= (double) radius * radius) {
                    return network;
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ rebuild

    public void tick(MinecraftServer server, CityRegistry registry) {
        long tick = server.overworld().getGameTime();
        if (tick - lastRebuildTick < MIN_REBUILD_INTERVAL_TICKS) {
            return;
        }
        boolean any = false;
        for (Map<ResourceKey<Level>, Boolean> perLevel : dirty.values()) {
            for (Boolean flag : perLevel.values()) {
                any |= Boolean.TRUE.equals(flag);
            }
        }
        if (!any) {
            return;
        }
        lastRebuildTick = tick;

        for (UtilityKind kind : UtilityKind.values()) {
            Map<ResourceKey<Level>, Boolean> perLevel = dirty.get(kind);
            if (perLevel == null) {
                continue;
            }
            for (ServerLevel level : server.getAllLevels()) {
                if (!Boolean.TRUE.equals(perLevel.get(level.dimension()))) {
                    continue;
                }
                perLevel.put(level.dimension(), Boolean.FALSE);
                networks.computeIfAbsent(kind, key -> new HashMap<>())
                        .put(level.dimension(), rebuild(kind, level));
            }
        }
        applyDemand(server, registry);
    }

    private List<UtilityNetwork> rebuild(UtilityKind kind, ServerLevel level) {
        final List<UtilityNetwork> result = new ArrayList<>();
        final LongSet visited = new LongOpenHashSet();

        for (BlockPos distributor : DistributorIndex.get(level.getServer()).distributors(kind, level.dimension())) {
            if (visited.contains(distributor.asLong()) || !level.isLoaded(distributor)) {
                continue;
            }
            if (!(level.getBlockState(distributor).getBlock() instanceof UtilityComponent component)
                    || component.utilityKind() != kind
                    || component.utilityRole() != UtilityRole.DISTRIBUTOR) {
                continue;
            }
            UtilityNetwork network = walk(kind, level, distributor, visited);
            if (network != null) {
                result.add(network);
            }
        }
        return result;
    }

    private @Nullable UtilityNetwork walk(UtilityKind kind, ServerLevel level, BlockPos start, LongSet visited) {
        final UtilityNetwork network = new UtilityNetwork(kind);
        final Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        visited.add(start.asLong());

        int examined = 0;
        while (!queue.isEmpty()) {
            if (++examined > MAX_NETWORK_SIZE) {
                LivingCities.LOGGER.warn("Living Cities: {} network from {} exceeded {} blocks; truncating",
                        kind.id(), start, MAX_NETWORK_SIZE);
                break;
            }
            BlockPos pos = queue.poll();
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof UtilityComponent component) || component.utilityKind() != kind) {
                continue;
            }

            network.addMember(pos);
            switch (component.utilityRole()) {
                case DISTRIBUTOR -> {
                    network.addDistributor(pos);
                    network.addThroughput(component.throughput());
                }
                case PRODUCER -> network.addProduction(component.output(level, pos, state));
                case TRANSFORMER -> network.addThroughput(component.throughput());
                default -> { }
            }

            for (Direction direction : Direction.values()) {
                enqueue(kind, level, queue, visited, pos.relative(direction));
            }
            if (component.utilityRole() == UtilityRole.PYLON) {
                enqueuePylons(kind, level, queue, visited, pos, component.linkRange());
            }
        }
        return network.distributors().isEmpty() ? null : network;
    }

    private void enqueue(UtilityKind kind, ServerLevel level, Deque<BlockPos> queue, LongSet visited, BlockPos pos) {
        if (visited.contains(pos.asLong()) || !level.isLoaded(pos)) {
            return;
        }
        if (level.getBlockState(pos).getBlock() instanceof UtilityComponent component
                && component.utilityKind() == kind) {
            visited.add(pos.asLong());
            queue.add(pos.immutable());
        }
    }

    /**
     * Link this pylon to others in range.
     *
     * <p>Scanned as a cube rather than traced as a line: a real hanging cable would need an entity or a
     * block per metre, and the point of a pylon is to span ground the player has not built on.
     */
    private void enqueuePylons(UtilityKind kind, ServerLevel level, Deque<BlockPos> queue, LongSet visited,
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
                    if (level.getBlockState(cursor).getBlock() instanceof UtilityComponent other
                            && other.utilityKind() == kind
                            && other.utilityRole() == UtilityRole.PYLON) {
                        visited.add(cursor.asLong());
                        queue.add(cursor.immutable());
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ demand

    /** Attribute every registered building's demand to whichever network covers it, for both kinds. */
    private void applyDemand(MinecraftServer server, CityRegistry registry) {
        for (Map<ResourceKey<Level>, List<UtilityNetwork>> perKind : networks.values()) {
            for (List<UtilityNetwork> perLevel : perKind.values()) {
                for (UtilityNetwork network : perLevel) {
                    network.resetDemand();
                }
            }
        }

        // Two passes: a network's satisfaction is only known once every building on it has said
        // what it draws, so nothing can be assigned a share until all demand is in.
        final List<BuildingLink> links = new ArrayList<>();
        for (Building building : registry.buildings()) {
            City city = registry.byId(building.cityId());
            if (city == null) {
                continue;
            }
            ServerLevel level = server.getLevel(city.dimension());
            if (level == null) {
                continue;
            }
            BlockPos centre = building.centre();

            int power = powerDemandOf(building);
            int water = waterDemandOf(building);
            building.setPowerDemandKw(power);
            building.setWaterDemand(water);

            UtilityNetwork powerNetwork = networkCovering(UtilityKind.POWER, level, centre);
            UtilityNetwork waterNetwork = networkCovering(UtilityKind.WATER, level, centre);
            if (powerNetwork != null) {
                powerNetwork.addDemand(power);
            }
            if (waterNetwork != null) {
                waterNetwork.addDemand(water);
            }
            links.add(new BuildingLink(building, powerNetwork, waterNetwork));
        }

        for (BuildingLink link : links) {
            link.building.setPowerSatisfaction(link.power == null ? 0.0F : link.power.satisfaction());
            link.building.setWaterSatisfaction(link.water == null ? 0.0F : link.water.satisfaction());
        }
    }

    private record BuildingLink(Building building,
                                @Nullable UtilityNetwork power,
                                @Nullable UtilityNetwork water) {
    }

    /** Households and workplaces both draw power; empty floor area does not. */
    public static int powerDemandOf(Building building) {
        double perResident = LivingCitiesConfig.SERVER.kwPerResident.get();
        double perJob = LivingCitiesConfig.SERVER.kwPerJob.get();
        return (int) Math.ceil(building.housingCapacity() * perResident + building.jobCapacity() * perJob);
    }

    /** Homes dominate water demand; workplaces use less of it per head than they do electricity. */
    public static int waterDemandOf(Building building) {
        double perResident = LivingCitiesConfig.SERVER.waterPerResident.get();
        double perJob = LivingCitiesConfig.SERVER.waterPerJob.get();
        return (int) Math.ceil(building.housingCapacity() * perResident + building.jobCapacity() * perJob);
    }
}
