package com.branciho.citiesinlife.work;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

/**
 * Where the jobs are.
 *
 * <p>A desk and a till are ordinary blocks that could be anywhere in the world, and there is no way
 * to ask a level for "every block entity of this type" without walking the chunks. So they put their
 * own position in here when they are placed and take it out again when they are broken, and the
 * citizen director reads this list instead of searching the ground.
 *
 * <p>Positions here are only a shortlist. Whether a desk is actually a job still depends on it
 * standing inside a registered office, which the director checks — this class deliberately does not
 * know what a city is.
 */
public final class WorkplaceRegistry extends SavedData {

    private static final String FILE_ID = "citiesinlife_workplaces";

    private final Map<ResourceKey<Level>, LongOpenHashSet> offices = new HashMap<>();
    private final Map<ResourceKey<Level>, LongOpenHashSet> registers = new HashMap<>();

    public static WorkplaceRegistry get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(WorkplaceRegistry::new, WorkplaceRegistry::load), FILE_ID);
    }

    public void addOffice(ResourceKey<Level> dimension, BlockPos pos) {
        if (offices.computeIfAbsent(dimension, key -> new LongOpenHashSet()).add(pos.asLong())) {
            setDirty();
        }
    }

    public void addRegister(ResourceKey<Level> dimension, BlockPos pos) {
        if (registers.computeIfAbsent(dimension, key -> new LongOpenHashSet()).add(pos.asLong())) {
            setDirty();
        }
    }

    public void remove(ResourceKey<Level> dimension, BlockPos pos) {
        boolean changed = drop(offices, dimension, pos) | drop(registers, dimension, pos);
        if (changed) {
            setDirty();
        }
    }

    private static boolean drop(Map<ResourceKey<Level>, LongOpenHashSet> from,
                                ResourceKey<Level> dimension, BlockPos pos) {
        LongOpenHashSet set = from.get(dimension);
        if (set == null || !set.remove(pos.asLong())) {
            return false;
        }
        if (set.isEmpty()) {
            from.remove(dimension);
        }
        return true;
    }

    public LongArrayList offices(ResourceKey<Level> dimension) {
        return copy(offices.get(dimension));
    }

    public LongArrayList registers(ResourceKey<Level> dimension) {
        return copy(registers.get(dimension));
    }

    private static LongArrayList copy(LongOpenHashSet set) {
        LongArrayList list = new LongArrayList();
        if (set != null) {
            list.addAll(set);
        }
        return list;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("offices", write(offices));
        tag.put("registers", write(registers));
        return tag;
    }

    private static ListTag write(Map<ResourceKey<Level>, LongOpenHashSet> from) {
        ListTag list = new ListTag();
        for (Map.Entry<ResourceKey<Level>, LongOpenHashSet> entry : from.entrySet()) {
            CompoundTag dimensionTag = new CompoundTag();
            dimensionTag.putString("dimension", entry.getKey().location().toString());
            dimensionTag.putLongArray("positions", entry.getValue().toLongArray());
            list.add(dimensionTag);
        }
        return list;
    }

    public static WorkplaceRegistry load(CompoundTag tag, HolderLookup.Provider registries) {
        WorkplaceRegistry registry = new WorkplaceRegistry();
        read(tag.getList("offices", Tag.TAG_COMPOUND), registry.offices);
        read(tag.getList("registers", Tag.TAG_COMPOUND), registry.registers);
        return registry;
    }

    private static void read(ListTag list, Map<ResourceKey<Level>, LongOpenHashSet> into) {
        for (int i = 0; i < list.size(); i++) {
            CompoundTag dimensionTag = list.getCompound(i);
            ResourceKey<Level> dimension = ResourceKey.create(
                    Registries.DIMENSION, ResourceLocation.parse(dimensionTag.getString("dimension")));
            LongOpenHashSet set = new LongOpenHashSet();
            for (long key : dimensionTag.getLongArray("positions")) {
                set.add(key);
            }
            into.put(dimension, set);
        }
    }
}
