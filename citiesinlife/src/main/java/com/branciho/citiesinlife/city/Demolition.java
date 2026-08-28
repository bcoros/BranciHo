package com.branciho.citiesinlife.city;

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
    private static final int MIN_RUIN = 32;

    /** One damaged building, and how much of it went. */
    private record Wound(UUID structureId, int blocks) {
    }

    private static final Map<UUID, Integer> PENDING = new HashMap<>();

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

        boolean ruined = wound.blocks() >= threshold(structure);
        // The honest test, and the same bar registering it had to clear in the first place: it
        // held people, and now it does not. Conditioned on it having held them, because a box that
        // was already below the bar would otherwise be condemned by the first creeper to walk past
        // - it would fail the "measures nothing" test before the explosion as easily as after.
        if (!ruined && structure.type().measured()
                && structure.usableCells() >= StructureType.MIN_USABLE_CELLS) {
            StructureScanner.Measurement now = StructureScanner.measure(
                    level, structure.min(), structure.max());
            ruined = now.usableCells() < StructureType.MIN_USABLE_CELLS;
        }
        if (!ruined) {
            return;
        }

        City city = data.city(structure.cityId());
        data.removeStructure(structure.id());
        if (city == null) {
            return;
        }
        ServerPlayer owner = server.getPlayerList().getPlayer(city.owner());
        if (owner != null) {
            owner.sendSystemMessage(Component.translatable(
                    "message.citiesinlife.structure_destroyed",
                    structure.name(), structure.type().displayName()));
        }
    }

    /**
     * How much of this one has to go before it is a ruin.
     *
     * <p>Half the footprint, and never less than {@link #MIN_RUIN}. A creeper takes about thirty
     * blocks in total and rarely that many inside one box, so an accident does not condemn a house;
     * a hundred blocks of TNT or a reactor going up does.
     */
    private static int threshold(Structure structure) {
        return Math.max(MIN_RUIN, structure.footprint() / 2);
    }

    /** Dropped with the world, so a new one does not inherit the last one's rubble. */
    public static void clear() {
        PENDING.clear();
    }
}
