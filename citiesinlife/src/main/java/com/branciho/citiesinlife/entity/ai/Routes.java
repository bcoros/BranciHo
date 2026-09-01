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

    /**
     * How long to wait before trying again after a route that could not arrive.
     *
     * <p>A failed search is the expensive one — it is the only case that spends the whole node
     * budget, because A* only stops early when it has actually found the target. Retrying that
     * every three seconds for every citizen who cannot get to work is how a pathfinding budget
     * turns into a tick budget.
     *
     * <p>Growing, up to a ceiling: the first retry is soon, because the usual reason a route failed
     * is a door somebody happened to be standing in. After a few, the honest conclusion is that
     * there is no way there, and asking again every twenty seconds is plenty.
     */
    private static final int FIRST_BACKOFF = 40;
    private static final int MAX_BACKOFF = 400;

    private Routes() {
    }

    /** Somewhere to keep the wait between attempts, since a Goal is the thing that has one. */
    public static final class Patience {

        private int wait;

        /** Ticks to sit out before trying again, and the count doubles each time it is asked. */
        public int backOff() {
            wait = wait == 0 ? FIRST_BACKOFF : Math.min(MAX_BACKOFF, wait * 2);
            return wait;
        }

        /** A route arrived, so the next failure starts from a short wait again. */
        public void arrived() {
            wait = 0;
        }
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
