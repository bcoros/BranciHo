package com.branciho.citiesinlife.service;

import com.branciho.citiesinlife.blockentity.TurbineBlockEntity;
import com.branciho.citiesinlife.city.City;
import com.branciho.citiesinlife.city.CityData;
import com.branciho.citiesinlife.config.CitiesInLifeConfig;
import com.branciho.citiesinlife.entity.CarEntity;
import com.branciho.citiesinlife.entity.CitizenEntity;
import com.branciho.citiesinlife.entity.ServiceEntity;
import com.branciho.citiesinlife.plant.PlantSurvey;
import com.branciho.citiesinlife.registry.ModEntities;
import com.branciho.citiesinlife.road.RoadNetwork;
import com.branciho.citiesinlife.road.RoadRouter;
import com.branciho.citiesinlife.structure.Structure;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Getting a crew to where they are needed, and showing the city its own services.
 *
 * <p>The first version of this sent a vehicle out on a random loop and brought it back, and it
 * almost never ran. Three reasons, and they compounded: a station only ever hired anybody when
 * there was already an incident, so in a quiet city nobody was on duty to put in a car; a crew that
 * had ever boarded a vehicle which then vanished without dropping them off kept a dangling car id
 * forever, and one of those permanently blocked every future dispatch from that station; and on top
 * of both there was a one-in-twenty-four gate. The result was a feature that worked perfectly and
 * fired approximately never.
 *
 * <p>So the trigger is now the thing it always should have been: <b>the journey</b>. When there is
 * something for this service to deal with and it is further away than a citizen would walk to,
 * they drive to it — the same hundred-block rule the commuters answer to, from the same setting.
 * An ambulance crossing the city to somebody who is hurt is both the honest reason for a vehicle to
 * exist and far more visible than any patrol.
 *
 * <p>The patrol stayed, underneath, for the case that has no incident: a city where nothing is
 * wrong should still see a police car go past now and then. That is what a service looks like when
 * it is working.
 */
public final class ServicePatrol {

    /**
     * Roughly how often a station sends somebody out on a plain patrol, with nothing wrong.
     *
     * <p>Only ever gates the patrol. A real call-out is dispatched the moment it is noticed — an
     * ambulance that rolled a die before deciding whether to attend would be a strange ambulance.
     */
    private static final int PATROL_CHANCE = 8;

    /**
     * How far from the crew a road has to be for them to get into a vehicle on it.
     *
     * <p>Deliberately short. The crew is put into the car where it spawns rather than walking to
     * it, which is right for an emergency vehicle leaving a station — but it means the distance is
     * a visible hop, and thirty-two blocks of hop would read as a teleport rather than as getting
     * in. A station with no road within thirty-two blocks simply does not run vehicles, which is
     * true of a fire station with no road outside it.
     */
    private static final int PICKUP_SEARCH = 32;

    /** How near the destination the road has to come for driving there to be worth it. */
    private static final int DROPOFF_SEARCH = 48;

    /** How far out to look for something this service should be attending. */
    private static final int CALLOUT_SEARCH = 256;

    /** How far out a plain patrol will wander. */
    private static final int PATROL_RANGE = 140;

    /**
     * How many road tiles a patrol will consider before picking one.
     *
     * <p>Generous on purpose. {@code RoadNetwork.near} walks chunks in ascending order and stops
     * dead the moment it has this many, so a limit smaller than the number of tiles actually in
     * range does not sample them — it returns the first corner of the search box and nothing else,
     * and every patrol out of that station drives up the same street forever. Four thousand covers
     * a hundred-and-forty-block radius of even a dense grid.
     */
    private static final int CANDIDATES = 4096;

    /** Below this a patrol is not worth the trouble of getting in the car. */
    private static final int MIN_PATROL = 40;

    private ServicePatrol() {
    }

    /**
     * Consider sending a vehicle out from this station.
     *
     * <p>Silent about every reason it might not: no roads, nobody on duty, one already out, a route
     * that does not exist. All of those are ordinary, and a station that logged each of them once
     * every two seconds would be a nuisance rather than a diagnostic.
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

        ServiceEntity crew = available(level, onDuty);
        if (crew == null) {
            return;
        }

        // A real job first. Only if there is nothing to attend does anybody go for a drive.
        BlockPos callout = callout(level, city, service, crew);
        if (callout != null) {
            dispatch(level, city, crew, callout, false);
            return;
        }
        if (level.random.nextInt(PATROL_CHANCE) == 0) {
            patrol(level, city, crew, station);
        }
    }

    // ------------------------------------------------------------- the crew

    /**
     * Somebody on duty who is not already out.
     *
     * <p>One vehicle per station at a time, which the car id gives for nothing: whoever is driving
     * has one. The important part is the <em>stale</em> case. A crew's car id is not saved and is
     * only cleared when the car puts them down, so a vehicle removed any other way — a chunk
     * unloading under it, a command, a world reload — used to leave an id pointing at nothing, and
     * that one crew member blocked every dispatch from their station for the rest of the save.
     * Checking that the car still exists turns a permanent deadlock into a hiccup.
     */
    private static @Nullable ServiceEntity available(ServerLevel level, List<UUID> onDuty) {
        ServiceEntity free = null;
        for (UUID id : onDuty) {
            if (!(level.getEntity(id) instanceof ServiceEntity crew)) {
                continue;
            }
            UUID car = crew.carId();
            if (car != null) {
                if (level.getEntity(car) instanceof CarEntity) {
                    // Genuinely out. Nobody else goes until they are back.
                    return null;
                }
                crew.setCarId(null);
            }
            if (free == null && crew.isAlive() && crew.drives() && !crew.leaving()) {
                free = crew;
            }
        }
        return free;
    }

    // ---------------------------------------------------------- the journey

    /**
     * Something this service should be attending, far enough away to drive to.
     *
     * <p>The distance test is {@link CitiesInLifeConfig#carDistance()} — the same hundred blocks a
     * citizen has to be from work before they fetch a car. Anything nearer, they walk, exactly as
     * they always did: an ambulance driving forty blocks would spend longer getting in and out of
     * the vehicle than walking would have taken.
     */
    private static @Nullable BlockPos callout(ServerLevel level, City city, ServiceType service,
                                              ServiceEntity crew) {
        int threshold = CitiesInLifeConfig.carDistance();
        double minimum = (double) threshold * threshold;
        return switch (service) {
            case POLICE -> farCitizen(level, city, crew, minimum, true);
            case HOSPITAL -> farCitizen(level, city, crew, minimum, false);
            case FIRE -> farTurbine(level, city, crew, minimum);
            default -> null;
        };
    }

    /** The nearest of this city's people who need this service, beyond the driving threshold. */
    private static @Nullable BlockPos farCitizen(ServerLevel level, City city, ServiceEntity crew,
                                                 double minimum, boolean criminals) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        AABB around = new AABB(crew.blockPosition()).inflate(CALLOUT_SEARCH);
        for (CitizenEntity citizen : level.getEntitiesOfClass(CitizenEntity.class, around)) {
            if (!citizen.isAlive() || !city.id().equals(citizen.cityId())) {
                continue;
            }
            boolean wanted = criminals
                    ? citizen.criminal()
                    : citizen.getHealth() < citizen.getMaxHealth();
            if (!wanted) {
                continue;
            }
            double distance = crew.blockPosition().distSqr(citizen.blockPosition());
            if (distance < minimum || distance >= bestDistance) {
                continue;
            }
            bestDistance = distance;
            best = citizen.blockPosition();
        }
        return best;
    }

    /** A turbine of this city's that is burning or seized, beyond the driving threshold. */
    private static @Nullable BlockPos farTurbine(ServerLevel level, City city, ServiceEntity crew,
                                                 double minimum) {
        for (Structure structure : CityData.get(level.getServer()).structuresOf(city)) {
            if (!structure.type().isPlant()
                    || !structure.dimension().equals(level.dimension())
                    || !level.isLoaded(structure.min())) {
                continue;
            }
            for (BlockPos at : PlantSurvey.of(level, structure.min(), structure.max()).turbines()) {
                if (crew.blockPosition().distSqr(at) < minimum) {
                    continue;
                }
                if (level.getBlockEntity(at) instanceof TurbineBlockEntity machine
                        && (machine.burning() || machine.clogged())) {
                    return at;
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------ the driving

    /** Drive to a call. One way: they have somewhere to be, and they walk back afterwards. */
    private static void dispatch(ServerLevel level, City city, ServiceEntity crew,
                                 BlockPos destination, boolean roundTrip) {
        RoadNetwork roads = RoadNetwork.get(level.getServer());
        BlockPos from = roads.nearestWith(level.dimension(), crew.blockPosition(), PICKUP_SEARCH, 0);
        BlockPos to = roads.nearestWith(level.dimension(), destination, DROPOFF_SEARCH, 0);
        if (from == null || to == null || from.equals(to)) {
            return;
        }
        LongArrayList out = RoadRouter.route(
                roads, CityData.get(level.getServer()), level.dimension(), city.id(), from, to);
        if (out == null || out.size() < 2) {
            return;
        }
        drive(level, crew, from, roundTrip ? thereAndBack(out) : out);
    }

    /** Go for a drive, because nothing is wrong and a city should still see its own services. */
    private static void patrol(ServerLevel level, City city, ServiceEntity crew, BlockPos station) {
        RoadNetwork roads = RoadNetwork.get(level.getServer());
        BlockPos from = roads.nearestWith(level.dimension(), crew.blockPosition(), PICKUP_SEARCH, 0);
        if (from == null) {
            return;
        }
        BlockPos to = wander(level, roads, from);
        if (to == null) {
            return;
        }
        LongArrayList out = RoadRouter.route(
                roads, CityData.get(level.getServer()), level.dimension(), city.id(), from, to);
        if (out == null || out.size() < 2) {
            return;
        }
        // Out and back, so a patrol ends where it started rather than abandoning the crew at
        // whichever end of the road it happened to stop at.
        drive(level, crew, from, thereAndBack(out));
    }

    /** Put the crew in a vehicle at a road tile and set it going. */
    private static void drive(ServerLevel level, ServiceEntity crew, BlockPos from,
                              LongArrayList route) {
        CarEntity car = ModEntities.CAR.get().create(level);
        if (car == null) {
            return;
        }
        double x = from.getX() + 0.5D;
        double y = from.getY() + 1.0D;
        double z = from.getZ() + 0.5D;
        car.moveTo(x, y, z, crew.getYRot(), 0.0F);
        car.setRoute(route);
        level.addFreshEntity(car);
        // Put them in it rather than letting the car's first tick drag them across the gap. Both
        // end in the same place; this one does not spend a tick with the crew visibly standing
        // somewhere else.
        crew.teleportTo(x, y, z);
        // The livery comes off the crew rather than off whatever dispatched them, so a paramedic
        // is in an ambulance because of what they are.
        car.board(crew);
    }

    /**
     * Somewhere worth driving to for its own sake.
     *
     * <p>A road tile picked at random from those in range rather than the furthest one, so two
     * patrols out of the same station do not trace the same line up and down the same street all
     * day. Short hops are rejected outright: a car that appears, drives thirty blocks and vanishes
     * reads as a glitch rather than as a patrol.
     */
    private static @Nullable BlockPos wander(ServerLevel level, RoadNetwork roads, BlockPos from) {
        LongArrayList tiles = roads.near(level.dimension(), from, PATROL_RANGE, CANDIDATES);
        if (tiles.isEmpty()) {
            return null;
        }
        // Ten goes rather than a filtered list, because the filter would walk five hundred tiles
        // every time and this finds one on the first or second try in any city with roads.
        for (int attempt = 0; attempt < 10; attempt++) {
            BlockPos candidate = BlockPos.of(tiles.getLong(level.random.nextInt(tiles.size())));
            if (candidate.distSqr(from) >= (double) MIN_PATROL * MIN_PATROL) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * The route out, with the way home stuck on the end.
     *
     * <p>Only for a patrol. A call-out is one way on purpose — the crew has a reason to be at the
     * far end, and driving them straight back past the thing they were sent to would be absurd.
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
}
