package com.branciho.livingcities.utility;

/**
 * The kinds of network a city runs on.
 *
 * <p>Electricity and water are genuinely the same graph problem - producers, a conductive web, and
 * distributors that serve buildings within a radius - so they share one walker keyed by this. Where
 * they differ (water can be stored, power cannot) the difference lives on the components, not in a
 * second copy of the network code.
 */
public enum UtilityKind {

    POWER("power", "kW"),
    WATER("water", "m3/day");

    private final String id;
    private final String unit;

    UtilityKind(String id, String unit) {
        this.id = id;
        this.unit = unit;
    }

    public String id() {
        return id;
    }

    /** Short unit label for the UI, so the same screen can render either network. */
    public String unit() {
        return unit;
    }

    public String translationKey() {
        return "utility.livingcities." + id;
    }
}
