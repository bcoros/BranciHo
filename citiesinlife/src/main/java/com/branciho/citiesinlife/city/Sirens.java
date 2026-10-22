package com.branciho.citiesinlife.city;

import com.branciho.citiesinlife.nuclear.NuclearSimulation;
import com.branciho.citiesinlife.nuclear.Radiation;
import com.branciho.citiesinlife.nuclear.ReactorData;
import com.branciho.citiesinlife.nuclear.ReactorState;
import com.branciho.citiesinlife.structure.Structure;
import com.branciho.citiesinlife.structure.StructureType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Whether a city's sirens are up, and the only place that decides it.
 *
 * <p>They used to be painted. The missile director kept a set of cities under attack and, whenever
 * that set changed, walked every <em>registered structure box</em> the city owned looking for siren
 * blocks to switch on. Every word of that was a bug. A siren standing on a pole in the street —
 * which is where anybody sensible puts one — belonged to no box and was therefore invisible to the
 * only code that could ever raise it, so it stood there silently through the entire war. And the
 * walk only happened on the tick the answer changed, so a siren placed, loaded or rebuilt at any
 * other moment kept whatever state it happened to have.
 *
 * <p>So nothing is painted now. Sirens ask, on their own timer, and this answers. That inverts the
 * cost — one cheap question per siren per second instead of one expensive sweep per city — and it
 * means a siren works wherever it is standing, which is the only rule a player should have to know.
 *
 * <p>Four reasons a city sounds — something in the air aimed at it, an alert declared at the city
 * hall, a reactor of its own past the point of no return, and fallout still blowing off a crater on
 * its own ground, whether that crater came from its own reactor or somebody else's warhead. They
 * are OR-ed rather than ranked, so the sirens stay up until every one of them has gone away: a
 * warhead landing while the reactor is still critical is not the all-clear.
 *
 * <p>Over all four sits the city's mute, which wins.
 */
public final class Sirens {

    /** How long an answer is good for. A second late to a siren nobody will notice. */
    private static final int CACHE_TICKS = 20;

    /**
     * Cities with something in the air aimed at them, written by the missile director.
     *
     * <p>Its sweep is the only thing that can know this — it is the code that walks the live
     * missile entities — so it pushes the answer here rather than this pulling it back out.
     */
    private static final Set<UUID> THREATENED = new HashSet<>();

    private record Answer(boolean wailing, long at) {
    }

    /**
     * The last answer for each city.
     *
     * <p>Sirens in the same city ask on different ticks, and a city with a dozen of them should not
     * pay a dozen times for the same walk over its structures.
     */
    private static final Map<UUID, Answer> RECENT = new HashMap<>();

    private Sirens() {
    }

    /** Told by the missile director, once per sweep, who has something incoming. */
    public static void threaten(Set<UUID> cities) {
        THREATENED.clear();
        THREATENED.addAll(cities);
    }

    /** Whether anything is in the air aimed at this city, for anything that needs only that. */
    public static boolean threatened(UUID cityId) {
        return THREATENED.contains(cityId);
    }

    public static boolean wailing(MinecraftServer server, City city) {
        long now = server.getTickCount();
        Answer cached = RECENT.get(city.id());
        if (cached != null && now - cached.at() < CACHE_TICKS && now >= cached.at()) {
            return cached.wailing();
        }
        boolean answer = decide(server, city);
        RECENT.put(city.id(), new Answer(answer, now));
        return answer;
    }

    private static boolean decide(MinecraftServer server, City city) {
        // The master mute wins over everything, including things that are genuinely on fire. That
        // is what it is for; see City#hushed.
        if (city.hushed()) {
            return false;
        }
        // Cheapest first, and the two that cost nothing are also the two that happen most.
        if (THREATENED.contains(city.id()) || city.alertLevel().rousing()) {
            return true;
        }
        return reactorScreaming(server, city) || falloutOnOurGround(server, city);
    }

    /**
     * A reactor of ours past the point of no return.
     *
     * <p>Red only. The amber band — hot, or over pressure, but recoverable — is a matter for the
     * plant's own alarm and the person standing at the monitor; putting the whole city's sirens up
     * for it would train everybody to ignore them. This is the level where the answer is to leave,
     * and it stays up for as long as the reactor stays there.
     *
     * <p>Read from the reactor's stored condition rather than by surveying its blocks, so asking is
     * a map lookup. A city with no reactor pays for one list walk.
     */
    private static boolean reactorScreaming(MinecraftServer server, City city) {
        ReactorData reactors = ReactorData.get(server);
        for (Structure structure : CityData.get(server).structuresOf(city)) {
            if (structure.type() != StructureType.NUCLEAR_PLANT
                    || !reactors.known(structure.id())) {
                continue;
            }
            ReactorState state = reactors.of(structure.id());
            if (state.melting()
                    || state.temperature >= NuclearSimulation.TEMP_CRITICAL
                    || state.pressure >= NuclearSimulation.PRESSURE_CRITICAL) {
                return true;
            }
        }
        return false;
    }

    /**
     * A crater of ours still giving off fallout.
     *
     * <p>The core going is the loudest thing that can happen to a city and the danger outlives it
     * by ten minutes, so the sirens outlive it by ten minutes too — they fade with the fallout
     * rather than with the bang. Judged by whether the crater is on ground this city has claimed,
     * which is the same test everything else here uses for "ours".
     */
    private static boolean falloutOnOurGround(MinecraftServer server, City city) {
        if (!Radiation.any()) {
            return false;
        }
        for (BlockPos crater : Radiation.craters(city.dimension())) {
            City over = Diplomacy.owner(server, city.dimension(), crater);
            if (over != null && over.id().equals(city.id())) {
                return true;
            }
        }
        return false;
    }

    /** Dropped with the world, so a new one does not inherit the last one's emergency. */
    public static void clear() {
        THREATENED.clear();
        RECENT.clear();
    }
}
