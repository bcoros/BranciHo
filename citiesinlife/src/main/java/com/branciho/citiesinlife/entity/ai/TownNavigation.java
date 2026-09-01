package com.branciho.citiesinlife.entity.ai;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathFinder;

/**
 * Pathfinding for somebody who lives in a town rather than in a field.
 *
 * <p>Vanilla gives every walking mob a budget of two hundred and fifty-six nodes to find a route
 * with. That is a sensible number for a cow: a cow's whole world is the twenty blocks it can see,
 * and anything further is not worth the CPU. It is the wrong number for a citizen, and it is
 * exactly why a city with a wall round it breaks them.
 *
 * <p>A* spends its budget going <em>towards</em> the target. Put a wall in the way and it walks the
 * frontier straight into that wall, then has to spend nodes fanning out sideways to find the gate —
 * and a wall a hundred blocks long needs far more than two hundred and fifty-six nodes before the
 * gate is even reached, let alone the route beyond it. So the search runs out, and A* returns its
 * best partial answer: the node nearest the target, which is a spot against the wall. The citizen
 * then walks confidently up to the bricks and stands there. It is not that they are not trying to
 * go round; it is that they ran out of thinking before they got as far as the corner.
 *
 * <p>Two changes, and both of them are the same change: give them more room to think.
 *
 * <ul>
 *   <li>The node budget goes up eightfold, which is what buys the sideways search a wall needs.
 *   <li>Doors count as passable, so a gate in that wall is a route rather than an obstacle. The
 *       goal that actually opens it is added alongside this; a path through a door nobody opens is
 *       a path into a door.
 * </ul>
 *
 * <p>The cost is real and it is bounded: a path is computed when a goal asks for one, which for a
 * citizen is a few times a minute, not every tick. Eight times a rare cost is still a rare cost.
 */
public final class TownNavigation extends GroundPathNavigation {

    /**
     * How many nodes the search may visit.
     *
     * <p>Eight thousand. Vanilla's 256 was chosen for a mob that lives in a field; this is chosen
     * for one that lives behind a wall with a gate in it, which is the shape of every city anybody
     * actually builds.
     *
     * <p>Two thousand was the first attempt and it was not enough. The budget buys area: roughly
     * one node per walkable tile the search touches, so two thousand covers about a forty-five
     * block square — fine for a gate round the corner, useless for one fifty blocks along the wall,
     * which is an ordinary distance in a city somebody has actually built. Eight thousand covers
     * something closer to ninety blocks square.
     *
     * <p>What stops this being expensive is not the number but how rarely it is paid. A route is
     * built when a goal asks for one, a few times a minute; and a route that <em>fails</em> — the
     * only case that spends the whole budget — now backs off instead of being retried every three
     * seconds. See {@link Routes}.
     */
    private static final int NODE_BUDGET = 8192;

    public TownNavigation(Mob mob, Level level) {
        super(mob, level);
    }

    /**
     * Build the finder with our own budget rather than the one handed down.
     *
     * <p>{@link PathNavigation}'s constructor fixes the budget before a subclass gets a chance to
     * say anything, and passes it in here. Ignoring the argument is the only place the number can
     * be changed at all.
     */
    @Override
    protected PathFinder createPathFinder(int ignored) {
        // Pavement-aware: see TownNodeEvaluator. Routing is the only thing that decides where
        // somebody walks, so it is the only place a preference for the pavement can live.
        this.nodeEvaluator = new TownNodeEvaluator();
        this.nodeEvaluator.setCanPassDoors(true);
        // A closed wooden door is a way through, not a wall. Wants OpenDoorGoal on the same mob:
        // this makes the router plan through the door, and that makes somebody open it.
        this.nodeEvaluator.setCanOpenDoors(true);
        return new PathFinder(this.nodeEvaluator, NODE_BUDGET);
    }
}
