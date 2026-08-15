package com.branciho.livingcities.scan;

/**
 * The tunables the floor detector runs on, snapshotted into plain values.
 *
 * <p>They are a record rather than static constants for two reasons. The analysis runs on a worker
 * thread, and reading a live config value there would be a cross-thread read of state a config
 * reload can swap underneath it; taking a copy on the server thread removes the question. And it
 * makes the analyser a pure function of (snapshot, grade level, settings), which is what lets it be
 * unit-tested against synthetic geometry with no world at all.
 *
 * <p>Only {@code scanBlocksPerTick} currently lives in {@code LivingCitiesConfig}. When the rest of
 * these earn a config entry, {@link #DEFAULTS} is the single place to change.
 */
public record ScanSettings(
        // Vertical clearance a citizen needs. Two cells is a Minecraft body.
        int minBodyHeight,
        // How far up to look for a ceiling before calling a cell open to the sky.
        int maxCeilingProbe,
        // Minimum floor-to-floor pitch. Below this, two candidates are the same storey.
        int minFloorSeparation,
        // A height needs at least this many standable cells to be considered a storey at all.
        int minCandidateCells,
        // A detected floor below this many usable cells is not a floor.
        int minUsableCells,
        // Largest connected room must reach this, which is what kills scaffolding and catwalks.
        int minRoomCells,
        // Fraction of usable cells needing a ceiling; below it the floor is a roof or a terrace.
        float minCoverageFraction,
        // Fraction of stand cells reachable from outside before the envelope is called leaky.
        float leakThreshold,
        // A leak only matters on a floor that is otherwise well roofed; below this it is a balcony.
        float leakCoverageFloor,
        // How much to trust the ray estimator relative to a clean flood fill.
        float rayConfidence,
        // How far the ray estimator looks for a wall.
        int maxRayLength,
        // Two heights overlapping less than this in plan are one split-level storey, not two.
        float subLevelMaxOverlap,
        // Horizontal collar of outdoor cells around the selection. Must be at least 1.
        int marginXZ,
        // Extra cells above the selection, so a roof slab is inside the snapshot.
        int marginAbove,
        // Extra cells below the selection, so the ground floor has something to stand on.
        int marginBelow) {

    public static final ScanSettings DEFAULTS = new ScanSettings(
            2,      // minBodyHeight
            8,      // maxCeilingProbe
            3,      // minFloorSeparation
            6,      // minCandidateCells
            6,      // minUsableCells
            4,      // minRoomCells
            0.50F,  // minCoverageFraction
            0.60F,  // leakThreshold
            0.70F,  // leakCoverageFloor
            0.85F,  // rayConfidence
            32,     // maxRayLength
            0.20F,  // subLevelMaxOverlap
            2,      // marginXZ
            2,      // marginAbove
            1);     // marginBelow

    public ScanSettings {
        // A zero horizontal margin has no failure mode a player would recognise: the flood fill finds
        // no outdoor seed, decides the entire world is enclosed, and silently reports a lawn as
        // apartments. Clamping is cheaper than explaining that bug later.
        marginXZ = Math.max(1, marginXZ);
        minBodyHeight = Math.max(1, minBodyHeight);
        minFloorSeparation = Math.max(1, minFloorSeparation);
        maxCeilingProbe = Math.max(minBodyHeight + 1, maxCeilingProbe);
    }
}
