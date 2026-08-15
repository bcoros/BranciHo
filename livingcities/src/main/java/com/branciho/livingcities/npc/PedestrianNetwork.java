package com.branciho.livingcities.npc;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * SEAM - NOT IMPLEMENTED IN v0.1.
 *
 * <p>The interface the future pedestrian node graph will implement, and nothing else. There is no
 * graph in this release: {@link #EMPTY} is the only implementation, it answers "no route, no node" to
 * everything, and {@link CitizenSpawnDirector} treats those answers as "place the citizen wherever
 * the geometry allows", which is exactly what it does today.
 *
 * <p><b>This exists so that adding the graph is a change in one place.</b> The director already asks
 * for a node before it commits to a spawn position, so the day a real implementation is registered
 * via {@link CitizenSpawnDirector#setPedestrianNetwork}, citizens begin appearing on the sidewalk
 * network with no edit to the spawn logic. Building the graph now would be speculative work against
 * a router that does not exist; building the call site now costs three lines and removes the excuse
 * to bolt it on badly later.
 *
 * <h2>TODO (v0.2) - what a real implementation owes the caller</h2>
 *
 * <ul>
 *   <li>Nodes come from {@code PathNodeBlock} placements plus inferred sidewalk cells; the graph is
 *       built per city and rebuilt on block change, never per tick.</li>
 *   <li>{@link #nearestNode} must be O(small) - a chunk-bucketed index, not a scan - because it is
 *       called on the spawn path.</li>
 *   <li>{@link #route} returns waypoints, not a block-by-block path. Vanilla navigation walks the
 *       last few metres; the graph exists precisely so that vanilla pathfinding is never asked to
 *       understand a whole city.</li>
 *   <li>Server thread only, like everything else in this package.</li>
 * </ul>
 */
public interface PedestrianNetwork {

    /**
     * The nearest walkable node to {@code origin}, or null if there is none within
     * {@code maxDistance} blocks.
     *
     * <p>Callers must treat null as normal and fall back, not as an error.
     */
    @Nullable BlockPos nearestNode(ServerLevel level, BlockPos origin, int maxDistance);

    /**
     * Waypoints from {@code from} to {@code to}, or an empty list if the two are not connected.
     *
     * <p>Never null. An empty list means "walk there yourself or give up", which is a decision for
     * the caller and not for the graph.
     */
    List<BlockPos> route(ServerLevel level, BlockPos from, BlockPos to);

    /** True when this network has no nodes, so callers can skip work rather than probe it. */
    boolean isEmpty();

    /** The v0.1 network: no nodes, no routes. Behaves exactly as if the seam did not exist. */
    PedestrianNetwork EMPTY = new PedestrianNetwork() {

        @Override
        public @Nullable BlockPos nearestNode(ServerLevel level, BlockPos origin, int maxDistance) {
            return null;
        }

        @Override
        public List<BlockPos> route(ServerLevel level, BlockPos from, BlockPos to) {
            return List.of();
        }

        @Override
        public boolean isEmpty() {
            return true;
        }
    };
}
