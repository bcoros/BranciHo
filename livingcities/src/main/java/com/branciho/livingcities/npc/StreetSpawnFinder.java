package com.branciho.livingcities.npc;

import com.branciho.livingcities.building.Building;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Finds somewhere outside a building that a citizen can legally stand.
 *
 * <p><b>Server thread only.</b> This is the only class in the package that reads blocks.
 *
 * <h2>Where it looks, and why in that order</h2>
 *
 * <p>First choice is a registered {@link Building#entrances() entrance}, stepped a block or two
 * outward along whichever axis points away from the building's centre. An entrance is the one place
 * in an arbitrary player-built structure where we know the geometry is walkable and where a person
 * appearing makes narrative sense - they just came out of the door.
 *
 * <p>Second choice is a random point just outside the footprint's perimeter, at the building's base
 * height. That is a guess, so it is guarded by the same standability test and simply fails when the
 * guess is bad; a few cheap failed attempts beat one expensive search.
 *
 * <p>There is deliberately no third choice. If a building has no entrances and is surrounded by
 * cliff, water or its own walls, no citizen appears there, and the director quietly places the
 * crowd around some other building instead. Inventing a position by teleporting to the heightmap
 * would put people on rooftops and in gardens, which looks far worse than a slightly emptier street.
 */
public final class StreetSpawnFinder {

    /** Tries per request. Each attempt is a handful of block reads, so failing fast is cheap. */
    private static final int ATTEMPTS = 6;

    /** Blocks to step away from a wall or entrance, so nobody spawns inside the doorframe. */
    private static final int OUTWARD_MIN = 1;
    private static final int OUTWARD_MAX = 3;

    /** Vertical search window around the anchor: streets slope, and doors sit above grade. */
    private static final int SEARCH_UP = 2;
    private static final int SEARCH_DOWN = 4;

    /** Vertical clearance a citizen needs. Matches the entity's 1.8-block height, rounded up. */
    private static final int HEAD_ROOM = 2;

    private StreetSpawnFinder() {
    }

    /**
     * A standable street position near {@code building}, or null if none was found.
     *
     * @param viewer        the player this crowd is being staged for
     * @param minDistanceSq squared distance the spot must be from {@code viewer}, so nobody pops into
     *                      existence in front of the camera
     * @param maxDistanceSq squared distance beyond which the spot is not worth using, because the
     *                      director would despawn it again almost immediately
     */
    public static @Nullable BlockPos find(ServerLevel level,
                                          Building building,
                                          BlockPos viewer,
                                          double minDistanceSq,
                                          double maxDistanceSq,
                                          RandomSource random) {
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            final BlockPos spot = standableNear(level, anchor(building, random));
            if (spot == null) {
                continue;
            }
            // A spot inside the footprint is someone's living room, not a street.
            if (building.contains(spot)) {
                continue;
            }
            final double distanceSq = spot.distSqr(viewer);
            if (distanceSq < minDistanceSq || distanceSq > maxDistanceSq) {
                continue;
            }
            return spot;
        }
        return null;
    }

    /**
     * Whether a citizen can stand with its feet at this position.
     *
     * <p>Package-visible because the director re-checks positions it gets back from the pedestrian
     * network seam: a node index can outlive the blocks it was built from.
     */
    static boolean isStandable(ServerLevel level, BlockPos pos) {
        return isStandable(level, pos.getX(), pos.getY(), pos.getZ());
    }

    static boolean isStandable(ServerLevel level, int x, int y, int z) {
        final BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        probe.set(x, y - 1, z);
        // Unloaded chunks read as air and would happily pass every test below, so this check has to
        // come first. It also stops a spawn attempt from forcing a chunk load.
        if (!level.isLoaded(probe)) {
            return false;
        }

        final BlockState ground = level.getBlockState(probe);
        // The three-argument overload means SupportType.FULL, which is exactly "can something stand
        // on this", and avoids depending on the SupportType enum's members.
        if (!ground.isFaceSturdy(level, probe, Direction.UP) || !ground.getFluidState().isEmpty()) {
            return false;
        }

        for (int dy = 0; dy < HEAD_ROOM; dy++) {
            probe.set(x, y + dy, z);
            final BlockState state = level.getBlockState(probe);
            if (!state.getCollisionShape(level, probe).isEmpty() || !state.getFluidState().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ anchors

    private static BlockPos anchor(Building building, RandomSource random) {
        final Set<BlockPos> entrances = building.entrances();
        if (!entrances.isEmpty()) {
            final BlockPos entrance = pick(entrances, random);
            if (entrance != null) {
                return stepOutward(building, entrance, random);
            }
        }
        return perimeter(building, random);
    }

    /**
     * A random member of the entrance set without copying it to a list. Entrance sets are small and
     * this runs on the spawn path, which happens a few times a second at most.
     */
    private static @Nullable BlockPos pick(Set<BlockPos> entrances, RandomSource random) {
        int index = random.nextInt(entrances.size());
        for (BlockPos entrance : entrances) {
            if (index-- == 0) {
                return entrance;
            }
        }
        return null;
    }

    /**
     * Push a position away from the building's centre along its dominant axis.
     *
     * <p>Dominant axis rather than the true diagonal because a door is in a wall, and the wall is
     * perpendicular to whichever axis the door is furthest out on. Stepping diagonally from a corner
     * door would walk straight back into the adjacent wall.
     */
    private static BlockPos stepOutward(Building building, BlockPos from, RandomSource random) {
        final int centreX = (building.min().getX() + building.max().getX()) / 2;
        final int centreZ = (building.min().getZ() + building.max().getZ()) / 2;
        final int offsetX = from.getX() - centreX;
        final int offsetZ = from.getZ() - centreZ;

        int stepX = Integer.signum(offsetX);
        int stepZ = Integer.signum(offsetZ);
        if (Math.abs(offsetX) >= Math.abs(offsetZ)) {
            stepZ = 0;
        } else {
            stepX = 0;
        }
        if (stepX == 0 && stepZ == 0) {
            // An entrance at the exact centre of the footprint has no outward direction; any will do.
            stepX = 1;
        }

        final int distance = OUTWARD_MIN + random.nextInt(OUTWARD_MAX - OUTWARD_MIN + 1);
        return from.offset(stepX * distance, 0, stepZ * distance);
    }

    private static BlockPos perimeter(Building building, RandomSource random) {
        final BlockPos min = building.min();
        final BlockPos max = building.max();
        final int offset = OUTWARD_MIN + random.nextInt(OUTWARD_MAX - OUTWARD_MIN + 1);
        // Base height, not centre height: the street is at the bottom of a tower, not halfway up it.
        final int y = min.getY();

        return switch (random.nextInt(4)) {
            case 0 -> new BlockPos(min.getX() - offset, y, between(min.getZ(), max.getZ(), random));
            case 1 -> new BlockPos(max.getX() + offset, y, between(min.getZ(), max.getZ(), random));
            case 2 -> new BlockPos(between(min.getX(), max.getX(), random), y, min.getZ() - offset);
            default -> new BlockPos(between(min.getX(), max.getX(), random), y, max.getZ() + offset);
        };
    }

    private static int between(int min, int max, RandomSource random) {
        return max <= min ? min : min + random.nextInt(max - min + 1);
    }

    /** Search the vertical window around an anchor, top down, for the first standable cell. */
    private static @Nullable BlockPos standableNear(ServerLevel level, BlockPos anchor) {
        for (int dy = SEARCH_UP; dy >= -SEARCH_DOWN; dy--) {
            final int y = anchor.getY() + dy;
            if (isStandable(level, anchor.getX(), y, anchor.getZ())) {
                return new BlockPos(anchor.getX(), y, anchor.getZ());
            }
        }
        return null;
    }
}
