package com.branciho.citiesinlife.nuclear;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Every reactor's accumulated condition, keyed by the structure it belongs to.
 *
 * <p>Keyed by structure UUID rather than by position, so moving nothing and renaming nothing can
 * separate a reactor from its own temperature. Deleting the structure drops the row, which is the
 * behaviour you want: a plant that was demolished and rebuilt starts cold.
 */
public final class ReactorData extends SavedData {

    private static final String FILE_ID = "citiesinlife_reactors";

    private final Map<UUID, ReactorState> reactors = new HashMap<>();

    public static ReactorData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(ReactorData::new, ReactorData::load), FILE_ID);
    }

    /** The reactor's condition, created cold if this is the first time anyone has asked. */
    public ReactorState of(UUID structureId) {
        return reactors.computeIfAbsent(structureId, id -> {
            setDirty();
            return new ReactorState();
        });
    }

    /** Whether this reactor has any history at all, without conjuring a row for it. */
    public boolean known(UUID structureId) {
        return reactors.containsKey(structureId);
    }

    public void forget(UUID structureId) {
        if (reactors.remove(structureId) != null) {
            setDirty();
        }
    }

    /**
     * Drop every row whose plant no longer exists.
     *
     * <p>A melting reactor is never swept, even if its structure has gone: {@link Meltdown} owns
     * those rows and ends them itself, and taking one away here would be one more way to cancel a
     * meltdown by accident.
     */
    public void forgetOrphans(Set<UUID> live) {
        if (reactors.entrySet().removeIf(
                entry -> !live.contains(entry.getKey()) && !entry.getValue().melting())) {
            setDirty();
        }
    }

    /** Every reactor currently melting down, so the meltdown loop does not walk the whole map. */
    public Map<UUID, ReactorState> all() {
        return reactors;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, ReactorState> entry : reactors.entrySet()) {
            CompoundTag row = entry.getValue().save();
            row.putUUID("structure", entry.getKey());
            list.add(row);
        }
        tag.put("reactors", list);
        return tag;
    }

    private static ReactorData load(CompoundTag tag, HolderLookup.Provider registries) {
        ReactorData data = new ReactorData();
        ListTag list = tag.getList("reactors", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag row = list.getCompound(i);
            if (row.hasUUID("structure")) {
                data.reactors.put(row.getUUID("structure"), ReactorState.load(row));
            }
        }
        return data;
    }
}
