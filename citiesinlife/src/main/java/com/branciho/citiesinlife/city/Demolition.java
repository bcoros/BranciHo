package com.branciho.citiesinlife.city;

import com.branciho.citiesinlife.net.ServerActions;
import com.branciho.citiesinlife.path.PathNetwork;
import com.branciho.citiesinlife.road.RoadNetwork;
import com.branciho.citiesinlife.scan.StructureScanner;
import com.branciho.citiesinlife.structure.Structure;
import com.branciho.citiesinlife.structure.StructureType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.branciho.citiesinlife.sim.CitySimulation;

/**
 * What an explosion does to the things that are drawn on the world rather than built out of it.
 *
 * <p>Roads, pavements and registered buildings were all completely immune to being blown up, and
 * for the same reason: none of them is a block. A road tile is a flag on a coordinate; a Planner
 * Wand box is an entry in saved data. TNT removed the stone the road was painted on and the road
 * stayed, invisible and still routing cars through the crater. A meltdown could take a whole
 * district down to bedrock and every building in it went on housing people.
 *
 * <p>So the markings now answer to the blast as well. Road and path tiles under a destroyed block
 * go with it, and a building measured out of existence loses its registration.
 *
 * <p>The structures are handled <b>one tick later</b>, and that is not an optimisation. The event
 * that lists an explosion's victims fires <em>before</em> any of them are removed, so re-measuring
 * a building at that moment measures the building that is about to stop existing. Waiting a tick
 * is the whole of how this gets a true answer.
 */
public final class Demolition {

    /** How many wounded buildings are re-measured per tick, so a meltdown is not one big spike. */
    private static final int PER_TICK = 4;

    /**
     * The least damage that condemns a building outright, whatever it still measures.
     *
     * <p>Buildings the mod does not measure — parks, plants, a military base — have no capacity to
     * lose, so "measures nothing now" cannot be the test for them. This is: enough of it is gone
     * that it is a ruin. Proportional to the footprint so it is the same judgement on a cottage and
     * on a power station, with a floor under it so a small hut is not condemned by a creeper.
     */
    private static final int MIN_RUIN = 16;

    /**
     * How much of a building has to be gone before it stops being one, as a fraction of footprint.
     *
     * <p>An eighth rather than a half. Half a building's footprint is somewhere between one and
     * several hundred blocks, which no ordinary explosion reaches — so in practice nothing short of
     * a warhead ever condemned anything, and blowing a house apart with TNT left the registration
     * standing over the rubble. An eighth is about one good charge on a small house and several on
     * a tower, which is the shape it should have had.
     */
    private static final int RUIN_SHARE = 8;

    /**
     * How much of its capacity a building may lose before it is written off.
     *
     * <p>The old test was "does it still hold anybody at all", which a big building passes with
     * one wall left. Sixty per cent gone is a wreck by any reading.
     */
    private static final int SURVIVING_PERCENT = 40;

    /** One damaged building, and how much of it went. */
    private record Wound(UUID structureId, int blocks) {
    }

    private static final Map<UUID, Integer> PENDING = new HashMap<>();

    /**
     * Damage each building has taken and not yet been written off for.
     *
     * <p>Separate from the queue, and it is the difference between a building that can be
     * demolished and one that cannot. The queue is emptied every time it is assessed, so without
     * this a building blown apart by ten charges was ten separate small explosions, each judged on
     * its own and each forgiven — and ten charges never added up to anything.
     *
     * <p>In memory, so a restart forgives the damage. That is deliberate: a building nobody has
     * finished demolishing should not quietly die a month later because a creeper chipped it.
     */
    private static final Map<UUID, Integer> DAMAGE = new HashMap<>();

    private Demolition() {
    }

    // ------------------------------------------------------------- the blast

    /**
     * An explosion has settled on which blocks it is taking.
     *
     * <p>Called with the list the explosion is actually about to remove, so a block that survived
     * its own blast resistance — or that the border rules took back out of the list — does not
     * take a road with it.
     */
    public static void blast(ServerLevel level, Collection<BlockPos> destroyed) {
        if (destroyed.isEmpty()) {
            return;
        }
        RoadNetwork roads = RoadNetwork.get(level.getServer());
        PathNetwork paths = PathNetwork.get(level.getServer());
        for (BlockPos pos : destroyed) {
            roads.mark(level.dimension(), pos, pos, 0, true);
            paths.mark(level.dimension(), pos, pos, true);
        }
        wound(level, destroyed);
    }

    /**
     * A whole region has been levelled at once, by something that removes its own blocks.
     *
     * <p>The meltdown carves its crater by hand rather than through an explosion's block list, so
     * none of this would ever hear about the largest hole the mod can make. Given a box instead of
     * a list, the tiles inside it are walked directly — a crater's bounding box is well over a
     * million positions and only a handful of them were ever marked.
     */
    public static void flatten(ServerLevel level, BlockPos min, BlockPos max) {
        RoadNetwork.get(level.getServer()).clearIn(level.dimension(), min, max);
        PathNetwork.get(level.getServer()).clearIn(level.dimension(), min, max);

        CityData data = CityData.get(level.getServer());
        for (int chunkX = min.getX() >> 4; chunkX <= max.getX() >> 4; chunkX++) {
            for (int chunkZ = min.getZ() >> 4; chunkZ <= max.getZ() >> 4; chunkZ++) {
                for (Structure structure : data.structuresInChunk(level.dimension(),
                        ChunkPos.asLong(chunkX, chunkZ))) {
                    if (structure.intersects(min, max)) {
                        // Anything inside a crater is a ruin by definition; there is no need to
                        // count what went when the answer is "all of it".
                        PENDING.merge(structure.id(), Integer.MAX_VALUE, Integer::max);
                    }
                }
            }
        }
    }

    /**
     * Work out which registered buildings this took a bite out of, and how big a bite.
     *
     * <p>Grouped by chunk rather than asked per block. A large blast is a few hundred positions and
     * {@code structureAt} walks a chunk's structure list every time it is called, so asking it once
     * per victim would be the same handful of buildings looked up three hundred times.
     */
    private static void wound(ServerLevel level, Collection<BlockPos> destroyed) {
        CityData data = CityData.get(level.getServer());
        Set<Long> chunks = new HashSet<>();
        for (BlockPos pos : destroyed) {
            chunks.add(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
        }
        List<Structure> nearby = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (long chunkKey : chunks) {
            for (Structure structure : data.structuresInChunk(level.dimension(), chunkKey)) {
                if (seen.add(structure.id())) {
                    nearby.add(structure);
                }
            }
        }
        if (nearby.isEmpty()) {
            return;
        }
        for (Structure structure : nearby) {
            int hits = 0;
            for (BlockPos pos : destroyed) {
                if (structure.contains(pos)) {
                    hits++;
                }
            }
            if (hits > 0) {
                PENDING.merge(structure.id(), hits, Integer::sum);
            }
        }
    }

    // ----------------------------------------------------------- the aftermath

    /** Every server tick, and does nothing at all unless something has just been blown up. */
    public static void tick(MinecraftServer server) {
        if (PENDING.isEmpty()) {
            return;
        }
        CityData data = CityData.get(server);
        List<Wound> batch = new ArrayList<>();
        for (Map.Entry<UUID, Integer> entry : PENDING.entrySet()) {
            batch.add(new Wound(entry.getKey(), entry.getValue()));
            if (batch.size() >= PER_TICK) {
                break;
            }
        }
        for (Wound wound : batch) {
            PENDING.remove(wound.structureId());
            assess(server, data, wound);
        }
    }

    /** Is it still a building? */
    private static void assess(MinecraftServer server, CityData data, Wound wound) {
        Structure structure = data.structure(wound.structureId());
        if (structure == null) {
            return;
        }
        ServerLevel level = server.getLevel(structure.dimension());
        if (level == null || !level.isLoaded(structure.min())) {
            // Nobody is there to see it and the blocks may not even be in memory. Leaving it
            // standing is the safe answer: a building is never condemned on a guess.
            return;
        }

        // Everything this one has taken, not just this blast. Saturating, because a levelled
        // region reports Integer.MAX_VALUE and a plain sum would wrap it straight to negative -
        // which reads as "undamaged" and is how a building survives being at the centre of a
        // crater.
        int total = DAMAGE.merge(structure.id(), wound.blocks(),
                (a, b) -> (int) Math.min(Integer.MAX_VALUE, (long) a + b));
        boolean ruined = total >= threshold(structure);

        City city = data.city(structure.cityId());

        // The honest test: it held people, and now it holds far fewer. Conditioned on it having
        // held them, because a box that was already below the bar would otherwise be condemned by
        // the first creeper to walk past - it would fail the test before the explosion as easily
        // as after.
        if (!ruined && structure.type().measured()
                && structure.usableCells() >= StructureType.MIN_USABLE_CELLS) {
            StructureScanner.Measurement now = StructureScanner.measure(
                    level, structure.min(), structure.max());
            int before = structure.usableCells();
            ruined = now.usableCells() < StructureType.MIN_USABLE_CELLS
                    || now.usableCells() * 100 < before * SURVIVING_PERCENT;
            if (!ruined && now.usableCells() < before) {
                // Standing, but with a hole in it. The city should feel that immediately rather
                // than going on housing people in a room that is now open to the sky.
                structure.setMeasurement(now.usableCells());
                if (city != null) {
                    CitySimulation.refresh(data, city);
                }
            }
        }
        if (!ruined) {
            return;
        }

        DAMAGE.remove(structure.id());
        data.removeStructure(structure.id());
        if (city == null) {
            return;
        }
        ServerPlayer owner = server.getPlayerList().getPlayer(city.owner());

        // The city hall takes the city with it, exactly as it does when its box is deleted by hand.
        // Without this the city outlives its seat: the borders stay claimed, the treasury stays
        // funded, and because there is one city per player per world the owner cannot found another
        // - their city hall is rubble and the game still insists they have one. There is nothing to
        // ask here the way the wand asks, because nobody chose this.
        if (structure.type() == StructureType.CITY_CORE) {
            razed(server, data, city, owner);
            return;
        }

        if (owner != null) {
            owner.sendSystemMessage(Component.translatable(
                    "message.citiesinlife.structure_destroyed",
                    structure.name(), structure.type().displayName()));
        }
    }

    /**
     * Wind up a city whose hall has just been destroyed.
     *
     * <p>Everything the city owned goes: its other registrations, its claimed land, its wars and its
     * record. The same {@link CityData#deleteCity} the Planner Wand uses, so a city razed by a
     * warhead ends in exactly the state as one deleted deliberately - which is the state a player
     * can found a new city from.
     */
    private static void razed(MinecraftServer server, CityData data, City city, ServerPlayer owner) {
        // Its other buildings are about to stop existing, so anything still queued against them is
        // an assessment of a structure that will not be there. Harmless but pointless work, and the
        // damage tally would otherwise sit in memory for the rest of the session.
        for (UUID structureId : city.structures()) {
            PENDING.remove(structureId);
            DAMAGE.remove(structureId);
        }

        int removed = data.deleteCity(city);
        if (owner != null) {
            owner.sendSystemMessage(Component.translatable(
                    "message.citiesinlife.city_razed", city.name(), removed));
            ServerActions.sync(owner);
        }
        // Everyone else still has this city in their Neighbours list, with buttons that now do
        // nothing at all.
        for (ServerPlayer other : server.getPlayerList().getPlayers()) {
            if (owner == null || !other.getUUID().equals(owner.getUUID())) {
                ServerActions.syncNeighbours(other);
            }
        }
    }

    /**
     * How much of this one has to go before it is a ruin.
     *
     * <p>An eighth of the footprint, and never less than {@link #MIN_RUIN}. A single creeper takes
     * around thirty blocks in total and rarely half that many inside one box, so an accident still
     * does not condemn a house — but somebody who sets out to demolish one now can, and a warhead
     * takes the district.
     */
    private static int threshold(Structure structure) {
        return Math.max(MIN_RUIN, structure.footprint() / RUIN_SHARE);
    }

    /** Dropped with the world, so a new one does not inherit the last one's rubble. */
    public static void clear() {
        PENDING.clear();
        DAMAGE.clear();
    }
}
