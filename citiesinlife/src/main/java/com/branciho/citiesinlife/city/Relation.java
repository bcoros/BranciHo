package com.branciho.citiesinlife.city;

import net.minecraft.network.chat.Component;

/**
 * How one city stands towards another.
 *
 * <p>Three states and no more, because every extra one is another thing a player has to hold in
 * their head before they can decide whether to walk into somebody's town. Neutral is the default and
 * means "keep out"; the other two are the two ways that changes — by being asked, or by not being.
 */
public enum Relation {

    /** No agreement. You may look, you may not touch. */
    NEUTRAL("neutral", 0x8C97A3),

    /**
     * They have given you the run of the place.
     *
     * <p>One way round on purpose. Letting a neighbour help you build should not silently hand them
     * the keys to your own city as well, and a pair of one-way grants says "we trust each other"
     * perfectly well without pretending trust is automatically mutual.
     */
    ALLIED("allied", 0x66E576),

    /**
     * At war. Their blocks are yours to break and their people are yours to kill.
     *
     * <p>Symmetric, unlike permission: declaring war on somebody makes them at war with you whether
     * they like it or not, which is the entire difference between a war and a favour.
     */
    WAR("war", 0xFF6B6B);

    private final String id;
    private final int colour;

    Relation(String id, int colour) {
        this.id = id;
        this.colour = colour;
    }

    public String id() {
        return id;
    }

    /** The colour this relation paints a foreign city's land on the map. */
    public int colour() {
        return colour;
    }

    public Component displayName() {
        return Component.translatable("relation.citiesinlife." + id);
    }

    /** Whether this relation lets an outsider place and break blocks, and harm citizens. */
    public boolean permitsInterference() {
        return this != NEUTRAL;
    }

    public static Relation byOrdinal(int ordinal) {
        Relation[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : NEUTRAL;
    }
}
