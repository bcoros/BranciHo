package com.branciho.citiesinlife.city;

import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

/**
 * How worried a city is, declared from its city hall.
 *
 * <p>This is the switch behind "raise the alarms". A city sitting at {@link #PEACE} lets its sirens
 * and alarms speak only for themselves — a burning turbine sounds, everything else stays quiet. At
 * {@link #ALERT} or {@link #WAR} every siren the city owns goes up and stays up until somebody
 * stands the city down again.
 *
 * <p>The distinction matters on the way back down. Lowering the level silences the poles that were
 * only wailing because they were told to; it cannot silence a plant that is actually on fire, a
 * reactor that is actually critical, or a silo whose roof is actually open. Those keep sounding
 * until the thing causing them is dealt with, which is the whole point of an alarm.
 *
 * <p>Persisted by string id rather than by ordinal, like every other enum here that reaches disk.
 * A save written before alert levels existed reads back as {@link #PEACE}, which is the right
 * answer for a city nobody has ever put on alert.
 */
public enum AlertLevel implements StringRepresentable {

    /** Nothing declared. Alarms answer only to real trouble. */
    PEACE("peace", 0xFF66E576),

    /** Sirens up. Something is coming, or might be. */
    ALERT("alert", 0xFFFFD859),

    /** Sirens up and the city on a war footing. */
    WAR("war", 0xFFFF6B6B);

    private final String id;
    private final int colour;

    AlertLevel(String id, int colour) {
        this.id = id;
        this.colour = colour;
    }

    public String id() {
        return id;
    }

    /** The colour this level is painted in on the city hall panel. */
    public int colour() {
        return colour;
    }

    /**
     * Whether this level is holding the city's sirens up on purpose.
     *
     * <p>Everything above peace does. There is deliberately no separate "alarms on" flag: two
     * fields meaning nearly the same thing is how you end up with a city that is at war with its
     * sirens off.
     */
    public boolean rousing() {
        return this != PEACE;
    }

    public Component displayName() {
        return Component.translatable("alert_level.citiesinlife." + id);
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    public static AlertLevel byId(String id, AlertLevel fallback) {
        for (AlertLevel level : values()) {
            if (level.id.equals(id)) {
                return level;
            }
        }
        return fallback;
    }
}
