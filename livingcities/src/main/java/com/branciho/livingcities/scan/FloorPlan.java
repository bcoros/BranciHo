package com.branciho.livingcities.scan;

import java.util.List;

/**
 * The complete result of analysing one snapshot.
 *
 * <p>The diagnostic counters are not decoration. Floor detection is a heuristic over geometry nobody
 * designed to be measured, so when it gets a building wrong the only way anyone can tell <em>why</em>
 * is by seeing how many candidates it generated, how many it threw away, and how many envelopes
 * leaked. Those numbers go into the log and the player's chat, not just into a debugger.
 */
public record FloorPlan(
        List<DetectedFloor> floors,
        // Heights that looked like a storey before validation.
        int candidateCount,
        // Candidates validation rejected: roofs, terrain, scaffolding, crawlspaces.
        int rejectedCount,
        // Floors whose usable area came from the fallback estimator rather than a clean fill.
        int leakyFloorCount,
        // Distinct block states that threw while being profiled. Nonzero means a mod incompatibility.
        int unknownStates,
        int paletteSize,
        long analysisNanos) {

    public FloorPlan {
        floors = List.copyOf(floors);
    }

    public static FloorPlan empty() {
        return new FloorPlan(List.of(), 0, 0, 0, 0, 0, 0L);
    }

    public int totalUsableCells() {
        int total = 0;
        for (DetectedFloor floor : floors) {
            total += floor.usableCells();
        }
        return total;
    }

    public long analysisMillis() {
        return analysisNanos / 1_000_000L;
    }
}
