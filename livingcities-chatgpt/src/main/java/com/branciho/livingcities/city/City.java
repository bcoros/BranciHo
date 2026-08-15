package com.branciho.livingcities.city;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.ChunkPos;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class City {
    private final UUID id;
    private final UUID owner;
    private final String name;
    private final String dimension;
    private final Set<Long> chunks = new HashSet<>();
    private double treasury;
    private int population;

    public City(UUID id, UUID owner, String name, String dimension, double treasury, int population) {
        this.id = id;
        this.owner = owner;
        this.name = name;
        this.dimension = dimension;
        this.treasury = treasury;
        this.population = population;
    }

    public UUID id() { return id; }
    public UUID owner() { return owner; }
    public String name() { return name; }
    public String dimension() { return dimension; }
    public Set<Long> chunks() { return chunks; }
    public double treasury() { return treasury; }
    public int population() { return population; }
    public void addMoney(double value) { treasury += value; }
    public boolean spend(double value) { if (treasury < value) return false; treasury -= value; return true; }
    public void setPopulation(int value) { population = Math.max(0, value); }
    public void claim(ChunkPos pos) { chunks.add(pos.toLong()); }
    public void unclaim(ChunkPos pos) { chunks.remove(pos.toLong()); }
    public boolean owns(ChunkPos pos) { return chunks.contains(pos.toLong()); }

    public boolean adjacentToClaim(ChunkPos pos) {
        return owns(new ChunkPos(pos.x + 1, pos.z)) || owns(new ChunkPos(pos.x - 1, pos.z))
                || owns(new ChunkPos(pos.x, pos.z + 1)) || owns(new ChunkPos(pos.x, pos.z - 1));
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putUUID("Owner", owner);
        tag.putString("Name", name);
        tag.putString("Dimension", dimension);
        tag.putDouble("Treasury", treasury);
        tag.putInt("Population", population);
        ListTag claims = new ListTag();
        for (long chunk : chunks) claims.add(LongTag.valueOf(chunk));
        tag.put("Chunks", claims);
        return tag;
    }

    public static City load(CompoundTag tag) {
        City city = new City(tag.getUUID("Id"), tag.getUUID("Owner"), tag.getString("Name"),
                tag.getString("Dimension"), tag.getDouble("Treasury"), tag.getInt("Population"));
        ListTag claims = tag.getList("Chunks", Tag.TAG_LONG);
        for (int i = 0; i < claims.size(); i++) city.chunks.add(((LongTag) claims.get(i)).getAsLong());
        return city;
    }
}
