package com.branciho.citiesinlife.block;

import com.branciho.citiesinlife.blockentity.TurbineBlockEntity;
import com.branciho.citiesinlife.power.PowerBlock;
import com.branciho.citiesinlife.power.PowerGrid;
import com.branciho.citiesinlife.power.PowerRole;
import com.branciho.citiesinlife.registry.ModBlockEntities;
import com.branciho.citiesinlife.upgrade.Upgradeable;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import org.jetbrains.annotations.Nullable;

/**
 * The steam turbine: the other half of a coal plant.
 *
 * <p>It sits on the boiler's vent and turns the steam coming out of it into electricity, which the
 * power line tool then carries to a transit station like any other producer. On its own it does
 * nothing at all — there is no fuel slot and no switch. A turbine with no boiler underneath it is a
 * very large ornament, and that is the point: this is a plant you assemble, not a block you place.
 *
 * <p>Almost all of what you see is drawn by the block entity renderer, because the machine is three
 * blocks wide and the rotor turns. The block itself occupies one position.
 */
public class TurbineBlock extends BaseEntityBlock implements PowerBlock, Upgradeable {

    public static final MapCodec<TurbineBlock> CODEC = simpleCodec(TurbineBlock::new);

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public TurbineBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
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
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Face the player, so the rotor is side-on and you can actually see it turn.
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TurbineBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                           BlockEntityType<T> type) {
        if (level.isClientSide) {
            // The client ticks it only to advance the rotor's angle.
            return createTickerHelper(type, ModBlockEntities.TURBINE.get(), TurbineBlockEntity::clientTick);
        }
        return createTickerHelper(type, ModBlockEntities.TURBINE.get(), TurbineBlockEntity::serverTick);
    }

    @Override
    public PowerRole powerRole() {
        return PowerRole.PRODUCER;
    }

    @Override
    public int linkRange() {
        return 32;
    }

    @Override
    public int powerOutput(BlockGetter getter, BlockPos pos, BlockState state) {
        return getter.getBlockEntity(pos) instanceof TurbineBlockEntity turbine ? turbine.output() : 0;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            PowerGrid.get(serverLevel.getServer()).removeNode(level.dimension(), pos);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    // ------------------------------------------------------------- upgrading

    @Override
    public int maxTier() {
        return TurbineBlockEntity.MAX_TIER;
    }

    @Override
    public int tierAt(BlockGetter level, BlockPos pos, BlockState state) {
        return level.getBlockEntity(pos) instanceof TurbineBlockEntity turbine ? turbine.tier() : 0;
    }

    /**
     * Dearer than the pump or the sewer, because it is worth more.
     *
     * <p>A turbine at tier 1 gets half as much again out of coal that already cost something to
     * fetch, and out of wind that cost nothing at all. Pricing it the same as an intake would make
     * it the only upgrade anybody ever bought.
     */
    @Override
    public long upgradeCost(int fromTier) {
        return 2500L;
    }

    @Override
    public boolean upgrade(Level level, BlockPos pos, BlockState state) {
        return level.getBlockEntity(pos) instanceof TurbineBlockEntity turbine && turbine.upgrade();
    }

    @Override
    public Component describe(BlockGetter level, BlockPos pos, BlockState state) {
        int percent = level.getBlockEntity(pos) instanceof TurbineBlockEntity turbine
                ? Math.round(turbine.outputMultiplier() * 100.0F) : 100;
        return Component.translatable("message.citiesinlife.upgraded_turbine",
                tierAt(level, pos, state) + 1, percent);
    }
}
