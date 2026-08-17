package com.branciho.citiesinlife.path;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
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
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

/**
 * The ground the player has told the city's people to walk on.
 *
 * <p>Not nodes and not a graph — just a set of positions marked with the Path Tool. Citizens are not
 * routed along it; they simply find it far more attractive to stand on than anything else, so they
 * drift onto a marked street and follow it because it is the pleasant way round rather than because
 * they were told to.
 *
 * <p>That distinction is the whole design. A city with no paths still works and its people still
 * walk about; a city with paths gets people who use the pavements. Nothing ever stops dead because a
 * route was not drawn, which is what a node graph would have done the first time one was missing.
 *
 * <p>Positions are bucketed by chunk. A city's pavements can easily run to tens of thousands of
 * blocks, and both of the questions actually asked of this class — "is this one marked" and "what is
 * marked near here" — would otherwise mean walking that whole set, several times a second, once per
 * citizen.
 */
public final class PathNetwork extends SavedData {

    private static final String FILE_ID = "citiesinlife_paths";

    /** Most positions one drag may mark, so a careless box does not paint a whole chunk column. */
    public static final int MAX_PER_SELECTION = 8192;

    private final Map<ResourceKey<Level>, Long2ObjectOpenHashMap<LongOpenHashSet>> paths = new HashMap<>();

    public static PathNetwork get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(PathNetwork::new, PathNetwork::load), FILE_ID);
    }

    private static long chunkOf(long packed) {
        return ChunkPos.asLong(BlockPos.getX(packed) >> 4, BlockPos.getZ(packed) >> 4);
    }

    public boolean isPath(ResourceKey<Level> dimension, BlockPos pos) {
        Long2ObjectOpenHashMap<LongOpenHashSet> chunks = paths.get(dimension);
        if (chunks == null) {
            return false;
        }
        LongOpenHashSet bucket = chunks.get(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
        return bucket != null && bucket.contains(pos.asLong());
    }

    public boolean anyIn(ResourceKey<Level> dimension) {
        Long2ObjectOpenHashMap<LongOpenHashSet> chunks = paths.get(dimension);
        return chunks != null && !chunks.isEmpty();
    }

    /** Mark or clear every position in a box. Returns how many actually changed. */
    public int mark(ResourceKey<Level> dimension, BlockPos min, BlockPos max, boolean remove) {
        Long2ObjectOpenHashMap<LongOpenHashSet> chunks =
                paths.computeIfAbsent(dimension, key -> new Long2ObjectOpenHashMap<>());
        int changed = 0;
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int z = min.getZ(); z <= max.getZ(); z++) {
                long chunkKey = ChunkPos.asLong(x >> 4, z >> 4);
                LongOpenHashSet bucket = chunks.get(chunkKey);
                if (bucket == null) {
                    if (remove) {
                        continue;
                    }
                    bucket = new LongOpenHashSet();
                    chunks.put(chunkKey, bucket);
                }
                for (int y = min.getY(); y <= max.getY(); y++) {
                    long key = BlockPos.asLong(x, y, z);
                    boolean altered = remove ? bucket.remove(key) : bucket.add(key);
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

    private void prune(ResourceKey<Level> dimension, Long2ObjectOpenHashMap<LongOpenHashSet> chunks) {
        chunks.long2ObjectEntrySet().removeIf(entry -> entry.getValue().isEmpty());
        if (chunks.isEmpty()) {
            paths.remove(dimension);
        }
    }

    /**
     * Marked positions within a radius, for the overlay and for a citizen choosing where to stroll.
     *
     * <p>Only the chunks the radius touches are looked at, so the cost is set by how far you can see
     * rather than by how much pavement the city has.
     */
    public LongArrayList near(ResourceKey<Level> dimension, BlockPos centre, int radius, int limit) {
        LongArrayList found = new LongArrayList();
        Long2ObjectOpenHashMap<LongOpenHashSet> chunks = paths.get(dimension);
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
                LongOpenHashSet bucket = chunks.get(ChunkPos.asLong(chunkX, chunkZ));
                if (bucket == null) {
                    continue;
                }
                for (long key : bucket) {
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

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<ResourceKey<Level>, Long2ObjectOpenHashMap<LongOpenHashSet>> entry : paths.entrySet()) {
            LongArrayList flat = new LongArrayList();
            for (Long2ObjectMap.Entry<LongOpenHashSet> bucket : entry.getValue().long2ObjectEntrySet()) {
                for (long key : bucket.getValue()) {
                    flat.add(key);
                }
            }
            if (flat.isEmpty()) {
                continue;
            }
            CompoundTag dimensionTag = new CompoundTag();
            dimensionTag.putString("dimension", entry.getKey().location().toString());
            dimensionTag.putLongArray("marked", flat.toLongArray());
            list.add(dimensionTag);
        }
        tag.put("dimensions", list);
        return tag;
    }

    public static PathNetwork load(CompoundTag tag, HolderLookup.Provider registries) {
        PathNetwork network = new PathNetwork();
        ListTag list = tag.getList("dimensions", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag dimensionTag = list.getCompound(i);
            ResourceKey<Level> dimension = ResourceKey.create(
                    Registries.DIMENSION, ResourceLocation.parse(dimensionTag.getString("dimension")));
            Long2ObjectOpenHashMap<LongOpenHashSet> chunks = new Long2ObjectOpenHashMap<>();
            for (long key : dimensionTag.getLongArray("marked")) {
                chunks.computeIfAbsent(chunkOf(key), unused -> new LongOpenHashSet()).add(key);
            }
            network.paths.put(dimension, chunks);
        }
        return network;
    }
}
