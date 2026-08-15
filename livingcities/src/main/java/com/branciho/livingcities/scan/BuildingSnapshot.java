package com.branciho.livingcities.scan;

/**
 * A frozen, Minecraft-free copy of the blocks inside a building selection.
 *
 * <p>This is the hand-off object between the server thread and the analysis worker, and its only
 * hard rule is stated in the package docs: it holds no Minecraft type. Cells are palette indices
 * into an array of {@link BlockProfile}, which is a record of primitives. Nothing here can be
 * mutated, unloaded, or asked a question that reaches back into the world.
 *
 * <p><b>The cell array is column-major</b>: {@code index = ((z * sizeX) + x) * sizeY + y}. That is
 * not the obvious layout, and it is chosen because every single access pattern in floor detection is
 * a vertical walk - support below, headroom above, ceiling above that. Laying columns out
 * contiguously turns each of those walks into a linear scan instead of a stride of
 * {@code sizeX * sizeZ}, which on a half-million-cell region is roughly an order of magnitude in
 * cache misses.
 *
 * <p>The snapshot covers a slightly larger box than the building itself. The extra collar is what
 * gives the enclosure flood fill somewhere genuinely outdoors to start from; without it a selection
 * flush against the exterior walls has no outside seed at all and every cell in the world reports as
 * "enclosed". The registered building box is stored in snapshot-local coordinates so the analyser
 * can tell the two apart.
 */
public final class BuildingSnapshot {

    private final int minX;
    private final int minY;
    private final int minZ;

    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;

    /** The registered building box, inclusive, in snapshot-local coordinates. */
    private final int regMinX;
    private final int regMinY;
    private final int regMinZ;
    private final int regMaxX;
    private final int regMaxY;
    private final int regMaxZ;

    private final short[] cells;
    private final BlockProfile[] profiles;

    public BuildingSnapshot(int minX, int minY, int minZ,
                            int sizeX, int sizeY, int sizeZ,
                            int regMinX, int regMinY, int regMinZ,
                            int regMaxX, int regMaxY, int regMaxZ,
                            short[] cells, BlockProfile[] profiles) {
        final long expected = (long) sizeX * sizeY * sizeZ;
        if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0 || cells.length != expected) {
            throw new IllegalArgumentException("Snapshot cell array does not match " + sizeX + "x" + sizeY
                    + "x" + sizeZ);
        }
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.regMinX = regMinX;
        this.regMinY = regMinY;
        this.regMinZ = regMinZ;
        this.regMaxX = regMaxX;
        this.regMaxY = regMaxY;
        this.regMaxZ = regMaxZ;
        this.cells = cells;
        this.profiles = profiles;
    }

    /** Column-major index. Kept static so the code that fills the array uses the same formula. */
    public static int index(int x, int y, int z, int sizeX, int sizeY) {
        return ((z * sizeX) + x) * sizeY + y;
    }

    public int index(int x, int y, int z) {
        return ((z * sizeX) + x) * sizeY + y;
    }

    /** First index of the column at (x, z); add y to walk it. */
    public int columnBase(int x, int z) {
        return ((z * sizeX) + x) * sizeY;
    }

    public BlockProfile profileAt(int x, int y, int z) {
        return profiles[cells[index(x, y, z)] & 0xFFFF];
    }

    public BlockProfile profileAtIndex(int cellIndex) {
        return profiles[cells[cellIndex] & 0xFFFF];
    }

    // The hot loop in FloorAnalyzer reads these arrays directly; package-private keeps that fast
    // without handing raw mutable state to the rest of the mod.
    short[] cells() {
        return cells;
    }

    BlockProfile[] profiles() {
        return profiles;
    }

    public int paletteSize() {
        return profiles.length;
    }

    public int minX() {
        return minX;
    }

    public int minY() {
        return minY;
    }

    public int minZ() {
        return minZ;
    }

    public int sizeX() {
        return sizeX;
    }

    public int sizeY() {
        return sizeY;
    }

    public int sizeZ() {
        return sizeZ;
    }

    public int regMinX() {
        return regMinX;
    }

    public int regMinY() {
        return regMinY;
    }

    public int regMinZ() {
        return regMinZ;
    }

    public int regMaxX() {
        return regMaxX;
    }

    public int regMaxY() {
        return regMaxY;
    }

    public int regMaxZ() {
        return regMaxZ;
    }

    /** True when this snapshot-local column lies inside the registered building footprint. */
    public boolean insideFootprint(int x, int z) {
        return x >= regMinX && x <= regMaxX && z >= regMinZ && z <= regMaxZ;
    }

    public int cellCount() {
        return cells.length;
    }
}
