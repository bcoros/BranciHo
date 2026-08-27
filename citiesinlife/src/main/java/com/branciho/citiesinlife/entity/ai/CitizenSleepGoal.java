package com.branciho.citiesinlife.entity.ai;

import com.branciho.citiesinlife.entity.CitizenEntity;
import com.branciho.citiesinlife.road.Commute;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

import java.util.EnumSet;

/**
 * Go home to bed.
 *
 * <p>The bed is not decoration: it is the reason the citizen exists at all. A residential building
 * with no bed in it spawns nobody, so every citizen you can see is somebody's bed made visible, and
 * at night they go back to it.
 *
 * <p>Anybody on a night shift is exempt, which is the entire point of having night shifts.
 */
public class CitizenSleepGoal extends Goal {

    private static final double ARRIVED = 2.0D;
    private static final int REPATH_INTERVAL = 80;

    private final CitizenEntity citizen;
    private int repathIn;

    public CitizenSleepGoal(CitizenEntity citizen) {
        this.citizen = citizen;
        setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        BlockPos home = citizen.home();
        if (home == null || !Shifts.sleepingHours(citizen.level())) {
            return false;
        }
        if (Shifts.onShift(citizen.level(), citizen.nightShift())) {
            return false;
        }
        return stillABed(home);
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    /**
     * Whether the bed is still there.
     *
     * <p>Checked every tick rather than once, because somebody sleeping in a bed that has just been
     * mined out from under them would otherwise lie in mid-air until dawn.
     */
    private boolean stillABed(BlockPos pos) {
        if (!citizen.level().isLoaded(pos)) {
            return false;
        }
        BlockState state = citizen.level().getBlockState(pos);
        return state.getBlock() instanceof BedBlock
                && state.getValue(BedBlock.PART) == BedPart.HEAD;
    }

    @Override
    public void start() {
        repathIn = 0;
    }

    @Override
    public void tick() {
        BlockPos home = citizen.home();
        if (home == null) {
            return;
        }
        if (citizen.isSleeping()) {
            return;
        }
        if (citizen.blockPosition().distSqr(home) > ARRIVED * ARRIVED) {
            if (Commute.driving(citizen)) {
                return;
            }
            if (repathIn-- <= 0) {
                repathIn = REPATH_INTERVAL;
                if (!Commute.tryDrive(citizen, home)) {
                    citizen.getNavigation().moveTo(
                            home.getX() + 0.5D, home.getY(), home.getZ() + 0.5D, 1.0D);
                }
            }
            return;
        }
        citizen.getNavigation().stop();
        citizen.setActivity(CitizenEntity.ACTIVITY_IDLE);
        citizen.startSleeping(home);
    }

    @Override
    public void stop() {
        Commute.abandon(citizen);
        if (citizen.isSleeping()) {
            citizen.stopSleeping();
        }
        citizen.getNavigation().stop();
    }
}
