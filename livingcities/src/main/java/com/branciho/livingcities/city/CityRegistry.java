package com.branciho.livingcities.city;

import com.branciho.livingcities.LivingCities;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
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

    /** dimension -> (chunk key -> owning city id). Rebuilt from the cities on load. */
    private final Map<ResourceKey<Level>, Long2ObjectMap<UUID>> chunkIndex = new HashMap<>();

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
        registry.rebuildChunkIndex();
        LivingCities.LOGGER.info("Loaded {} cities (schema v{})", registry.cities.size(), version);
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
