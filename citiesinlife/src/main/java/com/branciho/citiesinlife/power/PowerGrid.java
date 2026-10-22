package com.branciho.citiesinlife.power;

import com.branciho.citiesinlife.city.City;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Every power line the player has drawn, and what reaches what through them.
 *
 * <p>Stored as an undirected graph of block positions. Connectivity is <em>not</em> saved: it is a
 * walk over the links, recomputed when asked. Saving derived reachability would mean two copies of
 * the same fact, and the copy that goes stale is always the one you are reading.
 *
 * <p>A link is only ever created by the player with the line tool. Nothing here auto-connects to
 * adjacent blocks, because the whole point is deciding where the line runs.
 */
public final class PowerGrid extends SavedData {

    private static final String FILE_ID = "citiesinlife_power";

    /** Guard against a walk running away on a pathological network. */
    private static final int MAX_WALK = 4096;

    /** dimension -> node -> its neighbours. Both directions are stored. */
    private final Map<ResourceKey<Level>, Long2ObjectOpenHashMap<LongOpenHashSet>> links = new HashMap<>();

    public static PowerGrid get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(PowerGrid::new, PowerGrid::load), FILE_ID);
    }

    // ------------------------------------------------------------------ links

    public boolean linked(ResourceKey<Level> dimension, BlockPos a, BlockPos b) {
        Long2ObjectOpenHashMap<LongOpenHashSet> index = links.get(dimension);
        if (index == null) {
            return false;
        }
        LongOpenHashSet neighbours = index.get(a.asLong());
        return neighbours != null && neighbours.contains(b.asLong());
    }

    public void link(ResourceKey<Level> dimension, BlockPos a, BlockPos b) {
        Long2ObjectOpenHashMap<LongOpenHashSet> index =
                links.computeIfAbsent(dimension, key -> new Long2ObjectOpenHashMap<>());
        index.computeIfAbsent(a.asLong(), key -> new LongOpenHashSet()).add(b.asLong());
        index.computeIfAbsent(b.asLong(), key -> new LongOpenHashSet()).add(a.asLong());
        setDirty();
    }

    public boolean unlink(ResourceKey<Level> dimension, BlockPos a, BlockPos b) {
        Long2ObjectOpenHashMap<LongOpenHashSet> index = links.get(dimension);
        if (index == null) {
            return false;
        }
        boolean removed = false;
        LongOpenHashSet fromA = index.get(a.asLong());
        if (fromA != null) {
            removed |= fromA.remove(b.asLong());
            if (fromA.isEmpty()) {
                index.remove(a.asLong());
            }
        }
        LongOpenHashSet fromB = index.get(b.asLong());
        if (fromB != null) {
            removed |= fromB.remove(a.asLong());
            if (fromB.isEmpty()) {
                index.remove(b.asLong());
            }
        }
        if (removed) {
            setDirty();
        }
        return removed;
    }

    /** Drop every line attached to a node, for when the block itself is broken. */
    public void removeNode(ResourceKey<Level> dimension, BlockPos pos) {
        Long2ObjectOpenHashMap<LongOpenHashSet> index = links.get(dimension);
        if (index == null) {
            return;
        }
        LongOpenHashSet neighbours = index.remove(pos.asLong());
        if (neighbours == null) {
            return;
        }
        for (long neighbour : neighbours) {
            LongOpenHashSet back = index.get(neighbour);
            if (back != null) {
                back.remove(pos.asLong());
                if (back.isEmpty()) {
                    index.remove(neighbour);
                }
            }
        }
        setDirty();
    }

    public LongOpenHashSet neighbours(ResourceKey<Level> dimension, BlockPos pos) {
        Long2ObjectOpenHashMap<LongOpenHashSet> index = links.get(dimension);
        if (index == null) {
            return new LongOpenHashSet();
        }
        LongOpenHashSet found = index.get(pos.asLong());
        return found == null ? new LongOpenHashSet() : found;
    }

    /** Every line in a dimension, as pairs. Each line appears once. */
    public List<long[]> allLines(ResourceKey<Level> dimension) {
        List<long[]> lines = new ArrayList<>();
        Long2ObjectOpenHashMap<LongOpenHashSet> index = links.get(dimension);
        if (index == null) {
            return lines;
        }
        for (Map.Entry<Long, LongOpenHashSet> entry : index.entrySet()) {
            long from = entry.getKey();
            for (long to : entry.getValue()) {
                // Emit each undirected edge once.
                if (from < to) {
                    lines.add(new long[]{from, to});
                }
            }
        }
        return lines;
    }

    // ----------------------------------------------------------- calculation

    /**
     * How much power reaches a city.
     *
     * <p>Walks outward from every station standing on ground the city owns and adds up the producers
     * it can reach. A solar farm connected to nothing, or to a station outside the borders, is worth
     * nothing — which is exactly the rule that makes a transmission line something you have to plan.
     */
    public int supplyFor(ServerLevel level, City city) {
        final LongOpenHashSet visited = new LongOpenHashSet();
        final LongArrayList queue = new LongArrayList();

        for (long node : nodesIn(level, city)) {
            if (visited.add(node)) {
                queue.add(node);
            }
        }

        int produced = 0;
        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        while (!queue.isEmpty() && visited.size() < MAX_WALK) {
            long current = queue.removeLong(queue.size() - 1);
            cursor.set(BlockPos.getX(current), BlockPos.getY(current), BlockPos.getZ(current));

            BlockState state = level.getBlockState(cursor);
            if (state.getBlock() instanceof PowerBlock node) {
                produced += node.powerOutput(level, cursor, state);
            }

            for (long neighbour : neighbours(level.dimension(), cursor)) {
                if (visited.add(neighbour)) {
                    queue.add(neighbour);
                }
            }
        }
        return produced;
    }

    /** Stations that stand on this city's ground and are therefore allowed to feed it. */
    private LongArrayList nodesIn(ServerLevel level, City city) {
        final LongArrayList found = new LongArrayList();
        final Long2ObjectOpenHashMap<LongOpenHashSet> index = links.get(level.dimension());
        if (index == null) {
            return found;
        }
        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (long node : index.keySet()) {
            int x = BlockPos.getX(node);
            int z = BlockPos.getZ(node);
            if (!city.owns(ChunkPos.asLong(x >> 4, z >> 4))) {
                continue;
            }
            cursor.set(x, BlockPos.getY(node), z);
            BlockState state = level.getBlockState(cursor);
            if (state.getBlock() instanceof PowerBlock block && block.powerRole() == PowerRole.STATION) {
                found.add(node);
            }
        }
        return found;
    }

    // ------------------------------------------------------------ persistence

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag dimensionList = new ListTag();
        for (Map.Entry<ResourceKey<Level>, Long2ObjectOpenHashMap<LongOpenHashSet>> entry : links.entrySet()) {
            CompoundTag dimensionTag = new CompoundTag();
            dimensionTag.putString("dimension", entry.getKey().location().toString());

            LongArrayList flat = new LongArrayList();
            for (Map.Entry<Long, LongOpenHashSet> node : entry.getValue().entrySet()) {
                for (long other : node.getValue()) {
                    if (node.getKey() < other) {
                        flat.add(node.getKey().longValue());
                        flat.add(other);
                    }
                }
            }
            dimensionTag.putLongArray("lines", flat.toLongArray());
            dimensionList.add(dimensionTag);
        }
        tag.put("dimensions", dimensionList);
        return tag;
    }

    public static PowerGrid load(CompoundTag tag, HolderLookup.Provider registries) {
        PowerGrid grid = new PowerGrid();
        ListTag dimensionList = tag.getList("dimensions", Tag.TAG_COMPOUND);
        for (int i = 0; i < dimensionList.size(); i++) {
            CompoundTag dimensionTag = dimensionList.getCompound(i);
            ResourceKey<Level> dimension = ResourceKey.create(
                    Registries.DIMENSION, ResourceLocation.parse(dimensionTag.getString("dimension")));
            long[] flat = dimensionTag.getLongArray("lines");
            for (int index = 0; index + 1 < flat.length; index += 2) {
                Long2ObjectOpenHashMap<LongOpenHashSet> nodes =
                        grid.links.computeIfAbsent(dimension, key -> new Long2ObjectOpenHashMap<>());
                nodes.computeIfAbsent(flat[index], key -> new LongOpenHashSet()).add(flat[index + 1]);
                nodes.computeIfAbsent(flat[index + 1], key -> new LongOpenHashSet()).add(flat[index]);
            }
        }
        return grid;
    }
}
