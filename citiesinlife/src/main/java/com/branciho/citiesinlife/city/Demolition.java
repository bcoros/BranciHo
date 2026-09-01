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
     * Health taken off per block a blast removes from inside the box.
     *
     * <p>Four, so a building is finished when roughly a quarter of what it is made of has been
     * taken out of it. A building is not a solid lump — most of a house is the air inside it — so
     * demanding every block would mean nothing short of a nuclear warhead ever finished anything,
     * which was the previous behaviour and is why TNT left registrations standing over rubble.
     */
    private static final int DAMAGE_PER_BLOCK = 4;

    /** One damaged building, and how much of it went. */
    private record Wound(UUID structureId, int blocks) {
    }

    private static final Map<UUID, Integer> PENDING = new HashMap<>();

    /**
     * Cap a block count so it can be multiplied without wrapping.
     *
     * <p>A levelled region reports {@link Integer#MAX_VALUE} destroyed blocks, and four times that
     * is a large negative number — which reads as healing, and is how a building would survive
     * being at the centre of a crater.
     */
    private static int saturate(int blocks) {
        return Math.min(blocks, Integer.MAX_VALUE / DAMAGE_PER_BLOCK);
    }

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

        // Health is the whole of it now, and it is a number the player can watch fall: structure
        // mode draws it over the box. There is no second, invisible rule that can condemn a
        // building whose bar still says it is standing.
        boolean ruined = structure.damage(saturate(wound.blocks()) * DAMAGE_PER_BLOCK);
        data.setDirty();

        City city = data.city(structure.cityId());

        // Standing, but with a hole in it. The city should feel that immediately rather than going
        // on housing people in a room that is now open to the sky - and the re-measure is also what
        // keeps the health bar honest, since the mass it is scaled against has just changed.
        if (!ruined && structure.type().measured()) {
            StructureScanner.Measurement now = StructureScanner.measure(
                    level, structure.min(), structure.max());
            if (now.usableCells() != structure.usableCells()) {
                structure.setMeasurement(now.usableCells());
                if (city != null) {
                    CitySimulation.refresh(data, city);
                }
            }
        }
        if (!ruined) {
            return;
        }

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
        // an assessment of a structure that will not be there. Harmless but pointless work.
        for (UUID structureId : city.structures()) {
            PENDING.remove(structureId);
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

    /** Dropped with the world, so a new one does not inherit the last one's rubble. */
    public static void clear() {
        PENDING.clear();
    }
}
