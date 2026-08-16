package com.branciho.citiesinlife.structure;

import net.minecraft.nbt.CompoundTag;

/**
 * One detected storey of a structure.
 *
 * <p>A floor is the Y level of a walkable surface plus how many cells of that surface are actually
 * usable — standable, with headroom, and enclosed. Bounding volume is never used: a hollow tower and
 * a solid cube of the same dimensions are not the same building.
 */
public final class Floor {

    private final int y;
    private final int usableCells;

    public Floor(int y, int usableCells) {
        this.y = y;
        this.usableCells = Math.max(0, usableCells);
    }

    public int y() {
        return y;
    }

    public int usableCells() {
        return usableCells;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("y", y);
        tag.putInt("cells", usableCells);
        return tag;
    }

    public static Floor load(CompoundTag tag) {
        return new Floor(tag.getInt("y"), tag.getInt("cells"));
    }
}
