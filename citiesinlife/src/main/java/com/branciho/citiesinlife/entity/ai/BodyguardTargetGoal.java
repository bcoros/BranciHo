package com.branciho.citiesinlife.entity.ai;

import com.branciho.citiesinlife.entity.ServiceEntity;
import com.branciho.citiesinlife.service.ServiceType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

/**
 * Who a bodyguard fights: whoever is fighting their employer.
 *
 * <p>Deliberately reactive, and deliberately narrow. A guard who picked their own fights would be
 * a liability — walking into a village and starting one, or attacking a neighbour you are at peace
 * with because they stood too close. This one has no opinions: it watches two facts about the
 * person who hired it, who hit them and who they hit, and answers those.
 *
 * <p>Holds no movement flags, like the other targeting goals here. It decides <em>who</em>; the
 * rifle and melee goals above it decide what to do about it, and they have to be able to keep doing
 * that while this keeps deciding.
 */
public class BodyguardTargetGoal extends Goal {

    /**
     * How long a threat stays a threat after it stops doing anything.
     *
     * <p>Vanilla's own last-hurt memory is a hundred ticks, and a guard who forgets sooner than
     * their employer's own damage tracker does would break off mid-swing.
     */
    private static final int MEMORY_TICKS = 100;

    /** Past this the fight is not the guard's any more. */
    private static final double REACH = 24.0D;

    private final ServiceEntity guard;

    public BodyguardTargetGoal(ServiceEntity guard) {
        this.guard = guard;
        setFlags(EnumSet.noneOf(Goal.Flag.class));
    }

    @Override
    public boolean canUse() {
        return guard.role() == ServiceType.BODYGUARD && guard.employer() != null;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void tick() {
        Player boss = guard.employer();
        if (boss == null) {
            guard.setTarget(null);
            return;
        }

        LivingEntity current = guard.getTarget();
        if (current != null && current.isAlive() && guard.distanceToSqr(current) <= REACH * REACH) {
            return;
        }

        LivingEntity threat = threatTo(boss);
        // Never the employer, however they came to be on that list. A guard that turns on the
        // person who hired it is not a bug anybody would find funny twice.
        if (threat == boss || threat == guard) {
            threat = null;
        }
        if (threat != null && (!threat.isAlive()
                || guard.distanceToSqr(threat) > REACH * REACH)) {
            threat = null;
        }
        guard.setTarget(threat);
    }

    /**
     * The fight the employer is in, if any.
     *
     * <p>Both directions. Somebody hitting them is the obvious one; somebody <em>they</em> are
     * hitting is what makes a bodyguard join in rather than stand there watching you lose.
     */
    private static LivingEntity threatTo(Player boss) {
        if (boss.getLastHurtByMob() != null
                && boss.tickCount - boss.getLastHurtByMobTimestamp() < MEMORY_TICKS) {
            return boss.getLastHurtByMob();
        }
        if (boss.getLastHurtMob() != null
                && boss.tickCount - boss.getLastHurtMobTimestamp() < MEMORY_TICKS) {
            return boss.getLastHurtMob();
        }
        return null;
    }
}
