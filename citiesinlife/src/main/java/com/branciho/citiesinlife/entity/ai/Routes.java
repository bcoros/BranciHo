package com.branciho.citiesinlife.entity.ai;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.Path;

/**
 * Going somewhere on purpose, and knowing when you cannot.
 *
 * <p>{@code PathNavigation.moveTo} returns true for a path that does not get there. When A* cannot
 * reach the target it hands back its best effort — the node it got closest on — and the mob walks
 * that with every appearance of confidence. Against a wall the closest node <em>is</em> the wall,
 * so the citizen strides up to the bricks and stops, having done exactly what it was told.
 *
 * <p>{@link TownNavigation} makes most walls navigable by giving the search enough budget to find
 * the gate. This is for the rest: a genuinely sealed city, a job across a ravine, a route somebody
 * bricked up this morning. Somebody who cannot get to work stays where they are. That reads as a
 * person who cannot get to work. Pressing into a wall for the rest of the day does not.
 */
public final class Routes {

    private Routes() {
    }

    /**
     * Set off, but only if the route actually arrives.
     *
     * <p>Returns whether it did. A false is not a failure to handle so much as an answer: there is
     * no way there from here on foot, and the caller should do something else or nothing.
     */
    public static boolean walkTo(Mob mob, double x, double y, double z, double speed) {
        if (!mob.getNavigation().moveTo(x, y, z, speed)) {
            return false;
        }
        Path path = mob.getNavigation().getPath();
        if (path == null || !path.canReach()) {
            // Cancel it rather than leave it running. A partial path left in place is the bug.
            mob.getNavigation().stop();
            return false;
        }
        return true;
    }
}
