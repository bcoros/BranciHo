package com.branciho.citiesinlife.road;

import com.branciho.citiesinlife.city.City;
import com.branciho.citiesinlife.city.CityData;
import com.branciho.citiesinlife.city.Diplomacy;
import com.branciho.citiesinlife.city.Pact;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Finds a way through the road graph, respecting which way each street runs.
 *
 * <p>Breadth-first and hard-capped. A city's road network is small enough that a shortest-hop search
 * is fine, and the cap matters more than the optimality: this runs on the server tick, and a player
 * who paints a road across a continent must not be able to stall it.
 *
 * <p>The territory rule here is the entire mechanical meaning of {@link RoadTile#HIGHWAY}. An
 * ordinary street stops at someone else's border; a highway does not. That is what makes a highway
 * worth painting and what makes travel between two players' cities possible at all.
 *
 * <p>Deliberately boxed {@link HashMap} and {@link ArrayDeque} rather than a primitive long map. The
 * search is capped at {@link #MAX_NODES}, so the boxing is irrelevant, and these two cannot be
 * wrong about being on the classpath.
 */
public final class RoadRouter {

    /** How many tiles one search may visit before giving up. */
    public static final int MAX_NODES = 4096;

    /** Y offsets tried for the next tile, in order: level, up a step, down a step. */
    private static final int[] STEPS = {0, 1, -1};

    private RoadRouter() {
    }

    /**
     * A route from one tile to another, inclusive of both, or null when there is no legal way.
     *
     * <p>{@code cityId} is the traveller's own city, and may be null for a stateless traveller. A
     * null city is bound by the same rule as anyone else — it may not cut through claimed land
     * except on a highway — because otherwise a citizen with no city would be a hole in the border.
     */
    public static @Nullable LongArrayList route(RoadNetwork roads, CityData data,
                                                ResourceKey<Level> dimension, @Nullable UUID cityId,
                                                BlockPos from, BlockPos to) {
        long start = from.asLong();
        long goal = to.asLong();
        if (!roads.isRoad(dimension, start) || !roads.isRoad(dimension, goal)) {
            return null;
        }
        if (start == goal) {
            LongArrayList single = new LongArrayList();
            single.add(start);
            return single;
        }

        // Resolved once rather than per chunk. The blocked test below asks about it on every
        // chunk boundary the search crosses, which on a long route is a great many times.
        City home = cityId == null ? null : data.city(cityId);

        ArrayDeque<Long> frontier = new ArrayDeque<>();
        LongOpenHashSet seen = new LongOpenHashSet();
        Map<Long, Long> cameFrom = new HashMap<>();
        frontier.add(start);
        seen.add(start);

        // The common case is walking down one chunk, so remember the last verdict rather than
        // asking CityData once per tile.
        long cachedChunk = Long.MIN_VALUE;
        boolean cachedBlocked = false;

        while (!frontier.isEmpty()) {
            long current = frontier.poll();
            int flags = roads.flagsAt(dimension, current);
            int x = BlockPos.getX(current);
            int y = BlockPos.getY(current);
            int z = BlockPos.getZ(current);

            for (Direction direction : Direction.Plane.HORIZONTAL) {
                if (!RoadTile.allows(flags, direction)) {
                    continue;
                }
                int nx = x + direction.getStepX();
                int nz = z + direction.getStepZ();

                // Roads climb. Level ground first, then a step up, then a step down, so a street
                // over a hill does not need every block of it painted at one height.
                long neighbour = 0L;
                int neighbourFlags = 0;
                boolean any = false;
                for (int step = 0; step < 3 && !any; step++) {
                    int ny = y + STEPS[step];
                    long candidate = BlockPos.asLong(nx, ny, nz);
                    int candidateFlags = roads.flagsAt(dimension, candidate);
                    if (candidateFlags != 0 && !seen.contains(candidate)) {
                        neighbour = candidate;
                        neighbourFlags = candidateFlags;
                        any = true;
                    }
                }
                if (!any) {
                    continue;
                }

                long chunkKey = ChunkPos.asLong(nx >> 4, nz >> 4);
                if (chunkKey != cachedChunk) {
                    cachedChunk = chunkKey;
                    City owner = data.cityAtChunk(dimension, chunkKey);
                    // A city you have an International Travel pact with is not foreign ground
                    // for this purpose. Without it a commuter could reach the border on a highway
                    // and then have no legal way to cover the last few streets to the office.
                    cachedBlocked = owner != null && (cityId == null || !owner.id().equals(cityId))
                            && !Diplomacy.pactActive(home, owner, Pact.TRAVEL);
                }
                if (cachedBlocked && !RoadTile.is(neighbourFlags, RoadTile.HIGHWAY)) {
                    continue;
                }

                seen.add(neighbour);
                cameFrom.put(neighbour, current);
                if (neighbour == goal) {
                    return reconstruct(cameFrom, start, goal);
                }
                if (seen.size() >= MAX_NODES) {
                    return null;
                }
                frontier.add(neighbour);
            }
        }
        return null;
    }

    private static LongArrayList reconstruct(Map<Long, Long> cameFrom, long start, long goal) {
        LongArrayList reversed = new LongArrayList();
        long cursor = goal;
        reversed.add(cursor);
        while (cursor != start) {
            Long previous = cameFrom.get(cursor);
            if (previous == null) {
                return reversed;
            }
            cursor = previous;
            reversed.add(cursor);
        }
        LongArrayList route = new LongArrayList(reversed.size());
        for (int i = reversed.size() - 1; i >= 0; i--) {
            route.add(reversed.getLong(i));
        }
        return route;
    }
}
