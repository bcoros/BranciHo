package com.branciho.citiesinlife.block;

import net.minecraft.util.StringRepresentable;

/**
 * The four liveries a windmill comes in.
 *
 * <p>Four separate blocks rather than one block you dye, because a wind farm is something you plan
 * the look of before you build it, and picking the colour out of the creative menu is one decision
 * instead of a placement followed by a correction.
 */
public enum WindmillColour implements StringRepresentable {

    WHITE("white"),
    BLACK("black"),
    BLUE("blue"),
    GREEN("green");

    private final String id;

    WindmillColour(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    /** The block and texture name for this livery, e.g. {@code windmill_black}. */
    public String blockName() {
        return "windmill_" + id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
