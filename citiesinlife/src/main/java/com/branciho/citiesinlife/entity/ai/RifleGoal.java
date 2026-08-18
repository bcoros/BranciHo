package com.branciho.citiesinlife.entity.ai;

import com.branciho.citiesinlife.entity.ServiceEntity;
import com.branciho.citiesinlife.item.Gunfire;
import com.branciho.citiesinlife.service.ServiceType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * A soldier who has been given something to shoot with, shooting with it.
 *
 * <p>Sits above the melee goal so an armed soldier keeps their distance instead of charging: they
 * close to about half their range, stop, and fire. Anything closer and they are in a knife fight
 * they did not need to be in.
 *
 * <p>Training is what this buys. An untrained soldier's shots wander badly enough to miss a moving
 * target across a street; a trained one hits what they are pointed at.
 */
public class RifleGoal extends Goal {

    /** How close a soldier tries to get before firing, as a fraction of the gun's range. */
    private static final double PREFERRED_RANGE = Gunfire.RANGE * 0.5D;

    /** Ticks between shots. */
    private static final int FIRE_INTERVAL = 25;

    /** How far off true an untrained shot goes, and how much each level of training takes off. */
    private static final double SPREAD_UNTRAINED = 0.22D;
    private static final double SPREAD_PER_TRAINING = 0.06D;

    private final ServiceEntity soldier;
    private int cooldown;

    public RifleGoal(ServiceEntity soldier) {
        this.soldier = soldier;
        setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return soldier.role() == ServiceType.MILITARY
                && Gunfire.firearm(soldier.getMainHandItem())
                && viableTarget() != null;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void stop() {
        cooldown = 0;
        soldier.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity target = viableTarget();
        if (target == null) {
            return;
        }
        soldier.reportBusy();
        soldier.getLookControl().setLookAt(target, 30.0F, 30.0F);

        double distance = soldier.distanceTo(target);
        if (distance > PREFERRED_RANGE) {
            soldier.getNavigation().moveTo(target, 1.0D);
        } else {
            soldier.getNavigation().stop();
        }

        if (--cooldown > 0) {
            return;
        }
        cooldown = FIRE_INTERVAL;
        // Aim by pointing the whole body: the shot leaves along the view vector, so a soldier whose
        // head has turned but whose body has not would fire into the wall beside them.
        soldier.lookAt(target, 60.0F, 60.0F);
        soldier.swing(soldier.getUsedItemHand());
        Gunfire.fire(soldier.level(), soldier, spread());
    }

    private double spread() {
        return Math.max(0.0D, SPREAD_UNTRAINED - soldier.training() * SPREAD_PER_TRAINING);
    }

    /** Somebody this soldier is already fighting, close enough and in sight. */
    private @Nullable LivingEntity viableTarget() {
        LivingEntity target = soldier.getTarget();
        if (target == null || !target.isAlive()
                || soldier.distanceTo(target) > Gunfire.RANGE
                || !soldier.hasLineOfSight(target)) {
            return null;
        }
        return target;
    }
}
