package com.branciho.livingcities.utility;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Where the distributors are, so rebuilding a network does not have to search the world for them.
 *
 * <p>Deliberately not saved. Distributors register from their block entity's load hook, which fires
 * with their chunk, so the index rebuilds itself after a restart and can never disagree with the
 * blocks that actually exist.
 *
 * <p>The consequence is intended: a distributor in an unloaded chunk is not in the index and its
 * network is not simulated. Running a city off machinery nobody has loaded would mean ticking the
 * whole world.
 */
public final class DistributorIndex {

    private static final Map<MinecraftServer, DistributorIndex> INSTANCES = new IdentityHashMap<>();

    private final Map<UtilityKind, Map<ResourceKey<Level>, List<BlockPos>>> byKind = new EnumMap<>(UtilityKind.class);

    private DistributorIndex() {
    }

    public static DistributorIndex get(MinecraftServer server) {
        return INSTANCES.computeIfAbsent(server, ignored -> new DistributorIndex());
    }

    public static void shutdown(MinecraftServer server) {
        INSTANCES.remove(server);
    }

    public static void resetAll() {
        INSTANCES.clear();
    }

    public List<BlockPos> distributors(UtilityKind kind, ResourceKey<Level> dimension) {
        Map<ResourceKey<Level>, List<BlockPos>> perLevel = byKind.get(kind);
        if (perLevel == null) {
            return List.of();
        }
        return perLevel.getOrDefault(dimension, List.of());
    }

    public void add(UtilityKind kind, ResourceKey<Level> dimension, BlockPos pos) {
        List<BlockPos> list = byKind
                .computeIfAbsent(kind, key -> new HashMap<>())
                .computeIfAbsent(dimension, key -> new ArrayList<>());
        BlockPos immutable = pos.immutable();
        if (!list.contains(immutable)) {
            list.add(immutable);
        }
    }

    public void remove(UtilityKind kind, ResourceKey<Level> dimension, BlockPos pos) {
        Map<ResourceKey<Level>, List<BlockPos>> perLevel = byKind.get(kind);
        if (perLevel == null) {
            return;
        }
        List<BlockPos> list = perLevel.get(dimension);
        if (list != null) {
            list.remove(pos.immutable());
        }
    }
}
