package com.branciho.citiesinlife.entity.ai;

import com.branciho.citiesinlife.path.PathNetwork;
import com.branciho.citiesinlife.road.RoadNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import org.jetbrains.annotations.Nullable;

/**
 * Walking, with an opinion about pavement.
 *
 * <p>Pavement used to affect exactly one thing: {@code getWalkTargetValue}, which vanilla consults
 * only when a wander goal is choosing somewhere aimless to go. Every journey with an actual
 * destination — to work, home, to a fire — is routed by A*, and A* had never heard of the path
 * network. So a citizen walking to the office cut straight across your gardens past a perfectly
 * good street, and the honest report was that they ignore pavement, because for anything that
 * mattered they did.
 *
 * <p>The fix belongs in the cost function, which is the only thing the router actually reads. Every
 * step that is <em>not</em> on pavement or road costs a little extra. That makes a paved route the
 * cheap one wherever a paved route roughly exists, without ever making an unpaved one impossible —
 * a preference, not a rail. A citizen whose office is across a field still crosses the field; one
 * with a street going most of the way there takes the street.
 *
 * <p>Deliberately a small number. Crank it up and they walk three sides of a square to avoid four
 * blocks of grass, which looks far more broken than cutting the corner ever did.
 */
public class TownNodeEvaluator extends WalkNodeEvaluator {

    /**
     * What one step off the pavement costs, on top of the step itself.
     *
     * <p>A step costs 1 to take. At half again, a ten-block walk down a street beats a six-block
     * scramble across the flowerbeds only just — which is the balance asked for: prefer the path,
     * but not at any price.
     */
    private static final float OFF_PATH_MALUS = 0.5F;

    /** Roads count too, and count for slightly less, because a pavement is for walking on. */
    private static final float ROAD_MALUS = 0.15F;

    private @Nullable PathNetwork paths;
    private @Nullable RoadNetwork roads;
    private @Nullable ResourceKey<Level> dimension;

    /**
     * Look up the two networks once per path, not once per node.
     *
     * <p>Both are {@code SavedData} on the overworld and reaching them means going through the
     * server; a route is thousands of nodes, and doing that per node would cost more than the
     * routing. The lookups themselves are hash sets, so asking them per node is fine.
     *
     * <p>Runs on the server thread — {@code PathNavigation.createPath} is synchronous — so touching
     * saved data here is safe. It must stay that way if pathing is ever moved off-thread.
     */
    @Override
    public void prepare(PathNavigationRegion region, Mob mob) {
        super.prepare(region, mob);
        if (mob.level() instanceof ServerLevel level) {
            ResourceKey<Level> here = level.dimension();
            // Asked once per route. A world where nobody has laid a single slab of pavement or road
            // gets no per-node lookups at all, which is most worlds for the first hour of play.
            PathNetwork network = PathNetwork.get(level.getServer());
            RoadNetwork streets = RoadNetwork.get(level.getServer());
            this.paths = network.anyIn(here) ? network : null;
            this.roads = streets.anyIn(here) ? streets : null;
            this.dimension = this.paths == null && this.roads == null ? null : here;
        } else {
            this.paths = null;
            this.roads = null;
            this.dimension = null;
        }
    }

    @Override
    public void done() {
        super.done();
        this.paths = null;
        this.roads = null;
        this.dimension = null;
    }

    @Override
    public int getNeighbors(Node[] out, Node from) {
        int found = super.getNeighbors(out, from);
        if (dimension == null || (paths == null && roads == null)) {
            return found;
        }
        for (int i = 0; i < found; i++) {
            Node node = out[i];
            // A negative malus is vanilla's way of saying "forbidden", and several checks in the
            // evaluator test for exactly that. Adding to one would quietly turn a blocked node into
            // a passable one, so only nodes that are already allowed are touched.
            if (node == null || node.costMalus < 0.0F) {
                continue;
            }
            node.costMalus += surfaceMalus(node);
        }
        return found;
    }

    /**
     * What this step costs beyond the step itself.
     *
     * <p>The block <em>below</em> the node, because pavement is marked at ground level and somebody
     * walking down it is standing on top of the marks rather than inside them. The node's own
     * position is checked too, for the case where the marking is at the walking level itself.
     */
    private float surfaceMalus(Node node) {
        BlockPos at = new BlockPos(node.x, node.y, node.z);
        BlockPos under = at.below();
        if (paths != null && (paths.isPath(dimension, under) || paths.isPath(dimension, at))) {
            return 0.0F;
        }
        if (roads != null
                && (roads.isRoad(dimension, under.asLong()) || roads.isRoad(dimension, at.asLong()))) {
            return ROAD_MALUS;
        }
        return OFF_PATH_MALUS;
    }
}
