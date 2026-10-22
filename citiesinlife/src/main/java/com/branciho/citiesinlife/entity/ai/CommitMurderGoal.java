package com.branciho.citiesinlife.entity.ai;

import com.branciho.citiesinlife.entity.CitizenEntity;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;

/**
 * What a citizen does once it has snapped.
 *
 * <p>The only crime in this city, and deliberately so: one thing to police means the player can read
 * what is happening from across the street without a log of offences to consult. It is also why it
 * is set so rare — a city where somebody is murdered every few minutes is not a city, it is a
 * horror film.
 *
 * <p>Chasing continues as long as the flag is up, so an officer who is on their way has something to
 * arrive at rather than a crowd of people standing about looking innocent.
 */
public class CommitMurderGoal extends MeleeAttackGoal {

    private final CitizenEntity citizen;

    public CommitMurderGoal(CitizenEntity citizen) {
        super(citizen, 1.25D, true);
        this.citizen = citizen;
    }

    @Override
    public boolean canUse() {
        if (!citizen.criminal()) {
            return false;
        }
        if (citizen.getTarget() == null || !citizen.getTarget().isAlive()) {
            citizen.setTarget(citizen.findVictim());
        }
        return super.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        return citizen.criminal() && super.canContinueToUse();
    }
}
