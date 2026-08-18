package com.branciho.citiesinlife.entity.ai;

import com.branciho.citiesinlife.entity.CitizenEntity;
import com.branciho.citiesinlife.entity.ServiceEntity;
import com.branciho.citiesinlife.service.ServiceType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.goal.Goal;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * Walk to somebody who is hurt and put them back together.
 *
 * <p>Nothing else in the mod heals a citizen. Mobs do not regenerate on their own, so a person who
 * survived a mugging or a fall stayed on two hearts for the rest of the save — which is exactly the
 * gap a hospital is for. Build one, staff it, and the city looks after its own.
 */
public class MedicGoal extends Goal {

    private static final double SEARCH_RANGE = 48.0D;

    /** Close enough to treat somebody, and how much treating them is worth per go. */
    private static final double TREAT_RANGE = 2.5D;
    private static final int TREAT_INTERVAL = 40;
    private static final float TREAT_AMOUNT = 4.0F;

    private final ServiceEntity doctor;
    private @Nullable CitizenEntity patient;
    private int cooldown;

    public MedicGoal(ServiceEntity doctor) {
        this.doctor = doctor;
        setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (doctor.role() != ServiceType.HOSPITAL) {
            return false;
        }
        patient = findPatient();
        return patient != null;
    }

    @Override
    public boolean canContinueToUse() {
        return patient != null && patient.isAlive() && patient.getHealth() < patient.getMaxHealth();
    }

    @Override
    public void stop() {
        patient = null;
        doctor.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (patient == null) {
            return;
        }
        doctor.reportBusy();
        doctor.getLookControl().setLookAt(patient, 30.0F, 30.0F);

        if (doctor.distanceToSqr(patient) > TREAT_RANGE * TREAT_RANGE) {
            doctor.getNavigation().moveTo(patient, 1.0D);
            return;
        }

        doctor.getNavigation().stop();
        if (--cooldown > 0) {
            return;
        }
        cooldown = TREAT_INTERVAL;
        patient.heal(TREAT_AMOUNT);
        doctor.swing(doctor.getUsedItemHand());
        doctor.level().playSound(null, patient.blockPosition(),
                SoundEvents.BOTTLE_FILL_DRAGONBREATH, SoundSource.NEUTRAL, 0.6F, 1.4F);
    }

    private @Nullable CitizenEntity findPatient() {
        CitizenEntity worst = null;
        double lowest = Double.MAX_VALUE;
        for (CitizenEntity citizen : doctor.level().getEntitiesOfClass(CitizenEntity.class,
                doctor.getBoundingBox().inflate(SEARCH_RANGE))) {
            if (!citizen.isAlive() || citizen.getHealth() >= citizen.getMaxHealth()) {
                continue;
            }
            if (doctor.cityId() == null || !doctor.cityId().equals(citizen.cityId())) {
                continue;
            }
            // Worst first. A doctor who treated the nearest scratch while somebody bled out across
            // the road would be a very odd thing to watch.
            double health = citizen.getHealth();
            if (health < lowest) {
                lowest = health;
                worst = citizen;
            }
        }
        return worst;
    }
}
