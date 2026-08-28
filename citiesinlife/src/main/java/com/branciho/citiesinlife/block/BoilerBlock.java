package com.branciho.citiesinlife.block;

import com.branciho.citiesinlife.blockentity.BoilerBlockEntity;
import com.branciho.citiesinlife.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import com.branciho.citiesinlife.sound.MachineSounds;
import net.minecraft.util.RandomSource;

/**
 * The coal boiler: coal and water in, steam and smoke out.
 *
 * <p>Solar panels are free power you drop on the ground. This is the opposite, and deliberately so —
 * it burns a fuel you have to mine, it only works inside a chamber you have to build, and what comes
 * out of it is useless until you put a turbine on top of it. The reward for all that is a great deal
 * more power than a field of panels.
 *
 * <p>Everything that makes it work lives in {@link BoilerBlockEntity}; this is the block wrapper.
 */
public class BoilerBlock extends BaseEntityBlock {

    public static final MapCodec<BoilerBlock> CODEC = simpleCodec(BoilerBlock::new);

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    /** Whether the firebox is burning, so the front glows and it throws a little light. */
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public BoilerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(LIT, false));
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
        builder.add(FACING, LIT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BoilerBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                           BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return createTickerHelper(type, ModBlockEntities.BOILER.get(), BoilerBlockEntity::serverTick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof BoilerBlockEntity boiler) {
            serverPlayer.openMenu(boiler);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof BoilerBlockEntity boiler) {
                Containers.dropContents(level, pos, boiler);
            }
            super.onRemove(state, level, pos, newState, moved);
        }
    }

    /** A lit firebox. Silent the moment the coal runs out, which is the tell you want. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        super.animateTick(state, level, pos, random);
        if (state.hasProperty(LIT) && state.getValue(LIT)) {
            MachineSounds.boiler(level, pos, random);
        }
    }
}
