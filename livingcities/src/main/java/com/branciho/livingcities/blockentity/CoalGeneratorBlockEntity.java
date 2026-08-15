package com.branciho.livingcities.blockentity;

import com.branciho.livingcities.block.CoalGeneratorBlock;
import com.branciho.livingcities.config.LivingCitiesConfig;
import com.branciho.livingcities.power.PowerGrid;
import com.branciho.livingcities.registry.ModBlockEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Burns fuel to generate electricity.
 *
 * <p>Stores a count of fuel rather than an ItemStack inventory: what matters to the simulation is how
 * long it can keep running, and a burn budget expresses that directly. Any item with a furnace burn
 * time is accepted, so charcoal and coal blocks work without a hardcoded list.
 */
public class CoalGeneratorBlockEntity extends BlockEntity {

    /** Cap on stored burn ticks, so a stack of coal blocks cannot buffer a week of runtime. */
    private static final int MAX_BURN_BUFFER = 20 * 60 * 30;

    private int burnTicksRemaining;

    public CoalGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.COAL_GENERATOR.get(), pos, state);
    }

    public int currentOutputKw() {
        return burnTicksRemaining > 0 ? LivingCitiesConfig.SERVER.coalGeneratorKw.get() : 0;
    }

    public void serverTick() {
        if (burnTicksRemaining <= 0) {
            return;
        }
        burnTicksRemaining--;
        if (burnTicksRemaining == 0) {
            setLit(false);
            // The grid's generation figure was computed while this was still burning.
            if (level instanceof ServerLevel serverLevel) {
                PowerGrid.get(serverLevel.getServer()).markDirty(serverLevel.dimension());
            }
        }
        setChanged();
    }

    /**
     * Accept one item of fuel from a player's hand.
     *
     * <p>One per click rather than the whole stack: feeding a generator should be a visible ongoing
     * cost, and swallowing a stack of coal blocks in a single click would hide it.
     */
    public boolean tryInsertFuel(ItemStack stack, Player player) {
        if (stack.isEmpty() || level == null) {
            return false;
        }
        // NeoForge's stack extension, so modded fuels work without a list of known items.
        int burnTime = stack.getBurnTime(null);
        if (burnTime <= 0) {
            return false;
        }
        if (burnTicksRemaining + burnTime > MAX_BURN_BUFFER) {
            player.displayClientMessage(Component.translatable("message.livingcities.generator_full")
                    .withStyle(ChatFormatting.GRAY), true);
            return false;
        }

        burnTicksRemaining += burnTime;
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }

        setLit(true);
        setChanged();
        if (level instanceof ServerLevel serverLevel) {
            PowerGrid.get(serverLevel.getServer()).markDirty(serverLevel.dimension());
        }
        reportStatus(player);
        return true;
    }

    public void reportStatus(Player player) {
        player.displayClientMessage(Component.translatable("message.livingcities.generator_status",
                currentOutputKw(), burnTicksRemaining / 20).withStyle(ChatFormatting.GRAY), true);
    }

    private void setLit(boolean lit) {
        if (level != null && getBlockState().hasProperty(CoalGeneratorBlock.LIT)
                && getBlockState().getValue(CoalGeneratorBlock.LIT) != lit) {
            level.setBlock(worldPosition, getBlockState().setValue(CoalGeneratorBlock.LIT, lit), 3);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.burnTicksRemaining = tag.getInt("BurnTicks");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("BurnTicks", burnTicksRemaining);
    }
}
