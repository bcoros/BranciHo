package com.branciho.citiesinlife.water;

/**
 * What a block does on the water network.
 *
 * <p>Water is deliberately not built like power. Power is a graph of hand-drawn lines and nothing
 * else, because the interesting decision there is where the line runs. Water has two halves: the
 * pumping station, which is invisible plumbing you draw with a tool because nobody wants to lay a
 * pipe across a riverbank, and the distribution network, which is real pipe blocks you place because
 * routing water through a city <em>is</em> the interesting part.
 *
 * <p>Which half a block belongs to is exactly this enum, and the rules about what may connect to what
 * fall out of it.
 */
public enum WaterRole {

    /** The starter pump: where water enters the system. One per station, and it must reach water. */
    SOURCE,

    /** A pump in the middle of a run. Carries the station further than one link would reach. */
    RELAY,

    /** The end pump: the one point where the invisible station meets real pipes. */
    OUTLET,

    /**
     * Pipes, valves and connectors.
     *
     * <p>These join to each other on their own the moment they are placed side by side, the way
     * redstone does. The tool has no business between two pipes.
     */
    CONDUIT,

    /** A tank. Water collects here, and a city drinks from the tanks standing on its own ground. */
    STORAGE;

    /** Whether this is one of the three pumps, which link to each other and to nothing else. */
    public boolean isPump() {
        return this == SOURCE || this == RELAY || this == OUTLET;
    }
}
