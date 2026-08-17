package com.branciho.citiesinlife.water;

import com.branciho.citiesinlife.city.City;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
 * The water network: the pumping links the player drew, plus the pipes they laid.
 *
 * <p>Only the links are stored. Pipe connections are read from the world every time they are needed,
 * because the blocks themselves already are the record — saving a second copy of "these two pipes
 * touch" would only give it something to disagree with.
 *
 * <p>Nothing about reachability is saved either. It is a walk over the graph, recomputed when asked.
 */
public final class WaterGrid extends SavedData {

    private static final String FILE_ID = "citiesinlife_water";

    /** Guard against a walk running away across a pathological pipe maze. */
    private static final int MAX_WALK = 8192;

    /** dimension -> node -> its neighbours. Both directions are stored. */
    private final Map<ResourceKey<Level>, Long2ObjectOpenHashMap<LongOpenHashSet>> links = new HashMap<>();

    public static WaterGrid get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(WaterGrid::new, WaterGrid::load), FILE_ID);
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

    /** Drop every link attached to a node, for when the block itself is broken. */
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

    /** Every hand-drawn link in a dimension, as pairs. Each appears once. */
    public List<long[]> allLines(ResourceKey<Level> dimension) {
        List<long[]> lines = new ArrayList<>();
        Long2ObjectOpenHashMap<LongOpenHashSet> index = links.get(dimension);
        if (index == null) {
            return lines;
        }
        for (Map.Entry<Long, LongOpenHashSet> entry : index.entrySet()) {
            long from = entry.getKey();
            for (long to : entry.getValue()) {
                if (from < to) {
                    lines.add(new long[]{from, to});
                }
            }
        }
        return lines;
    }

    // ------------------------------------------------------------ traversal

    /**
     * Walk the whole network outward from a starting set, through links and touching pipes alike.
     *
     * @param collector called once for every position reached, with the block found there
     */
    private LongOpenHashSet walk(ServerLevel level, LongArrayList start, NodeVisitor collector) {
        final LongOpenHashSet visited = new LongOpenHashSet();
        final LongArrayList queue = new LongArrayList();
        for (long node : start) {
            if (visited.add(node)) {
                queue.add(node);
            }
        }

        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        final BlockPos.MutableBlockPos neighbourCursor = new BlockPos.MutableBlockPos();

        while (!queue.isEmpty() && visited.size() < MAX_WALK) {
            long current = queue.removeLong(queue.size() - 1);
            cursor.set(BlockPos.getX(current), BlockPos.getY(current), BlockPos.getZ(current));

            BlockState state = level.getBlockState(cursor);
            if (!(state.getBlock() instanceof WaterBlock block)) {
                // The block was broken since the link was drawn. Its links die with it rather than
                // conducting through a hole in the ground.
                continue;
            }
            collector.visit(cursor, state, block);

            for (long neighbour : neighbours(level.dimension(), cursor)) {
                if (visited.add(neighbour)) {
                    queue.add(neighbour);
                }
            }

            // Pipes also reach whatever they are touching, with no link needed.
            for (Direction direction : Direction.values()) {
                if (!block.joinsAutomatically(level, cursor, state, direction)) {
                    continue;
                }
                neighbourCursor.setWithOffset(cursor, direction);
                BlockState neighbourState = level.getBlockState(neighbourCursor);
                if (!(neighbourState.getBlock() instanceof WaterBlock neighbourBlock)) {
                    continue;
                }
                // Both ends must agree, so a pipe aimed at the shut side of a valve is not a leak.
                if (!neighbourBlock.joinsAutomatically(level, neighbourCursor, neighbourState,
                        direction.getOpposite())) {
                    continue;
                }
                long key = neighbourCursor.asLong();
                if (visited.add(key)) {
                    queue.add(key);
                }
            }
        }
        return visited;
    }

    private interface NodeVisitor {
        void visit(BlockPos pos, BlockState state, WaterBlock block);
    }

    // ----------------------------------------------------------- calculation

    /**
     * How much water reaches a city's tanks each step.
     *
     * <p>Walks outward from every storage tank standing on ground the city owns and adds up the
     * starter pumps it can get back to. A pump connected to nothing, or to a tank outside the
     * borders, is worth nothing - the same rule that ties power to territory.
     */
    /** One connected run of plumbing: the city tanks on it, and what reaches them. */
    public record Delivery(LongArrayList tanks, int supply) {
    }

    /**
     * What reaches each separate run of plumbing the city owns.
     *
     * <p>Per run, not per city, and that distinction is the whole point. Pooling every tank's water
     * into one number and then pouring it into whichever tank came first meant a tank behind a shut
     * valve filled up on water it had no connection to, while the tank the valve had opened sat
     * empty — which is exactly what a valve is for, done backwards.
     *
     * <p>Tanks that share a run share its water. Tanks on separate runs get their own, and a tank
     * cut off from every pump gets nothing, which is what closing a valve is supposed to mean.
     */
    public List<Delivery> deliveriesFor(ServerLevel level, City city) {
        LongArrayList tanks = storagesFor(level, city);
        List<Delivery> deliveries = new ArrayList<>();
        if (tanks.isEmpty()) {
            return deliveries;
        }

        LongOpenHashSet grouped = new LongOpenHashSet();
        for (long tank : tanks) {
            if (!grouped.add(tank)) {
                continue;
            }
            // Leaks come off the top rather than cutting the run: the city gets less water and the
            // pipe drips where you can find it, which is a problem you can chase.
            final int[] supply = {0};
            LongArrayList seed = new LongArrayList();
            seed.add(tank);
            LongOpenHashSet reached = walk(level, seed, (pos, state, block) ->
                    supply[0] += block.waterOutput(level, pos, state) - block.waterLoss(level, pos, state));

            LongArrayList sharing = new LongArrayList();
            sharing.add(tank);
            for (long other : tanks) {
                if (other != tank && reached.contains(other) && grouped.add(other)) {
                    sharing.add(other);
                }
            }
            deliveries.add(new Delivery(sharing, Math.max(0, supply[0])));
        }
        return deliveries;
    }

    /**
     * What actually reaches one particular block of plumbing.
     *
     * <p>Same walk the city's tanks get, started from somewhere else. An end pipe is not a tank and
     * belongs to no city, but the question it asks is identical: is there a pump on the other end of
     * all this, and is it winning against the leaks.
     */
    public int supplyReaching(ServerLevel level, BlockPos start) {
        final int[] supply = {0};
        LongArrayList seed = new LongArrayList();
        seed.add(start.asLong());
        walk(level, seed, (pos, state, block) ->
                supply[0] += block.waterOutput(level, pos, state) - block.waterLoss(level, pos, state));
        return Math.max(0, supply[0]);
    }

    /** Everything the city's plumbing delivers, added up, for the city panel. */
    public int supplyFor(ServerLevel level, City city) {
        int total = 0;
        for (Delivery delivery : deliveriesFor(level, city)) {
            total += delivery.supply();
        }
        return total;
    }

    /** What a survey of one pumping station found. */
    public record Survey(int sources, LongOpenHashSet visited) {

        public boolean reaches(BlockPos pos) {
            return visited.contains(pos.asLong());
        }
    }

    /**
     * Walk out from one node across the <em>pumps only</em> and report the station it belongs to.
     *
     * <p>A pumping station is the chain of pumps, not everything downstream of it. That distinction
     * is the whole reason this is separate from {@link #walk}: surveying through the pipes as well
     * made the "station" mean the entire city's plumbing, which had it both ways and got both wrong.
     * It refused a perfectly good second intake on its own river merely because that river's pipes
     * would eventually reach the same city, and it could be walked around entirely by laying one
     * pipe block to bridge two runs, which the rule never looks at.
     *
     * <p>So this stops at anything that is not a pump, and only follows hand-drawn links. Two
     * stations feeding one city is then allowed, and their water adds up, which is what a second
     * intake ought to buy you.
     *
     * <p>The visited set comes back too, because the caller has to tell "two stations about to
     * become one" from "these were already the same station" — in the second case the link is merely
     * redundant, and refusing it would be wrong.
     */
    public Survey surveyStation(ServerLevel level, BlockPos start) {
        final LongOpenHashSet visited = new LongOpenHashSet();
        final LongArrayList queue = new LongArrayList();
        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int sources = 0;

        visited.add(start.asLong());
        queue.add(start.asLong());

        while (!queue.isEmpty() && visited.size() < MAX_WALK) {
            long current = queue.removeLong(queue.size() - 1);
            cursor.set(BlockPos.getX(current), BlockPos.getY(current), BlockPos.getZ(current));

            BlockState state = level.getBlockState(cursor);
            if (!(state.getBlock() instanceof WaterBlock block) || !block.waterRole().isPump()) {
                // The far side of the seam, or a block that has since been broken. Either way the
                // station stops here.
                continue;
            }
            if (block.waterRole() == WaterRole.SOURCE) {
                sources++;
            }
            for (long neighbour : neighbours(level.dimension(), cursor)) {
                if (visited.add(neighbour)) {
                    queue.add(neighbour);
                }
            }
        }
        return new Survey(sources, visited);
    }

    /** Storage tanks that stand on this city's ground and are therefore allowed to serve it. */
    public LongArrayList storagesFor(ServerLevel level, City city) {
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
            if (state.getBlock() instanceof WaterBlock block && block.waterRole() == WaterRole.STORAGE) {
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

    public static WaterGrid load(CompoundTag tag, HolderLookup.Provider registries) {
        WaterGrid grid = new WaterGrid();
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
