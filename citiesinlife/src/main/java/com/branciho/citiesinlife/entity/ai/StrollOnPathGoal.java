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

    /** Average ticks between setting off. Roughly every six seconds, so streets stay busy. */
    private static final int INTERVAL = 120;

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
        if (citizen.isSleeping() || citizen.activity() != CitizenEntity.ACTIVITY_IDLE) {
            return false;
        }
        if (!citizen.getNavigation().isDone()) {
            return false;
        }
        if (citizen.getRandom().nextInt(reducedTickDelay(INTERVAL)) != 0) {
            return false;
        }
        PathNetwork network = citizen.pathNetwork();
        if (network == null) {
            return false;
        }
        LongArrayList near = network.near(
                citizen.level().dimension(), citizen.blockPosition(), SEARCH_RADIUS, CANDIDATES);
        if (near.isEmpty()) {
            return false;
        }

        // A random mark rather than the nearest one, so a street fills up along its length instead
        // of everybody converging on the same corner.
        long chosen = near.getLong(citizen.getRandom().nextInt(near.size()));
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

    @Override
    public void start() {
        if (target != null) {
            citizen.getNavigation().moveTo(
                    target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D, speed);
        }
    }

    @Override
    public boolean canContinueToUse() {
        return !citizen.getNavigation().isDone()
                && citizen.activity() == CitizenEntity.ACTIVITY_IDLE
                && !citizen.isSleeping();
    }

    @Override
    public void stop() {
        target = null;
        citizen.getNavigation().stop();
    }
}
