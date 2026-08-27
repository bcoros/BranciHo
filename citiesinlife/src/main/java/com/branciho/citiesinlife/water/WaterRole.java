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
    STORAGE,

    /**
     * A sewage collector: the tank's opposite number.
     *
     * <p>Where a tank is the point water stops being plumbing and becomes something to drink, this
     * is the point the city's used water becomes plumbing again. It is on the same network and it
     * travels down the same pipes, deliberately - there is no such thing as a sewage pipe in this
     * mod, and there is not going to be. A pipe is a pipe.
     *
     * <p>Which means a run carrying sewage is a run you should not also be drinking from. Nothing
     * stops you plumbing both into one loop; it simply comes out of the tap brown.
     */
    SEWAGE;

    /** Whether this is one of the three pumps, which link to each other and to nothing else. */
    public boolean isPump() {
        return this == SOURCE || this == RELAY || this == OUTLET;
    }
}
