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

    /** Close enough to the bay to get in. */
    private static final double AT_PARKING_SQR = 4.0D;

    /** How long to leave a citizen alone after a car park turned out to lead nowhere. */
    private static final int FAILED_SEARCH_COOLDOWN = 1200;

    private Commute() {
    }

    public static boolean driving(CitizenEntity citizen) {
        return citizen.activity() == CitizenEntity.ACTIVITY_DRIVING && citizen.carId() != null;
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

        if (citizen.blockPosition().distSqr(parking) > AT_PARKING_SQR) {
            // Still walking to the car. Whether we have taken this leg on depends entirely on
            // whether that walk actually got a path - moveTo returns false when it did not.
            return citizen.getNavigation().moveTo(
                    parking.getX() + 0.5D, parking.getY() + 1, parking.getZ() + 0.5D, 1.0D);
        }

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
