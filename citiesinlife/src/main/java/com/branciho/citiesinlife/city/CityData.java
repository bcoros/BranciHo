package com.branciho.citiesinlife.city;

import com.branciho.citiesinlife.CitiesInLife;
import com.branciho.citiesinlife.structure.Structure;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
     * the problem at 2.
     */
    private static final int DATA_VERSION = 3;

    private final Map<UUID, City> cities = new LinkedHashMap<>();
    private final Map<UUID, Structure> structures = new LinkedHashMap<>();

    /** dimension -> chunk key -> owning city. */
    private final Map<ResourceKey<Level>, Map<Long, UUID>> territoryIndex = new HashMap<>();

    /** dimension -> chunk key -> structures touching that chunk. */
    private final Map<ResourceKey<Level>, Map<Long, List<UUID>>> structureIndex = new HashMap<>();

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

        setDirty();
        return removed;
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
