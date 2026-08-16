package com.branciho.citiesinlife.scan;

import com.branciho.citiesinlife.structure.Floor;
import com.branciho.citiesinlife.structure.MeasureMode;
import com.branciho.citiesinlife.structure.StructureType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Measures what the player actually built inside a selection.
 *
 * <p>Nothing here asks a block <em>what it is</em> — only what it <em>does</em>: can you stand on it,
 * can you walk through it, does it hold a roof up. That is the whole reason a build made of Create
 * casings, a furniture mod's floorboards or something nobody has ever tested measures the same way a
 * vanilla one does.
 *
 * <p>The alternative — a list of known blocks — breaks the moment somebody uses a mod this was not
 * written against, which in a modpack is immediately.
 */
public final class StructureScanner {

    /** Biggest selection that may be measured, in blocks. */
    public static final int MAX_VOLUME = 150_000;

    /** Biggest footprint on either horizontal axis. */
    public static final int MAX_SPAN = 128;

    /** Headroom a cell needs above it before a person could plausibly occupy it. */
    private static final int REQUIRED_HEADROOM = 2;

    /** Storeys closer together than this are the same storey seen twice. */
    private static final int MIN_FLOOR_SPACING = 3;

    /**
     * Cubic blocks of enclosed space that count as one cell of floor.
     *
     * <p>Three, because a storey is a floor plus roughly two blocks of headroom. Dividing interior
     * volume by that converts "how much space is in here" into the same units floor detection
     * produces, so both modes feed the same capacity formulas.
     */
    private static final int VOLUME_PER_CELL = 3;

    /** What measuring a selection produced. Floors may be empty in volume mode. */
    public record Measurement(List<Floor> floors, int usableCells) {
    }

    private StructureScanner() {
    }

    /** Why a selection could not be measured, or {@code null} if it can. */
    public static String validate(BlockPos min, BlockPos max) {
        long spanX = (long) max.getX() - min.getX() + 1;
        long spanY = (long) max.getY() - min.getY() + 1;
        long spanZ = (long) max.getZ() - min.getZ() + 1;
        if (spanX > MAX_SPAN || spanZ > MAX_SPAN) {
            return "too_wide";
        }
        if (spanX * spanY * spanZ > MAX_VOLUME) {
            return "too_large";
        }
        return null;
    }

    /**
     * Measure a selection using the mode the player chose.
     *
     * <p>Runs on the server thread and is bounded by {@link #MAX_VOLUME}, which is what keeps it from
     * being a tick spike. Everything it touches is read through {@link Level}, so it must never be
     * moved off-thread without snapshotting first.
     */
    public static Measurement measure(Level level, BlockPos min, BlockPos max, MeasureMode mode) {
        if (mode == MeasureMode.BLOCK_VOLUME) {
            return new Measurement(List.of(), enclosedCells(level, min, max));
        }
        List<Floor> floors = scanFloors(level, min, max);
        int cells = 0;
        for (Floor floor : floors) {
            cells += floor.usableCells();
        }
        return new Measurement(floors, cells);
    }

    /**
     * Interior space, converted to an equivalent floor area.
     *
     * <p>A cell counts as interior when it is open space with something solid both above and below it
     * inside the selection. That is a crude definition and deliberately so — it is meant to give a
     * sensible number for a shape the storey detector cannot read, not to be precise.
     */
    private static int enclosedCells(Level level, BlockPos min, BlockPos max) {
        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int enclosed = 0;

        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int z = min.getZ(); z <= max.getZ(); z++) {
                for (int y = min.getY(); y <= max.getY(); y++) {
                    cursor.set(x, y, z);
                    if (!isPassable(level, cursor)) {
                        continue;
                    }
                    if (hasSolidBelow(level, cursor, x, y - 1, z, min.getY())
                            && hasCoverAbove(level, cursor, x, y + 1, z, max.getY())) {
                        enclosed++;
                    }
                }
            }
        }
        return enclosed / VOLUME_PER_CELL;
    }

    private static boolean hasSolidBelow(Level level, BlockPos.MutableBlockPos cursor,
                                         int x, int fromY, int z, int floorLimit) {
        for (int y = fromY; y >= floorLimit; y--) {
            cursor.set(x, y, z);
            if (level.getBlockState(cursor).blocksMotion()) {
                return true;
            }
        }
        return false;
    }

    private static List<Floor> scanFloors(Level level, BlockPos min, BlockPos max) {
        final int height = max.getY() - min.getY() + 1;
        final int[] usableAtY = new int[height];

        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int y = min.getY(); y <= max.getY(); y++) {
            int usable = 0;
            for (int x = min.getX(); x <= max.getX(); x++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    if (isUsableCell(level, cursor, x, y, z, max.getY())) {
                        usable++;
                    }
                }
            }
            usableAtY[y - min.getY()] = usable;
        }

        return groupIntoFloors(usableAtY, min.getY());
    }

    /**
     * A cell counts if you could stand in it: something solid underfoot, clear space at head height,
     * and something overhead.
     *
     * <p>The overhead test is what stops a player selecting an open field, calling it residential and
     * housing a town in it. A roof is the cheapest available proxy for "this is interior space".
     */
    private static boolean isUsableCell(Level level, BlockPos.MutableBlockPos cursor,
                                        int x, int y, int z, int ceilingLimit) {
        cursor.set(x, y, z);
        BlockState floor = level.getBlockState(cursor);
        if (!isStandable(level, cursor, floor)) {
            return false;
        }

        for (int offset = 1; offset <= REQUIRED_HEADROOM; offset++) {
            cursor.set(x, y + offset, z);
            if (!isPassable(level, cursor)) {
                return false;
            }
        }

        return hasCoverAbove(level, cursor, x, y + REQUIRED_HEADROOM + 1, z, ceilingLimit);
    }

    private static boolean isStandable(Level level, BlockPos pos, BlockState state) {
        if (state.isAir()) {
            return false;
        }
        // Sturdy top face covers slabs and stairs; full collision covers everything else solid.
        return state.isFaceSturdy(level, pos, Direction.UP) || state.isCollisionShapeFullBlock(level, pos);
    }

    private static boolean isPassable(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.getFluidState().isEmpty()) {
            return false;
        }
        return state.isAir() || !state.blocksMotion();
    }

    private static boolean hasCoverAbove(Level level, BlockPos.MutableBlockPos cursor,
                                         int x, int fromY, int z, int ceilingLimit) {
        for (int y = fromY; y <= ceilingLimit; y++) {
            cursor.set(x, y, z);
            if (level.getBlockState(cursor).blocksMotion()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Turn a per-Y count into storeys.
     *
     * <p>Walking upward and skipping ahead after each hit is what stops a staircase landing, a carpet
     * and the floor beneath it being reported as three separate floors of a building.
     */
    private static List<Floor> groupIntoFloors(int[] usableAtY, int baseY) {
        final List<Floor> floors = new ArrayList<>();
        int y = 0;
        while (y < usableAtY.length) {
            if (usableAtY[y] < StructureType.MIN_USABLE_CELLS) {
                y++;
                continue;
            }
            // Take the widest level in this cluster: a floor with furniture on it reads slightly
            // smaller than the bare floor one block down, and the bare floor is the honest number.
            int best = y;
            for (int probe = y; probe < Math.min(usableAtY.length, y + MIN_FLOOR_SPACING); probe++) {
                if (usableAtY[probe] > usableAtY[best]) {
                    best = probe;
                }
            }
            floors.add(new Floor(baseY + best, usableAtY[best]));
            y = best + MIN_FLOOR_SPACING;
        }
        return floors;
    }
}
