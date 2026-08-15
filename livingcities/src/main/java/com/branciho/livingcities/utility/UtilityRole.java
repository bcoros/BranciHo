package com.branciho.livingcities.utility;

/** What a block does inside a utility network. */
public enum UtilityRole {

    /** Carries supply between blocks it directly touches. */
    CONDUCTOR,

    /** Conducts, and additionally links to other pylons at a distance for long runs. */
    PYLON,

    /** Feeds supply into the network. */
    PRODUCER,

    /**
     * Conducts, and contributes throughput capacity.
     *
     * <p>A network can only deliver as much as its transformers can carry, which is what stops a
     * single cable from a huge plant powering an entire city and makes transmission a thing you plan.
     */
    TRANSFORMER,

    /** Serves every registered building within its radius. */
    DISTRIBUTOR
}
