package com.branciho.citiesinlife.structure;

import net.minecraft.network.chat.Component;

/**
 * How a structure's capacity is worked out from its blocks.
 *
 * <p>Floor detection is the accurate method and the default, but it only works on buildings that
 * have recognisable storeys — a floor you can stand on, headroom above it, a roof over it. Plenty of
 * things people actually build do not: domes, open-plan warehouses, hollow towers with no interior
 * floors yet, anything with slabs at odd heights. Those come back with zero floors and house nobody,
 * which reads as the mod being broken rather than as a measurement choice.
 *
 * <p>{@link #BLOCK_VOLUME} is the answer to that: it measures the enclosed space instead of the
 * storeys, so a big sealed building is worth something even when the scanner cannot find a single
 * floor in it. It is less accurate on purpose, and the player picks it deliberately.
 */
public enum MeasureMode {

    /** Count usable floor cells storey by storey. Accurate, and needs real floors. */
    FLOORS("floors"),

    /** Count enclosed interior space and convert it to an equivalent floor area. */
    BLOCK_VOLUME("block_volume");

    private final String id;

    MeasureMode(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public Component displayName() {
        return Component.translatable("measure.citiesinlife." + id);
    }

    public MeasureMode other() {
        return this == FLOORS ? BLOCK_VOLUME : FLOORS;
    }

    public static MeasureMode byId(String id, MeasureMode fallback) {
        for (MeasureMode mode : values()) {
            if (mode.id.equals(id)) {
                return mode;
            }
        }
        return fallback;
    }
}
