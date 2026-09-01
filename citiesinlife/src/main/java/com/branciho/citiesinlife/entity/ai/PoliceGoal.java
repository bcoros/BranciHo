package com.branciho.citiesinlife.entity.ai;

import com.branciho.citiesinlife.entity.CitizenEntity;
import com.branciho.citiesinlife.entity.ServiceEntity;
import com.branciho.citiesinlife.service.ServiceType;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Find whoever is causing trouble and point the officer at them.
 *
 * <p>Only that. The chase and the arrest are an ordinary melee goal sitting above this one, which is
 * why this holds no movement flags: it has to keep running while the officer is already sprinting
 * down the street, so that a criminal who dies or calms down is dropped rather than followed
 * forever.
 */
public class PoliceGoal extends Goal {

    /** How far an officer will look. A station serves its own district, not the whole map. */
    private static final double SEARCH_RANGE = 64.0D;

    private final ServiceEntity officer;

    public PoliceGoal(ServiceEntity officer) {
        this.officer = officer;
        setFlags(EnumSet.noneOf(Goal.Flag.class));
    }

    @Override
    public boolean canUse() {
        return officer.role() == ServiceType.POLICE;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return false;
    }

    @Override
    public void tick() {
        if (officer.getTarget() instanceof CitizenEntity current
                && current.isAlive() && current.criminal()) {
            officer.reportBusy();
            return;
        }

        CitizenEntity wanted = null;
        double best = SEARCH_RANGE * SEARCH_RANGE;
        for (CitizenEntity citizen : officer.level().getEntitiesOfClass(CitizenEntity.class,
                officer.getBoundingBox().inflate(SEARCH_RANGE))) {
            if (!citizen.isAlive() || !citizen.criminal()) {
                continue;
            }
            if (officer.cityId() == null || !officer.cityId().equals(citizen.cityId())) {
                continue;
            }
            double distance = officer.distanceToSqr(citizen);
            if (distance < best) {
                best = distance;
                wanted = citizen;
            }
        }
        officer.setTarget(wanted);
        if (wanted != null) {
            officer.reportBusy();
        }
    }
}
