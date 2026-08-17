package com.branciho.citiesinlife.work;

import net.minecraft.core.BlockPos;

import java.util.UUID;

/**
 * Somewhere a citizen can be given a job.
 *
 * <p>Two things implement this and they behave very differently — a desk seats one person who sits
 * down and works, a till stands two people up facing the customers — so the shared part is
 * deliberately thin: how many it takes, who has it, and where that person should be standing.
 *
 * <p>Claims expire rather than being handed back. A citizen can be unloaded with the chunk, killed,
 * or removed because the player turned the cap down, and none of those give it a chance to resign.
 * A desk that waits to be told is a desk that stays empty forever, so instead the worker checks in
 * while it is there and the desk forgets anybody who has stopped checking in.
 */
public interface Workplace {

    /** How many citizens this place employs at once. */
    int capacity();

    /** Claim a place, or renew one already held. False if it is full and this worker is not on it. */
    boolean checkIn(UUID worker, long gameTime);

    boolean employs(UUID worker);

    /** How many workers have checked in recently enough to count as present. */
    int staffed();

    /**
     * Where this worker should be while on shift.
     *
     * <p>Per worker rather than one spot for the building, because a till with two people behind it
     * needs them side by side rather than stood inside each other.
     */
    BlockPos spotFor(UUID worker);

    /** Whether the worker sits down here, which is the difference between an office and a shop. */
    boolean seated();
}
