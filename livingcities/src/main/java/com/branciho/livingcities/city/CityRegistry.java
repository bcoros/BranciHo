package com.branciho.livingcities.city;

import com.branciho.livingcities.LivingCities;
import com.branciho.livingcities.building.Building;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The single server-global home for all city data.
 *
 * <p>Deliberately one {@link SavedData} attached to the overworld rather than per-chunk attachments:
 * a city is not a chunk-local concept, its territory spans chunks that are mostly unloaded, and the
 * simulation has to run whether or not anyone is standing in it. Attaching to the overworld's storage
 * gives one authoritative copy that loads with the server and saves with it.
 *
 * <p>The chunk index is <em>derived</em> state, rebuilt on load, so the save file never has to keep
 * two representations of ownership in sync.
 */
public final class CityRegistry extends SavedData {

    private static final String DATA_NAME = LivingCities.MOD_ID + "_cities";

    /** Bumped when the on-disk schema changes; {@link #migrate} decides what to do about it. */
    private static final int CURRENT_DATA_VERSION = 1;

    private final Map<UUID, City> cities = new HashMap<>();

    private final Map<UUID, Building> buildings = new HashMap<>();

    /** dimension -> (chunk key -> owning city id). Rebuilt from the cities on load. */
    private final Map<ResourceKey<Level>, Long2ObjectMap<UUID>> chunkIndex = new HashMap<>();

    /**
     * chunk key -> buildings whose footprint touches that chunk. Rebuilt on load.
     *
     * <p>Answering "which building is this position inside?" by scanning every building would be
     * O(buildings) on a hot path. Bucketing by chunk makes it O(buildings in one chunk), which is a
     * handful even in a dense downtown.
     */
    private final Map<ResourceKey<Level>, Long2ObjectMap<List<UUID>>> buildingChunkIndex = new HashMap<>();

    public static final SavedData.Factory<CityRegistry> FACTORY =
            new SavedData.Factory<>(CityRegistry::new, CityRegistry::load, null);

    public CityRegistry() {
    }

    /** The registry for this server. Always call on the server thread. */
    public static CityRegistry get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    // ------------------------------------------------------------------ queries

    public Collection<City> cities() {
        return cities.values();
    }

    public @Nullable City byId(UUID id) {
        return cities.get(id);
    }

    public @Nullable City byChunk(ResourceKey<Level> dimension, ChunkPos pos) {
        Long2ObjectMap<UUID> index = chunkIndex.get(dimension);
        if (index == null) {
            return null;
        }
        UUID cityId = index.get(pos.toLong());
        return cityId == null ? null : cities.get(cityId);
    }

    /** Cities this player belongs to, most privileged first. */
    public List<City> citiesOf(UUID player) {
        List<City> result = new ArrayList<>();
        for (City city : cities.values()) {
            if (city.roleOf(player) != null) {
                result.add(city);
            }
        }
        result.sort((a, b) -> Integer.compare(b.roleOf(player).rank(), a.roleOf(player).rank()));
        return result;
    }

    public boolean nameTaken(String name) {
        for (City city : cities.values()) {
            if (city.name().equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ mutation

    public void addCity(City city) {
        cities.put(city.id(), city);
        Long2ObjectMap<UUID> index = chunkIndex.computeIfAbsent(city.dimension(), key -> new Long2ObjectOpenHashMap<>());
        for (long chunkKey : city.claimedChunks()) {
            index.put(chunkKey, city.id());
        }
        setDirty();
    }

    public void removeCity(UUID cityId) {
        City removed = cities.remove(cityId);
        if (removed != null) {
            Long2ObjectMap<UUID> index = chunkIndex.get(removed.dimension());
            if (index != null) {
                for (long chunkKey : removed.claimedChunks()) {
                    index.remove(chunkKey);
                }
            }
            setDirty();
        }
    }

    /** Claim a chunk for a city. Returns false if some other city already owns it. */
    public boolean claim(City city, ChunkPos pos) {
        Long2ObjectMap<UUID> index = chunkIndex.computeIfAbsent(city.dimension(), key -> new Long2ObjectOpenHashMap<>());
        UUID existing = index.get(pos.toLong());
        if (existing != null && !existing.equals(city.id())) {
            return false;
        }
        city.addClaim(pos);
        index.put(pos.toLong(), city.id());
        setDirty();
        return true;
    }

    public void unclaim(City city, ChunkPos pos) {
        city.removeClaim(pos);
        Long2ObjectMap<UUID> index = chunkIndex.get(city.dimension());
        if (index != null) {
            index.remove(pos.toLong());
        }
        setDirty();
    }

    // ------------------------------------------------------------------ buildings

    public Collection<Building> buildings() {
        return buildings.values();
    }

    public @Nullable Building building(UUID buildingId) {
        return buildings.get(buildingId);
    }

    public List<Building> buildingsOf(City city) {
        List<Building> result = new ArrayList<>(city.buildingIds().size());
        for (UUID buildingId : city.buildingIds()) {
            Building building = buildings.get(buildingId);
            if (building != null) {
                result.add(building);
            }
        }
        return result;
    }

    public void addBuilding(City city, Building building) {
        buildings.put(building.id(), building);
        city.buildingIds().add(building.id());
        indexBuilding(city.dimension(), building);
        setDirty();
    }

    public void removeBuilding(UUID buildingId) {
        Building removed = buildings.remove(buildingId);
        if (removed == null) {
            return;
        }
        City city = cities.get(removed.cityId());
        if (city != null) {
            city.buildingIds().remove(buildingId);
            Long2ObjectMap<List<UUID>> index = buildingChunkIndex.get(city.dimension());
            if (index != null) {
                for (ChunkPos chunk : removed.occupiedChunks()) {
                    List<UUID> bucket = index.get(chunk.toLong());
                    if (bucket != null) {
                        bucket.remove(buildingId);
                    }
                }
            }
        }
        setDirty();
    }

    /** The building containing this position, if any. */
    public @Nullable Building buildingAt(ResourceKey<Level> dimension, BlockPos pos) {
        for (Building building : buildingsInChunk(dimension, new ChunkPos(pos))) {
            if (building.contains(pos)) {
                return building;
            }
        }
        return null;
    }

    public List<Building> buildingsInChunk(ResourceKey<Level> dimension, ChunkPos chunk) {
        Long2ObjectMap<List<UUID>> index = buildingChunkIndex.get(dimension);
        if (index == null) {
            return List.of();
        }
        List<UUID> bucket = index.get(chunk.toLong());
        if (bucket == null || bucket.isEmpty()) {
            return List.of();
        }
        List<Building> result = new ArrayList<>(bucket.size());
        for (UUID buildingId : bucket) {
            Building building = buildings.get(buildingId);
            if (building != null) {
                result.add(building);
            }
        }
        return result;
    }

    /** Any registered building overlapping these bounds, so two builds cannot claim the same blocks. */
    public @Nullable Building findOverlap(ResourceKey<Level> dimension, Building candidate) {
        for (ChunkPos chunk : candidate.occupiedChunks()) {
            for (Building existing : buildingsInChunk(dimension, chunk)) {
                if (!existing.id().equals(candidate.id()) && existing.intersects(candidate)) {
                    return existing;
                }
            }
        }
        return null;
    }

    /** Mark every building overlapping a changed position as needing a rescan. */
    public void markDirtyAt(ResourceKey<Level> dimension, BlockPos pos) {
        for (Building building : buildingsInChunk(dimension, new ChunkPos(pos))) {
            if (building.contains(pos)) {
                building.markDirty();
                setDirty();
            }
        }
    }

    private void indexBuilding(ResourceKey<Level> dimension, Building building) {
        Long2ObjectMap<List<UUID>> index =
                buildingChunkIndex.computeIfAbsent(dimension, key -> new Long2ObjectOpenHashMap<>());
        for (ChunkPos chunk : building.occupiedChunks()) {
            index.computeIfAbsent(chunk.toLong(), key -> new ArrayList<>()).add(building.id());
        }
    }

    private void rebuildBuildingIndex() {
        buildingChunkIndex.clear();
        for (Building building : buildings.values()) {
            City city = cities.get(building.cityId());
            if (city != null) {
                indexBuilding(city.dimension(), building);
            }
        }
    }

    private void rebuildChunkIndex() {
        chunkIndex.clear();
        for (City city : cities.values()) {
            Long2ObjectMap<UUID> index = chunkIndex.computeIfAbsent(city.dimension(), key -> new Long2ObjectOpenHashMap<>());
            for (long chunkKey : city.claimedChunks()) {
                index.put(chunkKey, city.id());
            }
        }
    }

    // ------------------------------------------------------------------ persistence

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("DataVersion", CURRENT_DATA_VERSION);
        ListTag list = new ListTag();
        for (City city : cities.values()) {
            list.add(city.save());
        }
        tag.put("Cities", list);

        ListTag buildingList = new ListTag();
        for (Building building : buildings.values()) {
            buildingList.add(building.save());
        }
        tag.put("Buildings", buildingList);
        return tag;
    }

    private static CityRegistry load(CompoundTag tag, HolderLookup.Provider registries) {
        CityRegistry registry = new CityRegistry();
        int version = tag.contains("DataVersion") ? tag.getInt("DataVersion") : 0;
        migrate(tag, version);

        ListTag list = tag.getList("Cities", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            try {
                City city = City.load(list.getCompound(i));
                registry.cities.put(city.id(), city);
            } catch (RuntimeException e) {
                // One corrupt city must not take the whole server's city data with it.
                LivingCities.LOGGER.error("Skipping unreadable city at index {}", i, e);
            }
        }
        ListTag buildingList = tag.getList("Buildings", Tag.TAG_COMPOUND);
        for (int i = 0; i < buildingList.size(); i++) {
            try {
                Building building = Building.load(buildingList.getCompound(i));
                registry.buildings.put(building.id(), building);
            } catch (RuntimeException e) {
                LivingCities.LOGGER.error("Skipping unreadable building at index {}", i, e);
            }
        }

        registry.rebuildChunkIndex();
        registry.rebuildBuildingIndex();
        LivingCities.LOGGER.info("Loaded {} cities and {} buildings (schema v{})",
                registry.cities.size(), registry.buildings.size(), version);
        return registry;
    }

    /**
     * Upgrade an older save in place. There is nothing to do yet, but the hook and the version field
     * exist from day one so that the first schema change does not require guessing what old data meant.
     */
    private static void migrate(CompoundTag tag, int fromVersion) {
        if (fromVersion < CURRENT_DATA_VERSION) {
            LivingCities.LOGGER.info("Migrating Living Cities data from schema v{} to v{}", fromVersion, CURRENT_DATA_VERSION);
        }
    }
}
