package com.branciho.citiesinlife.block;

import com.branciho.citiesinlife.blockentity.EndPipeBlockEntity;
import com.branciho.citiesinlife.registry.ModBlockEntities;
import com.branciho.citiesinlife.water.WaterBlock;
import com.branciho.citiesinlife.water.WaterGrid;
import com.branciho.citiesinlife.water.WaterRole;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The tap on the end of a pipe run.
 *
 * <p>Everything else in the water system moves an abstract number around. This is the one block that
 * turns that number back into water you can swim in: point it somewhere and, as long as a pump is
 * still winning against the leaks, real water comes out of it. Stop the pumps and it stops, and the
 * water it made drains away.
 *
 * <p>It will also fill buckets. Link one to a chest, a barrel, a hopper or a coal boiler with the
 * Pipe Connect Tool and it keeps them topped up — which is what finally closes the loop on the
 * boiler, since a boiler hands back an empty bucket and this hands it straight back full.
 *
 * <p>Not to be confused with the End <em>Pump</em>, which is the seam where drawn links become real
 * pipe. This is the other end of the pipes entirely.
 */
public class EndPipeBlock extends BaseEntityBlock implements WaterBlock {

    public static final MapCodec<EndPipeBlock> CODEC = simpleCodec(EndPipeBlock::new);

    /** Which way the spout points. Water comes out of this side, and no pipe joins on it. */
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    private static final VoxelShape CORE = Block.box(4.0D, 4.0D, 4.0D, 12.0D, 12.0D, 12.0D);

    public EndPipeBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        // The spout points the way the player is looking, so it pours away from them rather than
        // over their feet.
        return defaultBlockState().setValue(FACING, context.getNearestLookingDirection());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return CORE;
    }

    // ------------------------------------------------------------------ water

    @Override
    public WaterRole waterRole() {
        return WaterRole.CONDUIT;
    }

    /**
     * Joins its neighbours like any other pipe, except on the side it pours out of.
     *
     * <p>Otherwise a tap aimed at a pipe would quietly plumb itself back into the run it is supposed
     * to be the end of.
     */
    @Override
    public boolean joinsAutomatically(BlockGetter level, BlockPos pos, BlockState state, Direction side) {
        return side != state.getValue(FACING);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EndPipeBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return createTickerHelper(type, ModBlockEntities.END_PIPE.get(), EndPipeBlockEntity::serverTick);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            // Take the water back with it, or breaking a tap leaves a spring behind forever.
            if (level.getBlockEntity(pos) instanceof EndPipeBlockEntity pipe) {
                pipe.stopPouring(level, state);
            }
            WaterGrid.get(serverLevel.getServer()).removeNode(level.dimension(), pos);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    /** Right click to be told whether it has water and what it is filling. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof EndPipeBlockEntity pipe) {
            player.displayClientMessage(pipe.report(), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
