package com.branciho.citiesinlife.blockentity;

import com.branciho.citiesinlife.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The fuel store: a hopper of uranium the core draws from.
 *
 * <p>Measured in units rather than items, where one uranium item is 100 units and 100 units is
 * exactly one fuel rod block's worth. That equivalence is the whole of the supply chain: a player
 * can count the blocks in their core, know the core holds that many uranium, and plan around it
 * without looking anything up.
 */
public class UraniumStorageBlockEntity extends BlockEntity {

    /** One uranium item. Also, not coincidentally, one fuel rod block filled to the brim. */
    public static final int UNITS_PER_ITEM = 100;

    /** Sixty-four items. Enough to fill the smallest legal core twice over. */
    public static final int CAPACITY = 64 * UNITS_PER_ITEM;

    /** How much the store can push into the rods in one simulation step. */
    public static final int TRANSFER_PER_STEP = 800;

    private int stored;

    public UraniumStorageBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.URANIUM_STORAGE.get(), pos, state);
    }

    public int stored() {
        return stored;
    }

    public float fraction() {
        return stored / (float) CAPACITY;
    }

    /** How many whole items would fit. Used to decide how much of a stack to swallow. */
    public int roomForItems() {
        return (CAPACITY - stored) / UNITS_PER_ITEM;
    }

    public void addItems(int items) {
        stored = Mth.clamp(stored + items * UNITS_PER_ITEM, 0, CAPACITY);
        setChanged();
    }

    /** Take up to this many units. Returns what was actually available. */
    public int draw(int units) {
        int taken = Math.min(units, stored);
        if (taken > 0) {
            stored -= taken;
            setChanged();
        }
        return taken;
    }

    public Component status() {
        return Component.translatable("message.citiesinlife.uranium_store",
                stored / UNITS_PER_ITEM, CAPACITY / UNITS_PER_ITEM);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        stored = Mth.clamp(tag.getInt("stored"), 0, CAPACITY);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("stored", stored);
    }
}
