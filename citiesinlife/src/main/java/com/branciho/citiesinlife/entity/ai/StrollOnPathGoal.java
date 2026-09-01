package com.branciho.citiesinlife.entity.ai;

import com.branciho.citiesinlife.entity.CitizenEntity;
import com.branciho.citiesinlife.path.PathNetwork;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * Go and walk down the street.
 *
 * <p>The other half of the path system. {@link CitizenEntity#getWalkTargetValue} already makes a
 * citizen prefer to <em>be</em> on pavement when it happens to consider it; this makes it
 * occasionally set out for a bit of pavement on purpose, which is the difference between people who
 * drift onto a street and people who use it.
 *
 * <p>It sits below the work and sleep goals and above the ordinary wander, so it never overrides
 * somebody's day and never stops a citizen with no streets nearby from wandering as normal.
 */
public class StrollOnPathGoal extends Goal {

    /** How far a citizen will look for a street, and how many marks it will consider. */
    private static final int SEARCH_RADIUS = 28;
    private static final int CANDIDATES = 64;

    /** Average ticks between setting off when already standing on a street. */
    private static final int INTERVAL = 120;

    /**
     * And when standing nowhere near one.
     *
     * <p>Somebody already on the pavement has nothing to fix and can dawdle. Somebody stranded in
     * the middle of a field has, and should be visibly heading back towards the street rather than
     * waiting out a one-in-a-hundred-and-twenty roll to think of it. Twenty ticks is about a second,
     * so the trip back begins almost at once.
     */
    private static final int OFF_PATH_INTERVAL = 20;

    private final CitizenEntity citizen;
    private final double speed;
    private @Nullable BlockPos target;

    public StrollOnPathGoal(CitizenEntity citizen, double speed) {
        this.citizen = citizen;
        this.speed = speed;
        setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (citizen.isSleeping() || citizen.curfew()
                || citizen.activity() != CitizenEntity.ACTIVITY_IDLE) {
            return false;
        }
        if (!citizen.getNavigation().isDone()) {
            return false;
        }
        PathNetwork network = citizen.pathNetwork();
        if (network == null) {
            return false;
        }
        // Whether they are standing on pavement decides how eager this is. The block below counts,
        // because a street is marked at ground level and somebody on it stands on top of the marks.
        BlockPos here = citizen.blockPosition();
        boolean onPath = network.isPath(citizen.level().dimension(), here.below())
                || network.isPath(citizen.level().dimension(), here);
        int roll = onPath ? INTERVAL : OFF_PATH_INTERVAL;
        if (citizen.getRandom().nextInt(reducedTickDelay(roll)) != 0) {
            return false;
        }
        LongArrayList near = network.near(
                citizen.level().dimension(), citizen.blockPosition(), SEARCH_RADIUS, CANDIDATES);
        if (near.isEmpty()) {
            return false;
        }

        // On the street already: a random mark, so a street fills along its length rather than
        // everybody converging on one corner. Off it: the NEAREST mark, because the point of the
        // trip is to get back to the pavement and the nearest is what "get back to it" means.
        long chosen = onPath
                ? near.getLong(citizen.getRandom().nextInt(near.size()))
                : nearest(near, here);
        BlockPos marked = BlockPos.of(chosen);

        // Stand on the pavement, not in it. If the mark is on the surface the citizen wants the
        // space above; if the player marked the air above a street, the mark itself is the space.
        BlockPos standing = citizen.level().getBlockState(marked).isAir() ? marked : marked.above();
        if (!citizen.level().getBlockState(standing).isAir()) {
            return false;
        }
        target = standing;
        return true;
    }

    /** Whichever of these marks is closest to standing here. */
    private static long nearest(LongArrayList marks, BlockPos from) {
        long best = marks.getLong(0);
        double bestGap = Double.MAX_VALUE;
        for (int i = 0; i < marks.size(); i++) {
            long mark = marks.getLong(i);
            double gap = from.distSqr(BlockPos.of(mark));
            if (gap < bestGap) {
                bestGap = gap;
                best = mark;
            }
        }
        return best;
    }

    @Override
    public void start() {
        if (target != null) {
            // Through Routes, so a stretch of pavement on the far side of a wall is not walked at.
            // Being off the pavement is not a reason to march into a fence trying to reach it.
            Routes.walkTo(citizen, target.getX() + 0.5D, target.getY(),
                    target.getZ() + 0.5D, speed);
        }
    }

    @Override
    public boolean canContinueToUse() {
        return !citizen.getNavigation().isDone()
                && citizen.activity() == CitizenEntity.ACTIVITY_IDLE
                && !citizen.isSleeping()
                && !citizen.curfew();
    }

    @Override
    public void stop() {
        target = null;
        citizen.getNavigation().stop();
    }
}
