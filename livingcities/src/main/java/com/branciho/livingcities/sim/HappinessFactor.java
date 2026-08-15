package com.branciho.livingcities.sim;

/**
 * The named things a city is judged on.
 *
 * <p>Happiness is deliberately not a single opaque number produced by one formula. A mayor watching
 * a city slide from 700 to 400 needs to know <em>which</em> of these moved, and a UI can only tell them
 * that if the model keeps the contributors separate all the way out. The weights live in
 * {@link HappinessModel}; this enum is just the vocabulary.
 *
 * <p>Weights are attached here rather than in the model so that the invariant "they sum to 1" is
 * checkable by reading one file, and so the UI can render the same ordering the model uses.
 */
public enum HappinessFactor {

    /** Is there somewhere to live? Homelessness is the harshest single input. */
    HOUSING("housing", 0.22D),

    /** Can working-age residents find work? */
    EMPLOYMENT("employment", 0.18D),

    /** How much of what the city produces does the city take? */
    TAXES("taxes", 0.17D),

    /** Parks, government, entertainment - floor area that exists for residents rather than profit. */
    SERVICES("services", 0.16D),

    /** Is the treasury solvent? People notice when the city stops paying its bills. */
    FINANCES("finances", 0.12D),

    /** Are the lights on? Nothing sours a city faster than a grid that cannot meet demand. */
    POWER("power", 0.15D);

    private final String id;
    private final double weight;

    HappinessFactor(String id, double weight) {
        this.id = id;
        this.weight = weight;
    }

    public String id() {
        return id;
    }

    public double weight() {
        return weight;
    }

    /** Translation key for a UI label. Lang entries are supplied by the resource pack, not by this class. */
    public String translationKey() {
        return "happiness.livingcities." + id;
    }

    public static HappinessFactor byId(String id, HappinessFactor fallback) {
        for (HappinessFactor factor : values()) {
            if (factor.id.equalsIgnoreCase(id) || factor.name().equalsIgnoreCase(id)) {
                return factor;
            }
        }
        return fallback;
    }
}
