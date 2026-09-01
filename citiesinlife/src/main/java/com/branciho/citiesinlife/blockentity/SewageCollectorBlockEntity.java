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
 * The sewage collector's insides: how big it is, and whether it currently has anywhere to put
 * anything.
 *
 * <p>It holds no buffer, and that is the difference between this and a water tank. A tank exists so
 * that losing your supply shows up as a slow decline rather than a cliff. A sewer has the opposite
 * job: the moment the outfall is blocked, the city should notice, because a sewer that quietly
 * stored a week of sewage would be a problem you only discover when it is a week deep.
 */
public class SewageCollectorBlockEntity extends BlockEntity {

    /** Sewage handled per simulation step at tier 0. */
    public static final int BASE_CAPACITY = 40;

    /** Each level adds this much again on top of the base. Tiers 0-3 give 40, 70, 100, 130. */
    public static final int CAPACITY_PER_TIER = 30;

    public static final int MAX_TIER = 3;

    private int tier;

    /**
     * What the last simulation step actually put through it.
     *
     * <p>Kept only so the block can answer when clicked. It is not saved: a number describing the
     * last two seconds has nothing useful to say after a reload.
     */
    private int throughput;

    /** Whether a discharge point outside the city was reachable last time anyone looked. */
    private boolean connected;

    public SewageCollectorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SEWAGE_COLLECTOR.get(), pos, state);
    }

    public int tier() {
        return tier;
    }

    public int capacity() {
        return BASE_CAPACITY + tier * CAPACITY_PER_TIER;
    }

    /** Raise the tier by one. Returns false at the ceiling. */
    public boolean upgrade() {
        if (tier >= MAX_TIER) {
            return false;
        }
        tier++;
        setChanged();
        return true;
    }

    /** Told by the city simulation what it managed this step. */
    public void report(int throughput, boolean connected) {
        this.throughput = throughput;
        this.connected = connected;
    }

    public boolean connected() {
        return connected;
    }

    /** One line: how big it is, how much it is shifting, and whether it has an outfall. */
    public Component status() {
        if (!connected) {
            return Component.translatable("message.citiesinlife.sewage_blocked", capacity());
        }
        return Component.translatable("message.citiesinlife.sewage_working",
                throughput, capacity(), tier + 1);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        tier = Mth.clamp(tag.getInt("tier"), 0, MAX_TIER);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("tier", tier);
    }
}
