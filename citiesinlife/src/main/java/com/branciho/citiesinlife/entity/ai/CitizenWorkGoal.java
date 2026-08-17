package com.branciho.citiesinlife.entity.ai;

import com.branciho.citiesinlife.entity.CitizenEntity;
import com.branciho.citiesinlife.work.Workplace;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Go to work, and then actually do some.
 *
 * <p>What "some" means is the difference between the two workplaces. A desk seats one person who
 * faces a screen and types; a till stands two people up facing whoever walks in. Everything else —
 * getting there, holding the job open, going home when the shift ends — is the same for both, so it
 * is all here and the workplace only has to say where to stand and whether to sit down.
 */
public class CitizenWorkGoal extends Goal {

    /** Close enough to be at work. Anything tighter and they jostle at the desk forever. */
    private static final double ARRIVED = 1.4D;

    /** How often the walk to work is re-issued, in case the route was blocked by a new build. */
    private static final int REPATH_INTERVAL = 60;

    private final CitizenEntity citizen;
    private int repathIn;

    public CitizenWorkGoal(CitizenEntity citizen) {
        this.citizen = citizen;
        setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        if (citizen.isSleeping() || citizen.workstation() == null) {
            return false;
        }
        if (!Shifts.onShift(citizen.level(), citizen.nightShift())) {
            return false;
        }
        Workplace place = citizen.workplace();
        return place != null && place.employs(citizen.getUUID());
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        repathIn = 0;
    }

    @Override
    public void tick() {
        Workplace place = citizen.workplace();
        BlockPos station = citizen.workstation();
        if (place == null || station == null) {
            return;
        }
        BlockPos spot = place.spotFor(citizen.getUUID());
        double distance = citizen.position().distanceTo(
                new Vec3(spot.getX() + 0.5D, citizen.getY(), spot.getZ() + 0.5D));

        if (distance > ARRIVED) {
            citizen.setActivity(CitizenEntity.ACTIVITY_IDLE);
            if (repathIn-- <= 0) {
                repathIn = REPATH_INTERVAL;
                citizen.getNavigation().moveTo(
                        spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D, 1.0D);
            }
            return;
        }

        // Arrived. Stand still, face the work, and settle into whatever this job looks like.
        citizen.getNavigation().stop();
        citizen.getLookControl().setLookAt(
                station.getX() + 0.5D, station.getY() + 1.1D, station.getZ() + 0.5D);
        citizen.setActivity(place.seated()
                ? CitizenEntity.ACTIVITY_TYPING
                : CitizenEntity.ACTIVITY_SERVING);
    }

    @Override
    public void stop() {
        citizen.setActivity(CitizenEntity.ACTIVITY_IDLE);
        citizen.getNavigation().stop();
    }
}
