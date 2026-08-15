package com.branciho.livingcities.scan;

import com.branciho.livingcities.building.FloorFlag;

import java.util.List;
import java.util.Set;

/**
 * One storey as the analyser found it, in world coordinates and plain values.
 *
 * <p>Kept separate from {@link com.branciho.livingcities.building.Floor} on purpose. {@code Floor}
 * is live, mutable, player-editable game state that gets serialised; this is an immutable
 * measurement produced on a worker thread. Converting one to the other happens once, on the server
 * thread, in {@link BuildingScanService} - so a scan that is abandoned or superseded never leaves a
 * half-applied floor list behind.
 */
public record DetectedFloor(
        /** World Y the feet of someone standing on this floor occupy. */
        int floorY,
        /** Inclusive world Y range this storey owns, up to the next floor or the top of the build. */
        int yMin,
        int yMax,
        /** Standable cells inside the footprint, before enclosure. The ceiling for any player override. */
        int standCells,
        /** Standable, enclosed, head-clear cells. This is the number that becomes population. */
        int usableCells,
        /** Median clear height over the usable cells, capped to the storey's own Y range. */
        int ceilingHeight,
        /**
         * The enclosure fill leaked - the floor is roofed but not sealed, so this figure came from
         * the fallback estimator. Worth surfacing in the UI rather than hiding: it usually means one
         * missing block in a wall, which the player can fix in five seconds if they are told.
         */
        boolean leaky,
        /** Extra world heights absorbed into this storey, for hillside and split-level builds. */
        List<Integer> subLevelYs,
        Set<FloorFlag> flags) {

    public DetectedFloor {
        subLevelYs = List.copyOf(subLevelYs);
        flags = Set.copyOf(flags);
    }

    public boolean has(FloorFlag flag) {
        return flags.contains(flag);
    }
}
