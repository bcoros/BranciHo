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
     * <p>Growing, up to a ceiling: the first retry is soon-ish, because the usual reason a route
     * failed is a door somebody happened to be standing in. After a few, the honest conclusion is
     * that there is no way there, and asking again every twenty seconds is plenty.
     *
     * <p>The first wait is deliberately LONGER than the goals' ordinary repath interval, which is
     * sixty to eighty ticks. A back-off shorter than the normal rhythm would speed retries up on
     * failure, which is precisely backwards, and it is the kind of thing that reads as correct in a
     * diff and does the opposite in the world.
     */
    private static final int FIRST_BACKOFF = 100;
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
     * How close the end of a route has to get before it counts as arriving.
     *
     * <p>Four blocks, and this number is the whole reason the first version of this was a disaster.
     * {@code canReach()} is only true when the search landed <em>within one block</em> of the
     * target — and half the things a citizen walks to are not blocks you can stand in. A bed is the
     * obvious one: nobody can occupy a bed's own block, so the route necessarily ends beside it and
     * necessarily reports "not reached". Refusing that route meant every citizen in the world
     * walked home, stopped a stride short of their own bed and stood there all night.
     *
     * <p>So the test is distance, not the flag. A route that ends four blocks from the bed has
     * arrived. A route that ends forty blocks short, against a wall, has not — which is the case
     * this check exists for, and the only one it should refuse.
     */
    private static final float CLOSE_ENOUGH = 4.0F;

    /**
     * Set off, but only if the route actually gets there.
     *
     * <p>Returns whether it did. A false is not a failure to handle so much as an answer: there is
     * no way there from here on foot, and the caller should do something else or nothing.
     */
    public static boolean walkTo(Mob mob, double x, double y, double z, double speed) {
        if (!mob.getNavigation().moveTo(x, y, z, speed)) {
            return false;
        }
        Path path = mob.getNavigation().getPath();
        if (path == null) {
            return false;
        }
        // getDistToTarget is how far the LAST node of the route is from where it was aimed, in
        // Manhattan blocks. canReach is the same question with a threshold of one, which is too
        // strict for anything you stand next to rather than on.
        if (path.canReach() || path.getDistToTarget() <= CLOSE_ENOUGH) {
            return true;
        }
        // Genuinely short. Cancel rather than leave it running: a partial route left in place is
        // what walks somebody into a wall and holds them there.
        mob.getNavigation().stop();
        return false;
    }
}
