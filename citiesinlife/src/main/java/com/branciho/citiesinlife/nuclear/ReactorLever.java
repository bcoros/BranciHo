package com.branciho.citiesinlife.nuclear;

import net.minecraft.network.chat.Component;

/** Which of the four controls a lever block is. One class, four registrations. */
public enum ReactorLever {

    /** Pulls heat out of the core while it is thrown. */
    COOLER("cooler_lever"),

    /** Drives the core harder. More output, and more of everything that goes with it. */
    HEAT("heat_lever"),

    /**
     * Vents pressure.
     *
     * <p>Pressure climbs on its own while the core is hot and falls back when it is cold, so this is
     * the one control that is doing something even when you are not touching it. Leaving it thrown
     * bleeds the core down; leaving it shut lets it build.
     */
    PRESSURE("pressure_lever"),

    /**
     * How hard the turbines are driven, in five steps from off to full.
     *
     * <p>The only one of the four that is not a plain on-off switch, because "turn the reactor off"
     * has to be a real position rather than an absence. At zero the plant makes no electricity and
     * cannot explode: it is genuinely stopped, which is what you want available when something is
     * going wrong and you would rather stop than think.
     */
    TURBINE("turbine_power");

    private final String id;

    ReactorLever(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    /** Whether this control has five positions rather than two. */
    public boolean stepped() {
        return this == TURBINE;
    }

    public Component displayName() {
        return Component.translatable("block.citiesinlife." + id);
    }
}
