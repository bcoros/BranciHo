package com.branciho.citiesinlife.power;

/**
 * What a block does on the power network.
 *
 * <p>The network is a plain graph of hand-placed links rather than blocks that connect themselves to
 * whatever is next to them. That is deliberate: a wire that snaps to its neighbours turns power into
 * plumbing, and the interesting decision here is <em>where the line runs</em>, which only exists if
 * the player draws it.
 */
public enum PowerRole {

    /** Makes power. */
    PRODUCER,

    /**
     * Carries power a long way and does nothing else.
     *
     * <p>Masts are why a solar farm can sit out in the desert and still feed a city: they link to each
     * other over a much longer distance than anything else will.
     */
    RELAY,

    /**
     * Hands power to a city.
     *
     * <p>A station only counts if it stands on ground the city owns. That single rule is what ties the
     * whole network back to territory instead of letting power appear from anywhere.
     */
    STATION
}
