package com.branciho.citiesinlife.entity.ai;

import com.branciho.citiesinlife.city.City;
import com.branciho.citiesinlife.city.CityData;
import com.branciho.citiesinlife.entity.ServiceEntity;
import com.branciho.citiesinlife.service.ServiceType;
import com.branciho.citiesinlife.structure.Structure;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.goal.Goal;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Doing the rounds.
 *
 * <p>A city makes rubbish whether or not anybody deals with it, and once there is more of it than
 * the place can stand people stop wanting to move in. This is the only thing that takes it away: a
 * bin man walks to a building, spends a moment at it, and the pile goes down.
 *
 * <p>They go to a building rather than to a bin, because asking the player to place a wheelie bin
 * outside every house they build would be exactly the kind of data entry the path tool exists to
 * avoid.
 */
public class RefuseGoal extends Goal {

    private static final double REACH = 3.0D;
    private static final int COLLECT_TICKS = 30;

    /** How much one call takes away. Enough that a couple of collectors keep a real city clear. */
    private static final int COLLECTED = 45;

    /** How far a round goes. Beyond this it is another depot's district. */
    private static final double ROUND_RANGE = 96.0D;

    private final ServiceEntity binman;
    private @Nullable BlockPos stop;
    private int work;

    public RefuseGoal(ServiceEntity binman) {
        this.binman = binman;
        setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (binman.role() != ServiceType.GARBAGE) {
            return false;
        }
        City city = binman.city();
        if (city == null || city.refuse() <= 0) {
            return false;
        }
        stop = nextStop(city);
        return stop != null;
    }

    @Override
    public boolean canContinueToUse() {
        City city = binman.city();
        return stop != null && city != null && city.refuse() > 0;
    }

    @Override
    public void stop() {
        stop = null;
        work = 0;
        binman.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        City city = binman.city();
        if (stop == null || city == null) {
            return;
        }
        binman.reportBusy();

        if (binman.distanceToSqr(stop.getX() + 0.5D, stop.getY() + 0.5D, stop.getZ() + 0.5D)
                > REACH * REACH) {
            binman.getNavigation().moveTo(stop.getX() + 0.5D, stop.getY(), stop.getZ() + 0.5D, 1.0D);
            work = 0;
            return;
        }

        binman.getNavigation().stop();
        binman.swing(binman.getUsedItemHand());
        if (++work < COLLECT_TICKS) {
            return;
        }
        work = 0;
        city.addRefuse(-COLLECTED);
        binman.level().playSound(null, stop, SoundEvents.BARREL_CLOSE, SoundSource.NEUTRAL, 0.7F, 1.1F);
        stop = nextStop(city);
    }

    /** A building of this city, somewhere in reach, picked at random so the rounds vary. */
    private @Nullable BlockPos nextStop(City city) {
        if (!(binman.level() instanceof ServerLevel level)) {
            return null;
        }
        List<BlockPos> candidates = new ArrayList<>();
        for (Structure structure : CityData.get(level.getServer()).structuresOf(city)) {
            if (!structure.dimension().equals(level.dimension())) {
                continue;
            }
            BlockPos door = new BlockPos(
                    (structure.min().getX() + structure.max().getX()) / 2,
                    structure.min().getY(),
                    (structure.min().getZ() + structure.max().getZ()) / 2);
            if (binman.distanceToSqr(door.getX() + 0.5D, door.getY() + 0.5D, door.getZ() + 0.5D)
                    <= ROUND_RANGE * ROUND_RANGE) {
                candidates.add(door);
            }
        }
        return candidates.isEmpty()
                ? null
                : candidates.get(level.random.nextInt(candidates.size()));
    }
}
