package com.branciho.citiesinlife.road;

import com.branciho.citiesinlife.city.CityData;
import com.branciho.citiesinlife.config.CitiesInLifeConfig;
import com.branciho.citiesinlife.entity.CarEntity;
import com.branciho.citiesinlife.entity.CitizenEntity;
import com.branciho.citiesinlife.registry.ModEntities;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * Whether this journey is worth driving, and the whole of getting into a car if it is.
 *
 * <p>Not a Goal, deliberately. The drive happens inside {@code CitizenWorkGoal} and
 * {@code CitizenSleepGoal}, both of which already hold {@code Goal.Flag.MOVE} at priorities 2 and 3,
 * so nothing below them can steal the citizen mid-trip and no goal needed renumbering. A new Goal
 * would have had to fight those two for the same flag.
 *
 * <p>Stateless: every decision is re-derived from the world, the way {@code ServerActions} does it.
 * The one piece of memory is a cooldown on the citizen, so a car park with no route out of it is
 * not retried every three seconds forever.
 */
public final class Commute {

    /**
     * How far a citizen will walk to fetch a car.
     *
     * <p>Kept well inside the roughly 56 blocks vanilla pathfinding can actually deliver at this
     * mob's FOLLOW_RANGE of 48. A longer search would hand out walks that silently return no path,
     * which is worse than not driving at all.
     */
    private static final int PARKING_SEARCH = 40;

    /** How near the destination the road has to come for a car to be worth taking. */
    private static final int DROPOFF_SEARCH = 48;

    /**
     * Close enough to the bay to get in, measured flat and squared.
     *
     * <p>Horizontal only, and deliberately loose. This was 4.0 measured in three dimensions, which
     * meant a citizen had to stop within about two blocks of the bay counting the height difference
     * as well - and vanilla navigation routinely finishes a block or two short, or finds the bay
     * already occupied by somebody else and stops beside it. Every one of those cases left the
     * citizen standing at the car park forever: near enough to have arrived, too far to board, and
     * with the work goal handing the whole journey to a car that never came.
     */
    private static final double AT_PARKING_SQR = 9.0D;

    /** How far above or below the bay still counts as standing on it. */
    private static final int AT_PARKING_HEIGHT = 3;

    /**
     * How long a citizen may spend trying to reach a bay before giving up and walking.
     *
     * <p>Twenty seconds. The bug this exists to make impossible is a citizen stuck heading for a
     * car park it can never quite stand on, which no amount of loosening the arrival test can rule
     * out entirely - a bay behind a locked door still paths and still never arrives.
     */
    private static final int MAX_BOARDING_TICKS = 400;

    /** Roughly how many ticks pass between two attempts, since this is not called every tick. */
    private static final int REPATH_ESTIMATE = 60;

    /** How long to leave a citizen alone after a car park turned out to lead nowhere. */
    private static final int FAILED_SEARCH_COOLDOWN = 1200;

    private Commute() {
    }

    public static boolean driving(CitizenEntity citizen) {
        return citizen.activity() == CitizenEntity.ACTIVITY_DRIVING && citizen.carId() != null;
    }

    /**
     * Whether the citizen is standing near enough to the bay to get in.
     *
     * <p>Flat distance plus a height band, rather than one three-dimensional test. A bay is a piece
     * of ground: the citizen stands on top of it, approaches it from beside it, and may be a step up
     * or down from it. Folding all of that into a single squared distance is what made arriving at a
     * car park and boarding a car two different things.
     */
    private static boolean atParking(CitizenEntity citizen, BlockPos parking) {
        BlockPos here = citizen.blockPosition();
        if (Math.abs(here.getY() - parking.getY()) > AT_PARKING_HEIGHT) {
            return false;
        }
        double dx = here.getX() - parking.getX();
        double dz = here.getZ() - parking.getZ();
        return dx * dx + dz * dz <= AT_PARKING_SQR;
    }

    /**
     * Take this leg of the journey by car, if that is the sensible thing to do.
     *
     * <p>Returns true only when it has genuinely taken responsibility - the citizen is either now
     * walking to a car park along a path that exists, or sitting in a moving car. Every other case
     * returns false so the caller falls back to walking. Returning true on a walk that was never
     * issued is how you freeze a citizen for a whole shift.
     */
    public static boolean tryDrive(CitizenEntity citizen, BlockPos destination) {
        if (!CitiesInLifeConfig.carsEnabled() || !citizen.mayLookForCar()) {
            return false;
        }
        if (!(citizen.level() instanceof ServerLevel level)) {
            return false;
        }
        MinecraftServer server = level.getServer();

        int threshold = CitiesInLifeConfig.carDistance();
        if (citizen.blockPosition().distSqr(destination) < (double) threshold * threshold) {
            return false;
        }

        RoadNetwork roads = RoadNetwork.get(server);
        BlockPos parking = roads.nearestWith(
                level.dimension(), citizen.blockPosition(), PARKING_SEARCH, RoadTile.PARKING);
        if (parking == null) {
            // No bay within reach. Walk, and do not keep asking.
            citizen.holdOffDriving(FAILED_SEARCH_COOLDOWN);
            return false;
        }

        if (!atParking(citizen, parking)) {
            // Still walking to the car. Two ways this can end badly, and both are handled: the walk
            // may find no path at all, in which case moveTo says so and the caller walks to work
            // instead; or the bay may be somewhere the citizen can approach but never quite stand
            // on, which is what the boarding budget is for.
            if (citizen.boardingTicks() > MAX_BOARDING_TICKS) {
                citizen.holdOffDriving(FAILED_SEARCH_COOLDOWN);
                citizen.resetBoarding();
                return false;
            }
            // Charged per attempt rather than per tick, because this runs on the goal's repath
            // countdown - once every sixty ticks for work, eighty for home.
            citizen.tickBoarding(REPATH_ESTIMATE);
            return citizen.getNavigation().moveTo(
                    parking.getX() + 0.5D, parking.getY() + 1, parking.getZ() + 0.5D, 1.0D);
        }
        citizen.resetBoarding();

        BlockPos dropoff = roads.nearestWith(level.dimension(), destination, DROPOFF_SEARCH, 0);
        if (dropoff == null) {
            citizen.holdOffDriving(FAILED_SEARCH_COOLDOWN);
            return false;
        }

        LongArrayList route = RoadRouter.route(
                roads, CityData.get(server), level.dimension(), citizen.cityId(), parking, dropoff);
        if (route == null || route.size() < 2) {
            // Standing at a bay that leads nowhere useful. Walking is the right answer, and asking
            // again in sixty ticks would only produce the same nothing.
            citizen.holdOffDriving(FAILED_SEARCH_COOLDOWN);
            return false;
        }

        CarEntity car = ModEntities.CAR.get().create(level);
        if (car == null) {
            return false;
        }
        car.moveTo(parking.getX() + 0.5D, parking.getY() + 1.0D, parking.getZ() + 0.5D,
                citizen.getYRot(), 0.0F);
        car.setRoute(route);
        level.addFreshEntity(car);
        car.board(citizen);
        return true;
    }

    /**
     * Give up on a trip in progress and put the passenger down where the car has got to.
     *
     * <p>Called from both goals' {@code stop}, which is reachable precisely because the passenger
     * keeps its AI. A shift flipping over or a bed being mined mid-drive both land here.
     */
    public static void abandon(CitizenEntity citizen) {
        if (!driving(citizen)) {
            return;
        }
        if (citizen.level() instanceof ServerLevel level && citizen.carId() != null
                && level.getEntity(citizen.carId()) instanceof CarEntity car) {
            car.dropOff(false);
            return;
        }
        // The car is already gone; just hand the citizen back its body.
        CarEntity.release(citizen);
    }
}
