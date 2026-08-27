package com.branciho.citiesinlife.road;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * The roads the player has drawn, and which way each one runs.
 *
 * <p>Deliberately a second store rather than a field bolted onto {@link
 * com.branciho.citiesinlife.path.PathNetwork}. Pavement and roads answer different questions —
 * pavement makes ground <em>attractive</em> to walk on and is never routed over, whereas a road is a
 * directed graph a car is actually steered along. Merging them would have meant every pavement tile
 * carrying a direction it does not have.
 *
 * <p>Bucketed by chunk for the same reason paths are: the two questions asked of it, "what is this
 * tile" and "what is marked near here", would otherwise walk the whole city several times a second.
 *
 * <p>An absent tile and a tile with no flags are indistinguishable, because {@link
 * Long2IntOpenHashMap} returns 0 for both. That is safe only because a road is never stored with
 * zero flags — {@code ServerActions.markRoad} refuses a paint with no kind on it.
 */
public final class RoadNetwork extends SavedData {

    private static final String FILE_ID = "citiesinlife_roads";

    /** Most positions one drag may mark, so a careless box does not paint a whole chunk column. */
    public static final int MAX_PER_SELECTION = 8192;

    private final Map<ResourceKey<Level>, Long2ObjectOpenHashMap<Long2IntOpenHashMap>> roads = new HashMap<>();

    public static RoadNetwork get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(RoadNetwork::new, RoadNetwork::load), FILE_ID);
    }

    private static long chunkOf(long packed) {
        return ChunkPos.asLong(BlockPos.getX(packed) >> 4, BlockPos.getZ(packed) >> 4);
    }

    /** The flags on a tile, or 0 when there is no road there. */
    public int flagsAt(ResourceKey<Level> dimension, BlockPos pos) {
        return flagsAt(dimension, pos.asLong());
    }

    public int flagsAt(ResourceKey<Level> dimension, long packed) {
        Long2ObjectOpenHashMap<Long2IntOpenHashMap> chunks = roads.get(dimension);
        if (chunks == null) {
            return 0;
        }
        Long2IntOpenHashMap bucket = chunks.get(chunkOf(packed));
        return bucket == null ? 0 : bucket.get(packed);
    }

    public boolean isRoad(ResourceKey<Level> dimension, long packed) {
        return flagsAt(dimension, packed) != 0;
    }

    public boolean anyIn(ResourceKey<Level> dimension) {
        Long2ObjectOpenHashMap<Long2IntOpenHashMap> chunks = roads.get(dimension);
        return chunks != null && !chunks.isEmpty();
    }

    /**
     * Paint - or clear - every position in a box. Returns how many actually changed.
     *
     * <p>Flags replace rather than merge. Painting a plain street over a junction is how you stop it
     * being a junction, and an OR would have made that impossible without a separate eraser.
     */
    public int mark(ResourceKey<Level> dimension, BlockPos min, BlockPos max, int flags, boolean remove) {
        Long2ObjectOpenHashMap<Long2IntOpenHashMap> chunks =
                roads.computeIfAbsent(dimension, key -> new Long2ObjectOpenHashMap<>());
        int changed = 0;
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int z = min.getZ(); z <= max.getZ(); z++) {
                long chunkKey = ChunkPos.asLong(x >> 4, z >> 4);
                Long2IntOpenHashMap bucket = chunks.get(chunkKey);
                if (bucket == null) {
                    if (remove) {
                        continue;
                    }
                    bucket = new Long2IntOpenHashMap();
                    chunks.put(chunkKey, bucket);
                }
                for (int y = min.getY(); y <= max.getY(); y++) {
                    long key = BlockPos.asLong(x, y, z);
                    boolean altered;
                    if (remove) {
                        altered = bucket.containsKey(key);
                        if (altered) {
                            bucket.remove(key);
                        }
                    } else {
                        altered = bucket.put(key, flags) != flags;
                    }
                    if (altered && ++changed >= MAX_PER_SELECTION) {
                        prune(dimension, chunks);
                        setDirty();
                        return changed;
                    }
                }
                if (bucket.isEmpty()) {
                    chunks.remove(chunkKey);
                }
            }
        }
        if (changed > 0) {
            prune(dimension, chunks);
            setDirty();
        }
        return changed;
    }

    private void prune(ResourceKey<Level> dimension, Long2ObjectOpenHashMap<Long2IntOpenHashMap> chunks) {
        chunks.long2ObjectEntrySet().removeIf(entry -> entry.getValue().isEmpty());
        if (chunks.isEmpty()) {
            roads.remove(dimension);
        }
    }

    /** Road tiles within a radius, for the overlay. Only the chunks the radius touches are read. */
    public LongArrayList near(ResourceKey<Level> dimension, BlockPos centre, int radius, int limit) {
        LongArrayList found = new LongArrayList();
        Long2ObjectOpenHashMap<Long2IntOpenHashMap> chunks = roads.get(dimension);
        if (chunks == null || limit <= 0) {
            return found;
        }
        int radiusSqr = radius * radius;
        int minChunkX = (centre.getX() - radius) >> 4;
        int maxChunkX = (centre.getX() + radius) >> 4;
        int minChunkZ = (centre.getZ() - radius) >> 4;
        int maxChunkZ = (centre.getZ() + radius) >> 4;

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                Long2IntOpenHashMap bucket = chunks.get(ChunkPos.asLong(chunkX, chunkZ));
                if (bucket == null) {
                    continue;
                }
                for (long key : bucket.keySet()) {
                    int dx = BlockPos.getX(key) - centre.getX();
                    int dy = BlockPos.getY(key) - centre.getY();
                    int dz = BlockPos.getZ(key) - centre.getZ();
                    if (dx * dx + dy * dy + dz * dz > radiusSqr) {
                        continue;
                    }
                    found.add(key);
                    if (found.size() >= limit) {
                        return found;
                    }
                }
            }
        }
        return found;
    }

    /** The flags for a list of tiles, in the same order, for the sync payload. */
    public int[] flagsFor(ResourceKey<Level> dimension, LongArrayList tiles) {
        int[] flags = new int[tiles.size()];
        for (int i = 0; i < tiles.size(); i++) {
            flags[i] = flagsAt(dimension, tiles.getLong(i));
        }
        return flags;
    }

    /**
     * The nearest tile carrying a given flag, or any road tile at all when {@code requiredFlag} is 0.
     *
     * <p>This is how a citizen finds a car park and how a car finds somewhere to stop near where it
     * is going. Only the chunks the radius touches are scanned.
     */
    public @Nullable BlockPos nearestWith(ResourceKey<Level> dimension, BlockPos centre, int radius,
                                          int requiredFlag) {
        Long2ObjectOpenHashMap<Long2IntOpenHashMap> chunks = roads.get(dimension);
        if (chunks == null) {
            return null;
        }
        int radiusSqr = radius * radius;
        long bestKey = 0L;
        int bestDistance = Integer.MAX_VALUE;
        boolean found = false;

        for (int chunkX = (centre.getX() - radius) >> 4; chunkX <= (centre.getX() + radius) >> 4; chunkX++) {
            for (int chunkZ = (centre.getZ() - radius) >> 4; chunkZ <= (centre.getZ() + radius) >> 4; chunkZ++) {
                Long2IntOpenHashMap bucket = chunks.get(ChunkPos.asLong(chunkX, chunkZ));
                if (bucket == null) {
                    continue;
                }
                for (Long2IntMap.Entry entry : bucket.long2IntEntrySet()) {
                    if (requiredFlag != 0 && (entry.getIntValue() & requiredFlag) == 0) {
                        continue;
                    }
                    long key = entry.getLongKey();
                    int dx = BlockPos.getX(key) - centre.getX();
                    int dy = BlockPos.getY(key) - centre.getY();
                    int dz = BlockPos.getZ(key) - centre.getZ();
                    int distance = dx * dx + dy * dy + dz * dz;
                    if (distance > radiusSqr || distance >= bestDistance) {
                        continue;
                    }
                    bestDistance = distance;
                    bestKey = key;
                    found = true;
                }
            }
        }
        return found ? BlockPos.of(bestKey) : null;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<ResourceKey<Level>, Long2ObjectOpenHashMap<Long2IntOpenHashMap>> entry : roads.entrySet()) {
            LongArrayList tiles = new LongArrayList();
            java.util.ArrayList<Integer> flags = new java.util.ArrayList<>();
            for (Long2ObjectMap.Entry<Long2IntOpenHashMap> bucket : entry.getValue().long2ObjectEntrySet()) {
                for (Long2IntMap.Entry cell : bucket.getValue().long2IntEntrySet()) {
                    tiles.add(cell.getLongKey());
                    flags.add(cell.getIntValue());
                }
            }
            if (tiles.isEmpty()) {
                continue;
            }
            int[] flagArray = new int[flags.size()];
            for (int i = 0; i < flags.size(); i++) {
                flagArray[i] = flags.get(i);
            }
            CompoundTag dimensionTag = new CompoundTag();
            dimensionTag.putString("dimension", entry.getKey().location().toString());
            dimensionTag.putLongArray("tiles", tiles.toLongArray());
            dimensionTag.putIntArray("flags", flagArray);
            list.add(dimensionTag);
        }
        tag.put("dimensions", list);
        return tag;
    }

    public static RoadNetwork load(CompoundTag tag, HolderLookup.Provider registries) {
        RoadNetwork network = new RoadNetwork();
        ListTag list = tag.getList("dimensions", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag dimensionTag = list.getCompound(i);
            ResourceKey<Level> dimension = ResourceKey.create(
                    Registries.DIMENSION, ResourceLocation.parse(dimensionTag.getString("dimension")));
            long[] tiles = dimensionTag.getLongArray("tiles");
            int[] flags = dimensionTag.getIntArray("flags");
            Long2ObjectOpenHashMap<Long2IntOpenHashMap> chunks = new Long2ObjectOpenHashMap<>();
            // Two parallel arrays that a truncated save could leave mismatched; take the shorter.
            int count = Math.min(tiles.length, flags.length);
            for (int t = 0; t < count; t++) {
                chunks.computeIfAbsent(chunkOf(tiles[t]), unused -> new Long2IntOpenHashMap())
                        .put(tiles[t], flags[t]);
            }
            network.roads.put(dimension, chunks);
        }
        return network;
    }
}
