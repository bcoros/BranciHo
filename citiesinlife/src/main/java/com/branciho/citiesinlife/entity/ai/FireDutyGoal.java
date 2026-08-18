package com.branciho.citiesinlife.entity.ai;

import com.branciho.citiesinlife.blockentity.TurbineBlockEntity;
import com.branciho.citiesinlife.city.City;
import com.branciho.citiesinlife.city.CityData;
import com.branciho.citiesinlife.entity.ServiceEntity;
import com.branciho.citiesinlife.plant.PlantSurvey;
import com.branciho.citiesinlife.service.ServiceType;
import com.branciho.citiesinlife.structure.Structure;
import com.branciho.citiesinlife.structure.StructureType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.goal.Goal;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * The fire brigade's whole job: get to the turbine and sort it out.
 *
 * <p>Exactly what a player does with an extinguisher in one hand and a wrench in the other, which is
 * the point of building the station — a coal plant no longer needs somebody standing in it waiting
 * for the alarm. Fire first, then the soot that started it, because putting the flames out of a
 * turbine that is still clogged only buys another two minutes.
 *
 * <p>Turbines are found through the plant survey rather than by scanning the world, so this costs
 * one cached lookup per registered plant instead of a search of every block in the city.
 */
public class FireDutyGoal extends Goal {

    /** Close enough to work on a machine, and how long the work takes. */
    private static final double REACH = 3.0D;
    private static final int WORK_TICKS = 40;

    private final ServiceEntity firefighter;
    private @Nullable BlockPos turbine;
    private int work;

    public FireDutyGoal(ServiceEntity firefighter) {
        this.firefighter = firefighter;
        setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (firefighter.role() != ServiceType.FIRE) {
            return false;
        }
        turbine = findTrouble();
        return turbine != null;
    }

    @Override
    public boolean canContinueToUse() {
        return turbine != null && stillInTrouble(turbine);
    }

    @Override
    public void stop() {
        turbine = null;
        work = 0;
        firefighter.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (turbine == null) {
            return;
        }
        firefighter.reportBusy();
        firefighter.getLookControl().setLookAt(
                turbine.getX() + 0.5D, turbine.getY() + 1.0D, turbine.getZ() + 0.5D);

        if (firefighter.distanceToSqr(turbine.getX() + 0.5D, turbine.getY() + 0.5D, turbine.getZ() + 0.5D)
                > REACH * REACH) {
            firefighter.getNavigation().moveTo(
                    turbine.getX() + 0.5D, turbine.getY(), turbine.getZ() + 0.5D, 1.0D);
            work = 0;
            return;
        }

        firefighter.getNavigation().stop();
        firefighter.swing(firefighter.getUsedItemHand());
        if (++work < WORK_TICKS) {
            return;
        }
        work = 0;

        if (!(firefighter.level().getBlockEntity(turbine) instanceof TurbineBlockEntity machine)) {
            turbine = null;
            return;
        }
        if (machine.burning()) {
            machine.douse();
            firefighter.level().playSound(null, turbine, SoundEvents.FIRE_EXTINGUISH,
                    SoundSource.BLOCKS, 1.0F, 1.0F);
        } else {
            machine.repair();
            firefighter.level().playSound(null, turbine, SoundEvents.ANVIL_USE,
                    SoundSource.BLOCKS, 0.7F, 1.4F);
        }
    }

    private boolean stillInTrouble(BlockPos pos) {
        return firefighter.level().getBlockEntity(pos) instanceof TurbineBlockEntity machine
                && (machine.burning() || machine.clogged());
    }

    /**
     * The nearest turbine in this city that is on fire or seized.
     *
     * <p>Burning ones win outright regardless of distance: a fire is a machine about to be gone, and
     * a clog is a machine that has merely stopped earning.
     */
    private @Nullable BlockPos findTrouble() {
        City city = firefighter.city();
        if (city == null || !(firefighter.level() instanceof ServerLevel level)) {
            return null;
        }
        BlockPos best = null;
        boolean bestBurning = false;
        double bestDistance = Double.MAX_VALUE;

        for (Structure structure : CityData.get(level.getServer()).structuresOf(city)) {
            if (structure.type() != StructureType.POWER_PLANT
                    || !structure.dimension().equals(level.dimension())
                    || !level.isLoaded(structure.min())) {
                continue;
            }
            for (BlockPos pos : PlantSurvey.of(level, structure.min(), structure.max()).turbines()) {
                if (!(level.getBlockEntity(pos) instanceof TurbineBlockEntity machine)) {
                    continue;
                }
                boolean burning = machine.burning();
                if (!burning && !machine.clogged()) {
                    continue;
                }
                double distance = firefighter.distanceToSqr(
                        pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
                if (burning && !bestBurning) {
                    best = pos;
                    bestBurning = true;
                    bestDistance = distance;
                } else if (burning == bestBurning && distance < bestDistance) {
                    best = pos;
                    bestDistance = distance;
                }
            }
        }
        return best;
    }
}
