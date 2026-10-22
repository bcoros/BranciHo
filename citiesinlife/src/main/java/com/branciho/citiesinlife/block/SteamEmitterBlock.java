package com.branciho.citiesinlife.block;

import com.branciho.citiesinlife.blockentity.SteamEmitterBlockEntity;
import com.branciho.citiesinlife.nuclear.ReactorReadout;
import com.branciho.citiesinlife.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * A black block that lets the heat out.
 *
 * <p>Plain, on purpose — the owner asked for it plain until they decide how it should look. What it
 * does is not plain at all: without one, the reactor's four cooling ports foul until they latch,
 * and a latched loop is how a well-fuelled plant walks itself into a meltdown while nobody is
 * doing anything wrong.
 *
 * <p>Which makes this the one block in the plant whose absence is most easily missed, so the
 * monitor names it from the very first step the reactor runs rather than waiting for the first
 * port to jam.
 */
public class SteamEmitterBlock extends BaseEntityBlock {

    public static final MapCodec<SteamEmitterBlock> CODEC = simpleCodec(SteamEmitterBlock::new);

    public SteamEmitterBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SteamEmitterBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return createTickerHelper(type, ModBlockEntities.STEAM_EMITTER.get(),
                SteamEmitterBlockEntity::serverTick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            player.displayClientMessage(ReactorReadout.describe(level, pos), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
