package com.branciho.citiesinlife.entity;

import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * Somebody with a building to go back to when they are done.
 *
 * <p>Two entities in this mod used to end the same way: {@code discard()}, on the spot, wherever
 * they happened to be standing. A tourist's five minutes were up, or a station decided it was
 * over-staffed, and a person the player was watching simply stopped existing in the middle of the
 * street. Both are correct bookkeeping and both look like a bug, because from outside there is no
 * difference between a mod tidying up and a mod losing an entity.
 *
 * <p>So the removal now has a journey in front of it. Whoever is leaving walks back to the building
 * they came from and goes in at the door, which is a thing you can watch happen and understand. The
 * bookkeeping is unchanged — they are off the station's roll the moment it decides they are — and
 * only the body takes the long way out.
 *
 * <p>Everything implementing this is also a {@code PathfinderMob}; {@link
 * com.branciho.citiesinlife.entity.ai.GoHomeGoal} takes both.
 */
public interface Homebound {

    /** The block they walk back to, or null if there is nowhere left to go. */
    @Nullable
    BlockPos homeBlock();

    /** Whether they are on their way out rather than still doing their job. */
    boolean leaving();

    /**
     * Say they are finished here.
     *
     * <p>Idempotent, and deliberately separate from the removal itself: whoever decides somebody is
     * done should not also have to decide where they walk or how long they get to do it.
     */
    void startLeaving();
}
