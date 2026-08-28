package com.branciho.citiesinlife.service;

import com.branciho.citiesinlife.city.City;
import com.branciho.citiesinlife.city.CityData;
import com.branciho.citiesinlife.config.CitiesInLifeConfig;
import com.branciho.citiesinlife.entity.CarEntity;
import com.branciho.citiesinlife.entity.ServiceEntity;
import com.branciho.citiesinlife.registry.ModEntities;
import com.branciho.citiesinlife.road.RoadNetwork;
import com.branciho.citiesinlife.road.RoadRouter;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Sending a patrol out.
 *
 * <p>Police, fire and ambulance crews have always walked. That was never wrong exactly — they are
 * dispatched to things within about fifty blocks and vanilla pathfinding covers that — but it
 * meant a city with three fully staffed services looked and sounded identical to one with none,
 * and the roads the player spent an afternoon laying carried nothing but commuters.
 *
 * <p>So a station with somebody on duty now sends a vehicle out along those roads and brings it
 * back. It is a patrol rather than a call-out on purpose: a call-out only happens when something
 * has gone wrong, and a city where you never see a police car until there is a crime is a city
 * where you never see a police car. This is the part of a service you are supposed to notice
 * while nothing is happening.
 *
 * <p>Nothing here is saved. A patrol is a couple of minutes of driving and the car discards itself
 * at the end of it or on a timeout; a station that unloads mid-patrol simply sends another one out
 * when it comes back.
 */
public final class ServicePatrol {

    /**
     * Roughly how often a station considers sending somebody out.
     *
     * <p>The spawner runs this every forty ticks, so one in twenty-four is about once a minute per
     * station. A patrol takes up to two minutes, and only one is allowed out at a time, so a busy
     * station has a car on the road most of the time and a quiet one does not.
     */
    private static final int CHANCE = 24;

    /** How far from the station to look for somewhere to start. */
    private static final int ROAD_SEARCH = 48;

    /**
     * How far out a patrol will go looking for a destination, and how many tiles to consider.
     *
     * <p>Kept well inside what a car can actually cover. The route is driven out and back, and a
     * car writes its trip off after two minutes - which at street speed is a little over four
     * hundred blocks including every bend the road takes.
     */
    private static final int PATROL_RANGE = 140;
    private static final int CANDIDATES = 512;

    /** Below this the drive is not worth the trouble of getting in the car. */
    private static final int MIN_TRIP = 40;

    private ServicePatrol() {
    }

    /**
     * Consider sending a vehicle out from this station.
     *
     * <p>Silent about every reason it might not: no roads, nobody on duty, one already out, a
     * route that does not exist. All of those are ordinary, and a station that logged each of them
     * once a minute would be a nuisance rather than a diagnostic.
     */
    public static void consider(ServerLevel level, City city, ServiceType service,
                                BlockPos station, List<UUID> onDuty) {
        if (!service.drives() || onDuty.isEmpty()) {
            return;
        }
        // The same setting that switches citizens' cars off. Somebody who turned cars off to save
        // frames did not mean "except the emergency services".
        if (!CitiesInLifeConfig.carsEnabled()) {
            return;
        }
        if (level.random.nextInt(CHANCE) != 0) {
            return;
        }

        ServiceEntity crew = available(level, onDuty);
        if (crew == null) {
            return;
        }

        RoadNetwork roads = RoadNetwork.get(level.getServer());
        BlockPos from = roads.nearestWith(level.dimension(), station, ROAD_SEARCH, 0);
        if (from == null) {
            return;
        }
        BlockPos to = destination(level, roads, from);
        if (to == null) {
            return;
        }

        LongArrayList out = RoadRouter.route(
                roads, CityData.get(level.getServer()), level.dimension(), city.id(), from, to);
        if (out == null || out.size() < 2) {
            return;
        }
        LongArrayList route = thereAndBack(out);

        CarEntity car = ModEntities.CAR.get().create(level);
        if (car == null) {
            return;
        }
        car.moveTo(from.getX() + 0.5D, from.getY() + 1.0D, from.getZ() + 0.5D,
                crew.getYRot(), 0.0F);
        car.setRoute(route);
        level.addFreshEntity(car);
        // The livery comes off the crew rather than off the station, so a paramedic is in an
        // ambulance because of what they are rather than because of what dispatched them.
        car.board(crew);
    }

    /**
     * The route out, with the way home stuck on the end.
     *
     * <p>A one-way patrol would leave the crew wherever the road ran out, a hundred blocks from
     * the station they are supposed to be staffing, and the next patrol would have to fetch them
     * back from there. Driving the same road home costs nothing - the route is already known
     * good in both directions - and it means a patrol ends where it started, which is what the
     * word means.
     */
    private static LongArrayList thereAndBack(LongArrayList out) {
        LongArrayList round = new LongArrayList();
        round.addAll(out);
        // From the second-to-last back to the first: the turning point itself is already there,
        // and repeating it would only make the car sit still for a tick.
        for (int i = out.size() - 2; i >= 0; i--) {
            round.add(out.getLong(i));
        }
        return round;
    }

    /**
     * Somebody on duty who is not already out.
     *
     * <p>One patrol per station at a time, which the car-id check gives for nothing: whoever is
     * driving has one, and if anybody does then this station's vehicle is already on the road.
     */
    private static @Nullable ServiceEntity available(ServerLevel level, List<UUID> onDuty) {
        ServiceEntity free = null;
        for (UUID id : onDuty) {
            if (!(level.getEntity(id) instanceof ServiceEntity crew)) {
                continue;
            }
            if (crew.carId() != null) {
                return null;
            }
            if (free == null && crew.isAlive() && crew.drives()) {
                free = crew;
            }
        }
        return free;
    }

    /**
     * Somewhere worth driving to.
     *
     * <p>A road tile picked at random from those in range rather than the furthest one, so two
     * patrols out of the same station do not trace the same line up and down the same street all
     * day. Short hops are rejected outright: a car that appears, drives thirty blocks and vanishes
     * reads as a glitch rather than as a patrol.
     */
    private static @Nullable BlockPos destination(ServerLevel level, RoadNetwork roads,
                                                  BlockPos from) {
        LongArrayList tiles = roads.near(level.dimension(), from, PATROL_RANGE, CANDIDATES);
        if (tiles.isEmpty()) {
            return null;
        }
        // Ten goes rather than a filtered list, because the filter would walk five hundred tiles
        // every time and this finds one on the first or second try in any city with roads.
        for (int attempt = 0; attempt < 10; attempt++) {
            BlockPos candidate = BlockPos.of(tiles.getLong(level.random.nextInt(tiles.size())));
            if (candidate.distSqr(from) >= (double) MIN_TRIP * MIN_TRIP) {
                return candidate;
            }
        }
        return null;
    }
}
