package com.branciho.citiesinlife.structure;

import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

/**
 * What a selected structure has been declared to be.
 *
 * <p>The mod ships no buildings. The player builds whatever they like out of whatever blocks they
 * like, draws a box around it, and picks one of these. Everything the simulation knows about a
 * building is derived from the blocks inside that box plus this choice.
 *
 * <p>Capacity is expressed as <em>cells per unit</em> rather than units per cell so the numbers read
 * as floor space: an office worker gets 14 floor cells, a factory worker 18, a shop worker 28
 * because shops are mostly aisle. Housing is handled separately because it quantises.
 */
public enum StructureType implements StringRepresentable {

    /**
     * The seat of the city. Selecting one founds a city; there is exactly one per city.
     *
     * <p>Deliberately not a block you place. The player builds a city hall that looks like a city
     * hall and then declares it to be one — the same rule as every other structure here.
     */
    CITY_CORE("city_core", 0xFFD86A, 0),

    RESIDENTIAL("residential", 0x66E576, 0),

    COMMERCIAL("commercial", 0x59A6FF, 6),

    /** Offices. Denser than shops because a desk needs less room than a shop floor. */
    BUSINESS("business", 0x4DD9E6, 3),

    FACTORY("factory", 0xFFD859, 4);

    /** Floor cells consumed by one dwelling. */
    public static final double CELLS_PER_DWELLING = 16.0D;

    /**
     * Virtual residents per dwelling.
     *
     * <p>Ten rather than a literal household, because a registered building stands for a piece of a
     * city rather than for one address. A modest apartment block reading as 370 people is what makes
     * a city feel like a city; the same block reading as 111 reads like a village and made the whole
     * economy feel pointless.
     *
     * <p>These are <em>virtual</em> citizens — a number, not entities. Physical NPCs will later be a
     * small visible sample of this figure, never one entity per person.
     */
    public static final int RESIDENTS_PER_DWELLING = 10;

    /** Below this, a floor is a cupboard rather than a home or a workplace. */
    public static final int MIN_USABLE_CELLS = 9;

    private final String id;
    private final int colour;
    private final int cellsPerJob;

    StructureType(String id, int colour, int cellsPerJob) {
        this.id = id;
        this.colour = colour;
        this.cellsPerJob = cellsPerJob;
    }

    public String id() {
        return id;
    }

    /** Outline and panel colour, so a glance at a district tells you its make-up. */
    public int colour() {
        return colour;
    }

    public boolean housesPeople() {
        return this == RESIDENTIAL;
    }

    public boolean employsPeople() {
        return cellsPerJob > 0;
    }

    public Component displayName() {
        return Component.translatable("structure.citiesinlife." + id);
    }

    /**
     * How many residents this many usable floor cells houses.
     *
     * <p>Quantised into whole dwellings because half an apartment houses nobody. A linear
     * cells-to-people ratio looks fine on a cottage and is wildly wrong on a tower.
     */
    public int residentsFor(int usableCells) {
        if (!housesPeople() || usableCells < MIN_USABLE_CELLS) {
            return 0;
        }
        int dwellings = (int) Math.floor(usableCells / CELLS_PER_DWELLING);
        return dwellings * RESIDENTS_PER_DWELLING;
    }

    public int jobsFor(int usableCells) {
        if (!employsPeople() || usableCells < MIN_USABLE_CELLS) {
            return 0;
        }
        return usableCells / cellsPerJob;
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    public static StructureType byId(String id, StructureType fallback) {
        for (StructureType type : values()) {
            if (type.id.equals(id)) {
                return type;
            }
        }
        return fallback;
    }

    /** The order the planner panel lists them in, and the order the scroll wheel cycles. */
    public static final StructureType[] SELECTABLE = values();

    public StructureType next(int direction) {
        int index = Math.floorMod(ordinal() + direction, SELECTABLE.length);
        return SELECTABLE[index];
    }
}
