package com.branciho.citiesinlife.block;

import com.branciho.citiesinlife.blockentity.WaterStorageBlockEntity;
import com.branciho.citiesinlife.water.WaterBlock;
import com.branciho.citiesinlife.water.WaterGrid;
import com.branciho.citiesinlife.water.WaterRole;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * A water tank.
 *
 * <p>This is where the network stops being plumbing and becomes something the city can drink. Only
 * tanks standing on ground the city owns count, which is the same rule that ties power to territory:
 * without it a player could run a pipe to anywhere at all and water a city they have no claim over.
 *
 * <p>It holds a buffer rather than passing water straight through, so a shut valve or a dry river
 * shows up as a tank slowly emptying instead of a city that goes thirsty the same tick.
 */
public class WaterStorageBlock extends BaseEntityBlock implements WaterBlock {

    public static final MapCodec<WaterStorageBlock> CODEC = simpleCodec(WaterStorageBlock::new);

    public WaterStorageBlock(Properties properties) {
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
        return new WaterStorageBlockEntity(pos, state);
    }

    /**
     * Right click to read the gauge.
     *
     * <p>A tank with no screen is a tank you cannot diagnose, and the number that matters is one
     * line long, so it goes in the action bar rather than into a menu.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof WaterStorageBlockEntity tank) {
            player.displayClientMessage(Component.translatable(
                    "message.citiesinlife.tank_level", tank.stored(),
                    WaterStorageBlockEntity.CAPACITY), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            WaterGrid.get(serverLevel.getServer()).removeNode(level.dimension(), pos);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof WaterStorageBlockEntity tank) {
            return Math.round(tank.fraction() * 15.0F);
        }
        return 0;
    }

    @Override
    public WaterRole waterRole() {
        return WaterRole.STORAGE;
    }

    @Override
    public int linkRange() {
        return 24;
    }
}
