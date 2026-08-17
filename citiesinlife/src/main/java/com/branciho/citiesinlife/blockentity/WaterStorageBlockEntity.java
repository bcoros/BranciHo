package com.branciho.citiesinlife.blockentity;

import com.branciho.citiesinlife.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What a tank is holding.
 *
 * <p>Nothing ticks here. The city simulation fills and drains every tank it can reach once per
 * growth step, which keeps the whole water system costing one walk per city rather than one tick per
 * tank — the same reason population is a number rather than a crowd of entities.
 */
public class WaterStorageBlockEntity extends BlockEntity {

    /** Units a tank holds. Roughly a minute of a full intake, so a shut valve takes a while to bite. */
    public static final int CAPACITY = 2000;

    private int stored;

    public WaterStorageBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WATER_STORAGE.get(), pos, state);
    }

    public int stored() {
        return stored;
    }

    public float fraction() {
        return stored / (float) CAPACITY;
    }

    /** Pour water in. Returns how much actually fitted. */
    public int fill(int amount) {
        int taken = Math.min(amount, CAPACITY - stored);
        if (taken > 0) {
            stored += taken;
            setChanged();
        }
        return taken;
    }

    /** Take water out. Returns how much was actually there. */
    public int drain(int amount) {
        int given = Math.min(amount, stored);
        if (given > 0) {
            stored -= given;
            setChanged();
        }
        return given;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        stored = Math.min(CAPACITY, Math.max(0, tag.getInt("stored")));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("stored", stored);
    }
}
