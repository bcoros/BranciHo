package com.branciho.livingcities.city;

import com.branciho.livingcities.LivingCities;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CityData extends SavedData {
    private static final String DATA_NAME = LivingCities.MOD_ID + "_alpha1";
    private final Map<UUID, City> cities = new HashMap<>();
    private final Map<UUID, Building> buildings = new HashMap<>();

    public static final SavedData.Factory<CityData> FACTORY =
            new SavedData.Factory<>(CityData::new, CityData::load, null);

    public static CityData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public Collection<City> cities() { return cities.values(); }
    public Collection<Building> buildings() { return buildings.values(); }

    public @Nullable City cityOwnedBy(UUID owner) {
        for (City city : cities.values()) if (city.owner().equals(owner)) return city;
        return null;
    }

    public @Nullable City cityAt(String dimension, ChunkPos pos) {
        for (City city : cities.values()) if (city.dimension().equals(dimension) && city.owns(pos)) return city;
        return null;
    }

    public List<Building> buildingsOf(UUID cityId) {
        List<Building> result = new ArrayList<>();
        for (Building building : buildings.values()) if (building.cityId().equals(cityId)) result.add(building);
        return result;
    }

    public void addCity(City city) { cities.put(city.id(), city); setDirty(); }
    public void addBuilding(Building building) { buildings.put(building.id(), building); setDirty(); }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag cityList = new ListTag();
        for (City city : cities.values()) cityList.add(city.save());
        tag.put("Cities", cityList);
        ListTag buildingList = new ListTag();
        for (Building building : buildings.values()) buildingList.add(building.save());
        tag.put("Buildings", buildingList);
        return tag;
    }

    private static CityData load(CompoundTag tag, HolderLookup.Provider registries) {
        CityData data = new CityData();
        ListTag cityList = tag.getList("Cities", Tag.TAG_COMPOUND);
        for (int i = 0; i < cityList.size(); i++) {
            City city = City.load(cityList.getCompound(i));
            data.cities.put(city.id(), city);
        }
        ListTag buildingList = tag.getList("Buildings", Tag.TAG_COMPOUND);
        for (int i = 0; i < buildingList.size(); i++) {
            Building building = Building.load(buildingList.getCompound(i));
            data.buildings.put(building.id(), building);
        }
        return data;
    }
}
