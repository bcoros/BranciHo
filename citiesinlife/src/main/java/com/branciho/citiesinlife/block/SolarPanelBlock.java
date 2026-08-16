package com.branciho.citiesinlife.block;

import com.branciho.citiesinlife.power.PowerBlock;
import com.branciho.citiesinlife.power.PowerGrid;
import com.branciho.citiesinlife.power.PowerRole;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A solar panel: a pedestal with a tilted array on top.
 *
 * <p>Output depends on the sky actually being visible above it and on it being daytime, so putting a
 * solar farm underground or in permanent shade does what you would expect. Weather knocks it down
 * rather than out — an overcast day is not a blackout.
 */
public class SolarPanelBlock extends HorizontalDirectionalBlock implements PowerBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    /** Units in clear daylight with a clear view of the sky. */
    public static final int PEAK_OUTPUT = 8;

    private static final VoxelShape SHAPE = Block.box(1.0D, 0.0D, 1.0D, 15.0D, 15.0D, 15.0D);

    public SolarPanelBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public PowerRole powerRole() {
        return PowerRole.PRODUCER;
    }

    @Override
    public int linkRange() {
        return 24;
    }

    @Override
    public int powerOutput(BlockGetter getter, BlockPos pos, BlockState state) {
        if (!(getter instanceof Level level)) {
            return 0;
        }
        if (!level.canSeeSky(pos.above())) {
            return 0;
        }
        if (!level.isDay()) {
            return 0;
        }
        // Overcast weather reduces output rather than removing it; a rainy afternoon should dim the
        // city, not black it out.
        if (level.isRaining()) {
            return PEAK_OUTPUT / 3;
        }
        return PEAK_OUTPUT;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            // A line to a block that is gone would render into thin air and feed a phantom producer.
            PowerGrid.get(serverLevel.getServer()).removeNode(level.dimension(), pos);
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
