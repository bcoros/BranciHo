package com.branciho.citiesinlife.sim;

import com.branciho.citiesinlife.city.City;
import com.branciho.citiesinlife.city.CityData;
import com.branciho.citiesinlife.entity.CitizenEntity;
import com.branciho.citiesinlife.entity.ServiceEntity;
import com.branciho.citiesinlife.registry.ModEntities;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.List;

/**
 * The one thing that ever goes wrong with the people themselves.
 *
 * <p>There is exactly one crime in this city and it is murder. That is not a placeholder for a
 * longer list — a single offence is what makes a police force legible: something is happening, you
 * can see who is doing it, and either somebody in uniform turns up or nobody does. A pickpocketing
 * mechanic would be invisible and a vandalism mechanic would be indistinguishable from a player
 * knocking a wall down.
 *
 * <p>The odds are set so that a city goes an hour or so between incidents, and a city with parks in
 * it goes considerably longer. Anything more frequent stops being a city and becomes a siege.
 */
public final class ServiceDirector {

    /** How often the dice are rolled. */
    private static final int INTERVAL_TICKS = 200;

    /**
     * One in this many, per citizen, per roll.
     *
     * <p>With fifteen people and a roll every ten seconds that is roughly one incident an hour. It
     * should feel like something that happens, not something that keeps happening.
     */
    private static final int CRIME_ODDS = 6_000;

    /**
     * How much park it takes to halve the odds.
     *
     * <p>Two thousand square metres — a square about forty-five metres on a side. Big enough that it
     * is a decision about the layout of the city and not a token square of grass behind the shops.
     */
    private static final int PARK_AREA_PER_HALVING = 2_000;

    private ServiceDirector() {
    }

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % INTERVAL_TICKS != 0) {
            return;
        }
        CityData data = CityData.get(server);
        for (City city : data.cities()) {
            ServerLevel level = server.getLevel(city.dimension());
            if (level == null) {
                continue;
            }
            rollForTrouble(server, level, city);
            finishCourses(server, data, level, city);
        }
    }

    /**
     * Let anybody who has finished a course out of it.
     *
     * <p>Training is stored as the game time it ends rather than as a countdown, so it keeps running
     * while the world is shut and cannot be dodged by logging out. The soldier already walking about
     * is told as well, since their training is what decides how fast they take ground.
     */
    private static void finishCourses(MinecraftServer server, CityData data, ServerLevel level,
                                      City city) {
        long now = level.getGameTime();
        for (City.Soldier soldier : List.copyOf(city.army())) {
            if (!soldier.inTraining() || now < soldier.trainingDoneAt()) {
                continue;
            }
            int trained = Math.min(ServiceEntity.MAX_TRAINING, soldier.training() + 1);
            city.replace(soldier.withTraining(trained));
            data.setDirty();

            for (ServiceEntity body : level.getEntities(ModEntities.SERVICE.get(),
                    entity -> entity.isAlive() && soldier.id().equals(entity.soldierId()))) {
                body.setTraining(trained);
            }

            ServerPlayer owner = server.getPlayerList().getPlayer(city.owner());
            if (owner != null) {
                owner.sendSystemMessage(Component.translatable(
                        "message.citiesinlife.training_done", soldier.name(), trained));
            }
        }
    }

    private static void rollForTrouble(MinecraftServer server, ServerLevel level, City city) {
        List<? extends CitizenEntity> citizens = level.getEntities(ModEntities.CITIZEN.get(),
                citizen -> citizen.isAlive() && city.id().equals(citizen.cityId()));
        if (citizens.size() < 2) {
            // It takes two: one to do it and one for it to be done to.
            return;
        }

        int odds = oddsFor(city);
        for (CitizenEntity citizen : citizens) {
            if (citizen.criminal() || level.random.nextInt(odds) != 0) {
                continue;
            }
            if (citizen.findVictim() == null) {
                continue;
            }
            citizen.setCriminal(true);
            level.playSound(null, citizen.blockPosition(), SoundEvents.WARDEN_ANGRY,
                    SoundSource.NEUTRAL, 0.7F, 1.6F);
            warn(server, city);
            return;
        }
    }

    /**
     * The odds against, for this city.
     *
     * <p>Parks push them out. Nothing else does, on purpose: the police do not prevent crime here,
     * they answer it, and pretending a station on the far side of town stops somebody snapping would
     * make the one thing the player can actually watch happen invisible.
     */
    private static int oddsFor(City city) {
        int halvings = Math.min(4, city.parkArea() / PARK_AREA_PER_HALVING);
        return CRIME_ODDS << halvings;
    }

    private static void warn(MinecraftServer server, City city) {
        ServerPlayer owner = server.getPlayerList().getPlayer(city.owner());
        if (owner != null) {
            owner.sendSystemMessage(Component.translatable("message.citiesinlife.crime", city.name()));
        }
    }
}
