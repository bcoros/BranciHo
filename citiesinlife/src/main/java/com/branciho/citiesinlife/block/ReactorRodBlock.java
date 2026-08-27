package com.branciho.citiesinlife.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * What a fuel rod and a control rod have in common: a thin column that has to stand in water.
 *
 * <p>Six by six, matching the core of a water pipe exactly. That was asked for and it turns out to
 * be the right answer for a second reason: a rod and a pipe read as parts of the same machine, which
 * is what a reactor hall actually looks like.
 *
 * <p>Waterloggable, and that is the whole of how "submerged" is implemented. Rather than inventing a
 * separate check for water around a rod - which would have to decide what counts as around, and
 * would disagree with itself at the corners - a rod is submerged exactly when it is waterlogged. You
 * build the rods, you pour water over them, they fill. Anything the player can see is the state the
 * reactor reads.
 *
 * <p>Deliberately not one block occupying several positions, the way the power mast is. A mast has a
 * fixed height the mod chose; a rod's height is the player's decision and the entire point of it, so
 * every block is placed by hand and the column is derived by walking.
 */
public abstract class ReactorRodBlock extends Block implements SimpleWaterloggedBlock {

    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    /** The shortest column that will run. Below this a rod is decoration. */
    public static final int MIN_HEIGHT = 8;

    /** Above this a column stops adding output, so a reactor cannot be scaled to the sky. */
    public static final int MAX_HEIGHT = 32;

    private static final VoxelShape SHAPE = Block.box(5.0D, 0.0D, 5.0D, 11.0D, 16.0D, 11.0D);

    protected ReactorRodBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(WATERLOGGED);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        // Placed straight into water, it comes up already flooded. Anything else and the player
        // pours water over the finished stack, which is how a real one is filled anyway.
        boolean inWater = context.getLevel()
                .getFluidState(context.getClickedPos()).getType() == Fluids.WATER;
        return defaultBlockState().setValue(WATERLOGGED, inWater);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED)
                ? Fluids.WATER.getSource(false)
                : super.getFluidState(state);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour,
                                     LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return super.updateShape(state, direction, neighbour, level, pos, neighbourPos);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext context) {
        return SHAPE;
    }

    /** Whether this rod is standing in water, which every rod in a live core must be. */
    public static boolean submerged(BlockState state) {
        return state.hasProperty(WATERLOGGED) && state.getValue(WATERLOGGED);
    }
}
