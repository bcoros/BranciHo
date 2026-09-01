package com.branciho.citiesinlife.city;

import net.minecraft.network.chat.Component;

/**
 * The things two cities can agree to that neither can impose.
 *
 * <p>Distinct from a building grant, and the difference is the whole point of having a second
 * mechanism. A grant is one way: it is the grantor's to give, it costs them something, and letting
 * a neighbour build on your land does not let you build on theirs. Everything here is two way and
 * has to be, because each of them is a thing that only makes sense if both sides mean it — you
 * cannot unilaterally decide that somebody else's citizens will come and work for you, and you
 * certainly cannot unilaterally decide to start billing them for electricity.
 *
 * <p>Held as a bitmask per neighbour on each city, and a pact is <em>active</em> only while both
 * cities have the bit set. That single rule gives consent, cancellation and the pending state for
 * nothing: offering is setting your bit, accepting is setting yours when theirs is already set, and
 * either side clearing theirs ends it that instant with no separate teardown to get wrong.
 */
public enum Pact {

    /**
     * Citizens may take jobs in the other city, and travel there to do it.
     *
     * <p>Only by car or by aeroplane, never on foot. A commuter crossing an international border
     * is making a journey, not a walk, and a citizen who cannot find a car or a flight stays home
     * rather than setting off across the map on foot.
     */
    TRAVEL("travel"),

    /**
     * Surplus power and water may cross the border, for money.
     *
     * <p>The price is the exporter's to set and the importer's to refuse: cancelling is the same
     * one click as any other pact, and running out of money does it for you.
     */
    UTILITIES("utilities"),

    /**
     * An alliance. Called to arms when the other goes to war.
     *
     * <p>It commits nothing by itself — nobody is dragged into a war by an ally's decision. What it
     * buys is being asked, which is the difference between hearing about a war and being in one.
     */
    ALLIANCE("alliance");

    private final String id;

    Pact(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public int bit() {
        return 1 << ordinal();
    }

    public Component displayName() {
        return Component.translatable("pact.citiesinlife." + id);
    }

    /** How this pact stands between two cities, from the first one's point of view. */
    public enum State {
        /** Neither side has asked. */
        NONE,
        /** You have asked and they have not answered. */
        OFFERED,
        /** They have asked and you have not answered. */
        INVITED,
        /** Both. */
        ACTIVE
    }
}
