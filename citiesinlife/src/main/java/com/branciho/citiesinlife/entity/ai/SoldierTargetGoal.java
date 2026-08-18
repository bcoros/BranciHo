package com.branciho.citiesinlife.entity.ai;

import com.branciho.citiesinlife.city.City;
import com.branciho.citiesinlife.city.Diplomacy;
import com.branciho.citiesinlife.city.Relation;
import com.branciho.citiesinlife.entity.CitizenEntity;
import com.branciho.citiesinlife.entity.ServiceEntity;
import com.branciho.citiesinlife.service.ServiceType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * Who a soldier is willing to shoot at.
 *
 * <p>Only somebody their city is actually at war with, and that is checked every time rather than
 * remembered — a peace signed while a soldier is mid-swing should stop the swing, not be discovered
 * later. Enemy soldiers come first: a war is decided by the armies, and a unit that walked past the
 * defenders to chase a shopkeeper would lose it.
 *
 * <p>Holds no movement flags so it can keep re-deciding while the melee goal above it is already
 * running.
 */
public class SoldierTargetGoal extends Goal {

    private static final double SEARCH_RANGE = 24.0D;

    private final ServiceEntity soldier;

    public SoldierTargetGoal(ServiceEntity soldier) {
        this.soldier = soldier;
        setFlags(EnumSet.noneOf(Goal.Flag.class));
    }

    @Override
    public boolean canUse() {
        return soldier.role() == ServiceType.MILITARY;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void tick() {
        City mine = soldier.city();
        if (mine == null) {
            soldier.setTarget(null);
            return;
        }
        if (soldier.getTarget() != null && soldier.getTarget().isAlive()
                && atWarWith(mine, soldier.getTarget())) {
            return;
        }

        LivingEntity chosen = null;
        double best = SEARCH_RANGE * SEARCH_RANGE;
        for (LivingEntity nearby : soldier.level().getEntitiesOfClass(LivingEntity.class,
                soldier.getBoundingBox().inflate(SEARCH_RANGE))) {
            if (nearby == soldier || !nearby.isAlive() || !atWarWith(mine, nearby)) {
                continue;
            }
            double distance = soldier.distanceToSqr(nearby);
            // Soldiers outrank everybody else, however far away they are.
            boolean armed = nearby instanceof ServiceEntity other && other.role() == ServiceType.MILITARY;
            if (armed) {
                distance -= SEARCH_RANGE * SEARCH_RANGE;
            }
            if (distance < best) {
                best = distance;
                chosen = nearby;
            }
        }
        soldier.setTarget(chosen);
    }

    /** Whether this thing belongs to a city we are at war with. Players are left to the players. */
    private boolean atWarWith(City mine, @Nullable LivingEntity other) {
        City theirs = null;
        if (other instanceof ServiceEntity service) {
            theirs = service.city();
        } else if (other instanceof CitizenEntity citizen && citizen.cityId() != null
                && soldier.level().getServer() != null) {
            theirs = com.branciho.citiesinlife.city.CityData
                    .get(soldier.level().getServer()).city(citizen.cityId());
        }
        return theirs != null && !theirs.id().equals(mine.id())
                && Diplomacy.stance(theirs, mine) == Relation.WAR;
    }
}
