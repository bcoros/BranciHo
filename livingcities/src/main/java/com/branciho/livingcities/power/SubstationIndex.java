package com.branciho.livingcities.power;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Where the substations are, so rebuilding a grid does not have to search the world for them.
 *
 * <p>Deliberately not saved to disk. Substations populate it from their block entity's load hook,
 * which fires when their chunk loads, so the index rebuilds itself after a restart and can never
 * disagree with the blocks that actually exist. A saved copy could.
 *
 * <p>The consequence is that a substation in an unloaded chunk is not in the index, and its grid is
 * therefore not simulated. That is the correct behaviour: powering a city from machinery nobody has
 * loaded would mean ticking the whole world.
 */
public final class SubstationIndex {

    private static final Map<MinecraftServer, SubstationIndex> INSTANCES = new IdentityHashMap<>();

    private final Map<ResourceKey<Level>, List<BlockPos>> byLevel = new HashMap<>();

    private SubstationIndex() {
    }

    public static SubstationIndex get(MinecraftServer server) {
        return INSTANCES.computeIfAbsent(server, ignored -> new SubstationIndex());
    }

    public static void shutdown(MinecraftServer server) {
        INSTANCES.remove(server);
    }

    public static void resetAll() {
        INSTANCES.clear();
    }

    public List<BlockPos> substations(ResourceKey<Level> dimension) {
        return byLevel.getOrDefault(dimension, List.of());
    }

    public void add(ResourceKey<Level> dimension, BlockPos pos) {
        List<BlockPos> list = byLevel.computeIfAbsent(dimension, key -> new ArrayList<>());
        BlockPos immutable = pos.immutable();
        if (!list.contains(immutable)) {
            list.add(immutable);
        }
    }

    public void remove(ResourceKey<Level> dimension, BlockPos pos) {
        List<BlockPos> list = byLevel.get(dimension);
        if (list != null) {
            list.remove(pos.immutable());
        }
    }
}
