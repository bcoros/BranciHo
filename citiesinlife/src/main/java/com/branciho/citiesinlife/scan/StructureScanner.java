package com.branciho.citiesinlife.scan;

import com.branciho.citiesinlife.structure.StructureType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

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

    /**
     * Biggest selection that may be measured, in blocks.
     *
     * <p>Raised from 150,000 because people build things far larger than that and were being told
     * their build was "too large to measure" with no recourse. This scan is a one-off on a
     * deliberate click rather than something that runs every tick, so a big box costs a single
     * frame at registration and nothing afterwards.
     */
    public static final int MAX_VOLUME = 1_000_000;

    /**
     * Biggest box that something re-reads on a timer.
     *
     * <p>Deliberately much smaller than {@link #MAX_VOLUME} and deliberately a separate number. A
     * power plant and a reactor are walked afresh every twenty ticks to find their machinery, so
     * for those the box size is a running cost rather than a one-off. Registration refuses a plant
     * box above this outright — the alternative is the failure this codebase has already had once,
     * where a box between two disagreeing limits registered happily and then behaved as though it
     * were empty.
     */
    public static final int MAX_SURVEY_VOLUME = 150_000;

    /** Biggest footprint on either horizontal axis. */
    public static final int MAX_SPAN = 256;

    /** Biggest vertical span. Taller than any building, and a bound on the per-column arrays. */
    public static final int MAX_HEIGHT = 384;

    /**
     * Cubic blocks of enclosed space that count as one cell of floor.
     *
     * <p>Three, because a storey is a floor plus roughly two blocks of headroom. Dividing interior
     * volume by that is what converts "how much space is in here" into a floor area, which is the
     * unit every capacity formula in the mod is written in.
     */
    private static final int VOLUME_PER_CELL = 3;

    /**
     * How far outside the selection to look for a floor or a roof the box missed.
     *
     * <p>A cell counts as interior when there is something solid below it and something solid above
     * it, and until now both had to be <em>inside the box</em>. That made drawing the box a test of
     * precision: start the selection on the air above your floorboards rather than on the
     * floorboards, or stop it on the last wall block rather than on the roof, and every column came
     * back with one solid block and nothing between — a finished building measuring nought.
     *
     * <p>So the cap may be just outside. Two blocks, which covers the off-by-one that actually
     * happens without inventing a ceiling for a column standing in the open: sky above a field is
     * still sky, so a field still measures nothing.
     */
    private static final int EDGE_REACH = 2;

    /** What measuring a selection produced, in floor-equivalent cells. */
    public record Measurement(int usableCells) {
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
        if (spanY > MAX_HEIGHT) {
            return "too_tall";
        }
        if (spanX * spanY * spanZ > MAX_VOLUME) {
            return "too_large";
        }
        return null;
    }

    /**
     * Measure a selection.
     *
     * <p>There used to be a choice here, and storey detection used to be the default: walk the
     * selection looking for surfaces you could stand on with headroom and a roof, and count those.
     * It was the more accurate of the two on a building that had recognisable storeys, and that
     * proviso was the problem — domes, warehouses, hollow towers and anything with slabs at odd
     * heights came back with no floors at all and housed nobody, which reads as the mod being
     * broken rather than as a measurement choice. Two modes meant every player met the broken one
     * first and had to be told there was a second.
     *
     * <p>So there is one, and it is the forgiving one. It measures enclosed space instead of
     * storeys, which gives a sensible number for any shape somebody actually builds — and holds up
     * far better afterwards, because a building with a hole blown in the side still has an inside
     * and very often no longer has a detectable floor.
     *
     * <p>Runs on the server thread and is bounded by {@link #MAX_VOLUME}, which is what keeps it
     * from being a tick spike. Everything it touches is read through {@link Level}, so it must
     * never be moved off-thread without snapshotting first.
     */
    public static Measurement measure(Level level, BlockPos min, BlockPos max) {
        return new Measurement(enclosedCells(level, min, max));
    }

    /**
     * Interior space, converted to an equivalent floor area.
     *
     * <p>A cell counts as interior when it is open space with something solid both above and below it
     * inside the selection. That is a crude definition and deliberately so — it is meant to give a
     * sensible number for a shape the storey detector cannot read, not to be precise.
     */
    private static int enclosedCells(Level level, BlockPos min, BlockPos max) {
        final int height = max.getY() - min.getY() + 1;
        final boolean[] passable = new boolean[height];
        final boolean[] solid = new boolean[height];
        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int enclosed = 0;

        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int z = min.getZ(); z <= max.getZ(); z++) {
                // Read each column once. The earlier version searched the whole column downward and
                // upward for every individual cell, which is fine on a cottage and turns a tall
                // hollow selection - exactly the shape this mode exists for - into tens of millions
                // of block lookups on the server thread from one right-click.
                for (int i = 0; i < height; i++) {
                    cursor.set(x, min.getY() + i, z);
                    BlockState state = level.getBlockState(cursor);
                    solid[i] = state.blocksMotion();
                    passable[i] = state.getFluidState().isEmpty() && (state.isAir() || !solid[i]);
                }

                // "Something solid below and something solid above" is exactly "between the lowest
                // and highest solid block in this column", which one pass can find.
                int lowestSolid = -1;
                int highestSolid = -1;
                for (int i = 0; i < height; i++) {
                    if (solid[i]) {
                        if (lowestSolid < 0) {
                            lowestSolid = i;
                        }
                        highestSolid = i;
                    }
                }
                if (lowestSolid < 0) {
                    // Nothing solid anywhere in this column: open air, and open air has no inside.
                    continue;
                }

                // The floor and the roof are allowed to be just outside the box. Only ever asked
                // when the box's own edge is open space - a column already capped inside the
                // selection is answered, and does not pay for the extra lookups.
                int floor = lowestSolid;
                if (passable[0] && capped(level, cursor, x, min.getY(), z, -1)) {
                    floor = -1;
                }
                int roof = highestSolid;
                if (passable[height - 1] && capped(level, cursor, x, max.getY(), z, 1)) {
                    roof = height;
                }
                for (int i = floor + 1; i < roof; i++) {
                    if (passable[i]) {
                        enclosed++;
                    }
                }
            }
        }
        return enclosed / VOLUME_PER_CELL;
    }

    /**
     * Whether there is a floor below, or a roof above, within reach of the selection's edge.
     *
     * <p>{@code step} is -1 to look down from the bottom of the box and 1 to look up from the top.
     */
    private static boolean capped(Level level, BlockPos.MutableBlockPos cursor,
                                  int x, int edgeY, int z, int step) {
        for (int away = 1; away <= EDGE_REACH; away++) {
            cursor.set(x, edgeY + step * away, z);
            if (level.getBlockState(cursor).blocksMotion()) {
                return true;
            }
        }
        return false;
    }
}
