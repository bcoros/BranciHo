package com.branciho.livingcities.sim;

import com.branciho.livingcities.city.CityRole;
import net.minecraft.ChatFormatting;

/**
 * The player-facing problems the simulation is willing to interrupt someone about.
 *
 * <p>Kept small on purpose. Every entry here is a condition a mayor can actually act on; anything
 * merely interesting belongs in the management screen, not in chat. A warning that fires and cannot be
 * fixed trains players to ignore the channel.
 *
 * <p>Each warning carries the minimum role that hears it, so a city of twenty citizens does not get
 * twenty copies of a treasury notice aimed at its administrators.
 */
public enum CityWarning {

    /** More residents than homes: growth has outrun construction. */
    HOUSING_SHORTAGE("housing_shortage", ChatFormatting.YELLOW, CityRole.BUILDER),

    /** Jobs standing empty: the city needs residents, i.e. housing, not more workplaces. */
    WORKER_SHORTAGE("worker_shortage", ChatFormatting.YELLOW, CityRole.BUILDER),

    /** Residents with no work: the city needs employers. The mirror image of {@link #WORKER_SHORTAGE}. */
    UNEMPLOYMENT("unemployment", ChatFormatting.YELLOW, CityRole.BUILDER),

    /** Reserves are thin enough that a few days of deficit would empty them. */
    TREASURY_LOW("treasury_low", ChatFormatting.GOLD, CityRole.ADMINISTRATOR),

    /** The city could not pay its bills this step. Everything downstream of happiness suffers. */
    TREASURY_EMPTY("treasury_empty", ChatFormatting.RED, CityRole.ADMINISTRATOR);

    private final String id;
    private final ChatFormatting colour;
    private final CityRole minimumRole;

    CityWarning(String id, ChatFormatting colour, CityRole minimumRole) {
        this.id = id;
        this.colour = colour;
        this.minimumRole = minimumRole;
    }

    public String id() {
        return id;
    }

    public ChatFormatting colour() {
        return colour;
    }

    /** Lowest city role that should be told about this. */
    public CityRole minimumRole() {
        return minimumRole;
    }

    public String translationKey() {
        return "message.livingcities.warning." + id;
    }
}
