package com.branciho.citiesinlife.city;

import com.branciho.citiesinlife.CitiesInLife;
import com.branciho.citiesinlife.structure.Structure;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Every city and structure in the world, and the indexes that make looking them up cheap.
 *
 * <p>Stored once on the overworld's data storage rather than per dimension, because a player has one
 * set of cities regardless of where they are standing, and searching every dimension's storage to
 * answer "which city am I in" would be silly.
 *
 * <p>The two chunk indexes are <em>derived</em> and are never written to disk — they are rebuilt in
 * {@link #load}. Saving them as well would mean two representations of the same fact that could drift
 * apart, and drift in save data is not something you find out about until someone's world is broken.
 */
public final class CityData extends SavedData {

    private static final String FILE_ID = "citiesinlife";

    /**
     * Bumped whenever the on-disk shape changes, so old saves can be migrated rather than corrupted.
     *
     * <p>2 marks the addition of a structure's stored cell count and measurement mode. It was left at
     * 1 when those fields were added, so nothing noticed the format change and old worlds loaded
     * silently wrong. 3 marks a city's diplomatic relations - which read back tolerantly from an
     * older save, but leaving the number alone after a shape change is the exact habit that caused
     * the problem at 2. 4 marks the creative treasury and who has turned it off. 5 marks the
     * airfields and who placed each one, which is what the twenty-per-player cap is counted from.
     */
    private static final int DATA_VERSION = 5;

    /**
     * How many airfields one player may have standing at once.
     *
     * <p>A cap rather than a cost, because an airfield is not bought, it is placed. Twenty is
     * generous for one city and small enough that a link between two of them still means something.
     */
    public static final int MAX_AIRFIELDS_PER_PLAYER = 20;

    private final Map<UUID, City> cities = new LinkedHashMap<>();
    private final Map<UUID, Structure> structures = new LinkedHashMap<>();

    /**
     * Players who have switched creative money off.
     *
     * <p>Stored as the exception rather than as the rule, because the rule is that creative mode
     * means infinite money. A player who has never pressed the key is not in here, which is also
     * what makes the setting arrive switched on for somebody who has just joined.
     */
    private final Set<UUID> creativeMoneyOff = new HashSet<>();

    /**
     * dimension -> packed position -> the player who placed the airfield there.
     *
     * <p>Saved, and the authority on whether an airfield works at all. Enforcing the cap only at
     * placement would leave anything that arrived another way - /fill, /clone, another mod - both
     * uncounted and fully functional, which in a mod built around creative building is the whole
     * cap defeated.
     */
    private final Map<ResourceKey<Level>, Map<Long, UUID>> airfields = new HashMap<>();

    /**
     * How many each player has, derived from {@link #airfields} and never saved.
     *
     * <p>Rebuilt in load. A derived index that is only correct in the session that built it is
     * exactly what territoryIndex exists to warn about.
     */
    private final Map<UUID, Integer> airfieldCounts = new HashMap<>();

    /** dimension -> chunk key -> owning city. */
    private final Map<ResourceKey<Level>, Map<Long, UUID>> territoryIndex = new HashMap<>();

    /** dimension -> chunk key -> structures touching that chunk. */
    private final Map<ResourceKey<Level>, Map<Long, List<UUID>>> structureIndex = new HashMap<>();

    /**
     * Chunks somebody's soldiers are standing in and slowly taking.
     *
     * <p>Kept here rather than in its own save file because a siege is a fact about two cities and
     * dies with either of them, and this is the only place that knows when a city stops existing.
     */
    private final Map<ResourceKey<Level>, Map<Long, Siege>> sieges = new HashMap<>();

    public static CityData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(CityData::new, CityData::load), FILE_ID);
    }

    // ------------------------------------------------------------------ cities

    public Collection<City> cities() {
        return cities.values();
    }

    public @Nullable City city(UUID id) {
        return cities.get(id);
    }

    /** The city this player owns in this dimension, if any. One per player per dimension for now. */
    public @Nullable City cityOf(UUID playerId, ResourceKey<Level> dimension) {
        for (City city : cities.values()) {
            if (city.owner().equals(playerId) && city.dimension().equals(dimension)) {
                return city;
            }
        }
        return null;
    }

    public boolean nameTaken(String name) {
        for (City city : cities.values()) {
            if (city.name().equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    public City createCity(String name, UUID owner, ResourceKey<Level> dimension, ChunkPos origin) {
        City city = new City(UUID.randomUUID(), name, owner, dimension);
        city.claim(origin.toLong());
        cities.put(city.id(), city);
        territoryIndex.computeIfAbsent(dimension, key -> new HashMap<>()).put(origin.toLong(), city.id());
        setDirty();
        return city;
    }

    /**
     * Wind a city up completely: its buildings, its land and the city itself.
     *
     * <p>Deleting the city hall used to leave the city standing with no seat, still holding its
     * treasury and its claims, and — because there is one city per player per world — with no way to
     * found another. Nothing about that was recoverable in game, so the city hall now takes the city
     * with it, and the player is asked before it does.
     *
     * @return how many registrations went with it
     */
    public int deleteCity(City city) {
        int removed = 0;
        for (UUID structureId : List.copyOf(city.structures())) {
            if (removeStructure(structureId)) {
                removed++;
            }
        }

        Map<Long, UUID> territory = territoryIndex.get(city.dimension());
        if (territory != null) {
            for (long chunkKey : city.claimedChunks().toLongArray()) {
                territory.remove(chunkKey);
            }
            if (territory.isEmpty()) {
                territoryIndex.remove(city.dimension());
            }
        }

        cities.remove(city.id());

        // Nobody is at war with a city that no longer exists. Left behind, these entries would sit in
        // every other city's save file forever, and a future city handed the same UUID would inherit
        // a grudge it never earned.
        for (City other : cities.values()) {
            other.forget(city.id());
        }

        // A siege this city was pressing, or one being pressed on its ground, is nothing at all now.
        Map<Long, Siege> besieged = sieges.get(city.dimension());
        if (besieged != null) {
            for (long chunkKey : city.claimedChunks()) {
                besieged.remove(chunkKey);
            }
        }
        for (Map<Long, Siege> index : sieges.values()) {
            index.values().removeIf(siege -> siege.attacker().equals(city.id()));
        }
        sieges.values().removeIf(Map::isEmpty);

        setDirty();
        return removed;
    }

    // ---------------------------------------------------------------- sieges

    /**
     * A chunk being taken: who is taking it and how far along they are.
     *
     * <p>Progress rather than a timer, so a chunk with four soldiers in it falls four times as fast
     * and one that has been left alone stays exactly where it was rather than quietly resetting.
     */
    public record Siege(UUID attacker, int progress) {
    }

    /** How much progress it takes to hold a chunk outright. */
    public static final int SIEGE_TARGET = 100;

    public @Nullable Siege siege(ResourceKey<Level> dimension, long chunkKey) {
        Map<Long, Siege> index = sieges.get(dimension);
        return index == null ? null : index.get(chunkKey);
    }

    /**
     * Push a siege forward, and say whether that finished it.
     *
     * <p>A different attacker arriving starts the count again from theirs. Two armies pushing the
     * same chunk at once and sharing a progress bar would mean whoever turned up second inherited
     * the first one's work.
     */
    public boolean advanceSiege(ResourceKey<Level> dimension, long chunkKey, UUID attacker, int amount) {
        Map<Long, Siege> index = sieges.computeIfAbsent(dimension, key -> new HashMap<>());
        Siege current = index.get(chunkKey);
        int progress = current != null && current.attacker().equals(attacker)
                ? current.progress() + amount
                : amount;
        index.put(chunkKey, new Siege(attacker, progress));
        setDirty();
        return progress >= SIEGE_TARGET;
    }

    public void clearSiege(ResourceKey<Level> dimension, long chunkKey) {
        Map<Long, Siege> index = sieges.get(dimension);
        if (index != null && index.remove(chunkKey) != null) {
            if (index.isEmpty()) {
                sieges.remove(dimension);
            }
            setDirty();
        }
    }

    /**
     * Take a chunk off one city and give it to another.
     *
     * <p>Unlike unclaiming, this does not refuse ground with a building on it. That refusal exists
     * to stop a player stranding their own structure outside their own borders; conquest is the one
     * case where the building changing hands is the entire point, and the War Planner Wand is what
     * finishes the job.
     */
    public void transferChunk(City from, City to, long chunkKey) {
        from.unclaim(chunkKey);
        to.claim(chunkKey);
        territoryIndex.computeIfAbsent(to.dimension(), key -> new HashMap<>()).put(chunkKey, to.id());
        clearSiege(to.dimension(), chunkKey);
        setDirty();
    }

    // -------------------------------------------------------- creative money

    /** Whether this player wants a creative treasury. Everybody does until they say otherwise. */
    public boolean creativeMoneyEnabled(UUID playerId) {
        return !creativeMoneyOff.contains(playerId);
    }

    /** Flip the setting. Returns what it is now, so the caller can say so on screen. */
    public boolean toggleCreativeMoney(UUID playerId) {
        boolean enabled;
        if (creativeMoneyOff.remove(playerId)) {
            enabled = true;
        } else {
            creativeMoneyOff.add(playerId);
            enabled = false;
        }
        setDirty();
        return enabled;
    }

    // -------------------------------------------------------------- territory

    public @Nullable City cityAtChunk(ResourceKey<Level> dimension, long chunkKey) {
        Map<Long, UUID> index = territoryIndex.get(dimension);
        if (index == null) {
            return null;
        }
        UUID cityId = index.get(chunkKey);
        return cityId == null ? null : cities.get(cityId);
    }

    public boolean claimChunk(City city, long chunkKey) {
        if (cityAtChunk(city.dimension(), chunkKey) != null) {
            return false;
        }
        city.claim(chunkKey);
        territoryIndex.computeIfAbsent(city.dimension(), key -> new HashMap<>()).put(chunkKey, city.id());
        setDirty();
        return true;
    }

    public boolean unclaimChunk(City city, long chunkKey) {
        if (!city.owns(chunkKey)) {
            return false;
        }
        // Refuse to drop ground a structure is standing on: the structure would keep occupying land
        // the city no longer owns, which is exactly the kind of orphaned state that is impossible to
        // explain to a player later.
        for (Structure structure : structuresInChunk(city.dimension(), chunkKey)) {
            if (structure.cityId().equals(city.id())) {
                return false;
            }
        }
        city.unclaim(chunkKey);
        Map<Long, UUID> index = territoryIndex.get(city.dimension());
        if (index != null) {
            index.remove(chunkKey);
        }
        setDirty();
        return true;
    }

    /** A chunk may only be claimed if it touches ground the city already owns. */
    public boolean isAdjacentToClaim(City city, ChunkPos chunk) {
        return city.owns(ChunkPos.asLong(chunk.x + 1, chunk.z))
                || city.owns(ChunkPos.asLong(chunk.x - 1, chunk.z))
                || city.owns(ChunkPos.asLong(chunk.x, chunk.z + 1))
                || city.owns(ChunkPos.asLong(chunk.x, chunk.z - 1));
    }

    // ------------------------------------------------------------- structures

    public @Nullable Structure structure(UUID id) {
        return structures.get(id);
    }

    public List<Structure> structuresOf(City city) {
        List<Structure> owned = new ArrayList<>();
        for (UUID id : city.structures()) {
            Structure structure = structures.get(id);
            if (structure != null) {
                owned.add(structure);
            }
        }
        return owned;
    }

    public List<Structure> structuresInChunk(ResourceKey<Level> dimension, long chunkKey) {
        Map<Long, List<UUID>> index = structureIndex.get(dimension);
        if (index == null) {
            return List.of();
        }
        List<UUID> ids = index.get(chunkKey);
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Structure> found = new ArrayList<>(ids.size());
        for (UUID id : ids) {
            Structure structure = structures.get(id);
            if (structure != null) {
                found.add(structure);
            }
        }
        return found;
    }

    /**
     * The registered structure whose box contains this position.
     *
     * <p>Used by the factory output block to find out which factory it stands in - a block has to be
     * able to ask "what am I part of" for a structure to mean anything to the world.
     */
    public @Nullable Structure structureAt(ResourceKey<Level> dimension, BlockPos pos) {
        long chunkKey = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
        for (Structure structure : structuresInChunk(dimension, chunkKey)) {
            if (structure.contains(pos)) {
                return structure;
            }
        }
        return null;
    }

    /** Anything already registered that would overlap this box. */
    /**
     * A structure belonging to somebody other than this city, anywhere in the chunk.
     *
     * <p>Asked before a chunk is claimed. Somebody else's power plant is allowed to stand on
     * unclaimed ground; letting a stranger buy the ground out from under it would let them lock its
     * owner out of their own machinery without ever touching a block.
     */
    public @Nullable Structure foreignStructureInChunk(ResourceKey<Level> dimension, long chunkKey,
                                                       UUID cityId) {
        for (Structure structure : structuresInChunk(dimension, chunkKey)) {
            if (!structure.cityId().equals(cityId)) {
                return structure;
            }
        }
        return null;
    }

    public @Nullable Structure overlapping(ResourceKey<Level> dimension, BlockPos min, BlockPos max) {
        for (int x = min.getX() >> 4; x <= max.getX() >> 4; x++) {
            for (int z = min.getZ() >> 4; z <= max.getZ() >> 4; z++) {
                for (Structure structure : structuresInChunk(dimension, ChunkPos.asLong(x, z))) {
                    if (structure.intersects(min, max)) {
                        return structure;
                    }
                }
            }
        }
        return null;
    }

    public void addStructure(City city, Structure structure) {
        structures.put(structure.id(), structure);
        city.addStructure(structure.id());
        indexStructure(structure);
        setDirty();
    }

    public boolean removeStructure(UUID structureId) {
        Structure structure = structures.remove(structureId);
        if (structure == null) {
            return false;
        }
        City city = cities.get(structure.cityId());
        if (city != null) {
            city.removeStructure(structureId);
        }
        Map<Long, List<UUID>> index = structureIndex.get(structure.dimension());
        if (index != null) {
            for (long chunkKey : structure.occupiedChunks()) {
                List<UUID> ids = index.get(chunkKey);
                if (ids != null) {
                    ids.remove(structureId);
                    if (ids.isEmpty()) {
                        index.remove(chunkKey);
                    }
                }
            }
        }
        setDirty();
        return true;
    }

    private void indexStructure(Structure structure) {
        Map<Long, List<UUID>> index =
                structureIndex.computeIfAbsent(structure.dimension(), key -> new HashMap<>());
        for (long chunkKey : structure.occupiedChunks()) {
            index.computeIfAbsent(chunkKey, key -> new ArrayList<>()).add(structure.id());
        }
    }

    // ------------------------------------------------------------- airfields

    public int airfieldCount(UUID playerId) {
        return airfieldCounts.getOrDefault(playerId, 0);
    }

    public boolean canPlaceAirfield(UUID playerId) {
        return airfieldCount(playerId) < MAX_AIRFIELDS_PER_PLAYER;
    }

    /** Record an airfield against the player who put it there. False when they are at the cap. */
    public boolean claimAirfield(UUID playerId, ResourceKey<Level> dimension, BlockPos pos) {
        if (!canPlaceAirfield(playerId)) {
            return false;
        }
        Map<Long, UUID> perDimension = airfields.computeIfAbsent(dimension, key -> new HashMap<>());
        UUID previous = perDimension.put(pos.asLong(), playerId);
        if (previous != null) {
            decrement(previous);
        }
        airfieldCounts.merge(playerId, 1, Integer::sum);
        setDirty();
        return true;
    }

    /** Forget an airfield that has been broken. */
    public boolean releaseAirfield(ResourceKey<Level> dimension, BlockPos pos) {
        Map<Long, UUID> perDimension = airfields.get(dimension);
        if (perDimension == null) {
            return false;
        }
        UUID owner = perDimension.remove(pos.asLong());
        if (owner == null) {
            return false;
        }
        if (perDimension.isEmpty()) {
            airfields.remove(dimension);
        }
        decrement(owner);
        setDirty();
        return true;
    }

    /**
     * The airfield nearest a point that belongs to a given city's owner.
     *
     * <p>Keyed off who placed it rather than off which city's ground it stands on, because that is
     * the only thing the airfield register actually records — and an airport built just outside the
     * city limits is still that player's airport.
     */
    public @Nullable BlockPos nearestAirfieldOf(ResourceKey<Level> dimension, UUID playerId,
                                                BlockPos near) {
        Map<Long, UUID> perDimension = airfields.get(dimension);
        if (perDimension == null) {
            return null;
        }
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Map.Entry<Long, UUID> entry : perDimension.entrySet()) {
            if (!entry.getValue().equals(playerId)) {
                continue;
            }
            BlockPos at = BlockPos.of(entry.getKey());
            double distance = at.distSqr(near);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = at;
            }
        }
        return best;
    }

    public @Nullable UUID airfieldOwner(ResourceKey<Level> dimension, BlockPos pos) {
        Map<Long, UUID> perDimension = airfields.get(dimension);
        return perDimension == null ? null : perDimension.get(pos.asLong());
    }

    private void decrement(UUID playerId) {
        Integer count = airfieldCounts.get(playerId);
        if (count == null) {
            return;
        }
        if (count <= 1) {
            airfieldCounts.remove(playerId);
        } else {
            airfieldCounts.put(playerId, count - 1);
        }
    }

    // ------------------------------------------------------------ persistence

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("DataVersion", DATA_VERSION);

        ListTag cityList = new ListTag();
        for (City city : cities.values()) {
            cityList.add(city.save());
        }
        tag.put("cities", cityList);

        ListTag structureList = new ListTag();
        for (Structure structure : structures.values()) {
            structureList.add(structure.save());
        }
        tag.put("structures", structureList);

        ListTag siegeList = new ListTag();
        for (Map.Entry<ResourceKey<Level>, Map<Long, Siege>> perDimension : sieges.entrySet()) {
            for (Map.Entry<Long, Siege> entry : perDimension.getValue().entrySet()) {
                CompoundTag siege = new CompoundTag();
                siege.putString("dimension", perDimension.getKey().location().toString());
                siege.putLong("chunk", entry.getKey());
                siege.putUUID("attacker", entry.getValue().attacker());
                siege.putInt("progress", entry.getValue().progress());
                siegeList.add(siege);
            }
        }
        tag.put("sieges", siegeList);

        ListTag optedOut = new ListTag();
        for (UUID playerId : creativeMoneyOff) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", playerId);
            optedOut.add(entry);
        }
        tag.put("creativeMoneyOff", optedOut);

        ListTag airfieldList = new ListTag();
        for (Map.Entry<ResourceKey<Level>, Map<Long, UUID>> perDimension : airfields.entrySet()) {
            for (Map.Entry<Long, UUID> entry : perDimension.getValue().entrySet()) {
                CompoundTag airfield = new CompoundTag();
                airfield.putString("dimension", perDimension.getKey().location().toString());
                airfield.putLong("pos", entry.getKey());
                airfield.putUUID("owner", entry.getValue());
                airfieldList.add(airfield);
            }
        }
        tag.put("airfields", airfieldList);
        return tag;
    }

    public static CityData load(CompoundTag tag, HolderLookup.Provider registries) {
        CityData data = new CityData();
        int version = tag.getInt("DataVersion");

        ListTag cityList = tag.getList("cities", Tag.TAG_COMPOUND);
        for (int i = 0; i < cityList.size(); i++) {
            // One unreadable city must not cost the player every other one. Vanilla swallows an
            // exception thrown out of here and quietly hands back a blank CityData, which would
            // read to the player as every city in the world having been deleted at once.
            City city;
            try {
                city = City.load(cityList.getCompound(i));
            } catch (RuntimeException failure) {
                CitiesInLife.LOGGER.error("Skipping an unreadable city entry", failure);
                continue;
            }
            data.cities.put(city.id(), city);
            Map<Long, UUID> index =
                    data.territoryIndex.computeIfAbsent(city.dimension(), key -> new HashMap<>());
            for (long chunkKey : city.claimedChunks()) {
                index.put(chunkKey, city.id());
            }
        }

        ListTag structureList = tag.getList("structures", Tag.TAG_COMPOUND);
        for (int i = 0; i < structureList.size(); i++) {
            Structure structure;
            try {
                structure = Structure.load(structureList.getCompound(i));
            } catch (RuntimeException failure) {
                CitiesInLife.LOGGER.error("Skipping an unreadable structure entry", failure);
                continue;
            }
            data.structures.put(structure.id(), structure);
            data.indexStructure(structure);
        }

        ListTag siegeList = tag.getList("sieges", Tag.TAG_COMPOUND);
        for (int i = 0; i < siegeList.size(); i++) {
            CompoundTag siege = siegeList.getCompound(i);
            ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION,
                    ResourceLocation.parse(siege.getString("dimension")));
            data.sieges.computeIfAbsent(dimension, key -> new HashMap<>()).put(
                    siege.getLong("chunk"),
                    new Siege(siege.getUUID("attacker"), siege.getInt("progress")));
        }

        ListTag optedOut = tag.getList("creativeMoneyOff", Tag.TAG_COMPOUND);
        for (int i = 0; i < optedOut.size(); i++) {
            data.creativeMoneyOff.add(optedOut.getCompound(i).getUUID("id"));
        }

        ListTag airfieldList = tag.getList("airfields", Tag.TAG_COMPOUND);
        for (int i = 0; i < airfieldList.size(); i++) {
            CompoundTag airfield = airfieldList.getCompound(i);
            ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION,
                    ResourceLocation.parse(airfield.getString("dimension")));
            UUID owner = airfield.getUUID("owner");
            data.airfields.computeIfAbsent(dimension, key -> new HashMap<>())
                    .put(airfield.getLong("pos"), owner);
            data.airfieldCounts.merge(owner, 1, Integer::sum);
        }

        data.migrate(version);
        return data;
    }

    /**
     * Hook for save-format changes.
     *
     * <p>Empty at version 1 and deliberately present anyway: the moment a format change is needed is
     * the moment it is too late to add the plumbing for it.
     */
    private void migrate(int fromVersion) {
        if (fromVersion < DATA_VERSION) {
            setDirty();
        }
    }
}
