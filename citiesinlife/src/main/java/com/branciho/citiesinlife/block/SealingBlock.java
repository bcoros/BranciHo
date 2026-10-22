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
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import com.branciho.citiesinlife.sound.MachineSounds;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * The lid on a rod column: a metal slab, and nothing else.
 *
 * <p>It has no behaviour of its own at all. Its entire job is to be found sitting on top of every
 * rod when the reactor looks, and to not be found when somebody has taken one off. That makes
 * sealing a thing you do to the core rather than a checkbox, and it makes an unsealed core
 * diagnosable by walking along the top of it and looking for the gap.
 *
 * <p>Waterlogged like the rods beneath it, because a core is filled with water to the brim and a dry
 * band across the top of it would look wrong.
 */
public class SealingBlock extends Block implements SimpleWaterloggedBlock {

    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    /**
     * Whether the core underneath it is over its limits.
     *
     * <p>Set by the reactor on every seal it owns, exactly the way the rods' own critical flag is.
     * The lid on a pressure vessel is the honest place for an overpressure to show, and it is the
     * only part of a core visible from outside the building - a reactor hall you cannot see into
     * now leaks steam through its roof when it is in trouble.
     */
    public static final BooleanProperty VENTING = BooleanProperty.create("venting");

    /**
     * How far the panel has slid back. 0 is shut, {@link #FULLY_OPEN} is out of the way.
     *
     * <p>This block started as a lid for a reactor rod column and had no behaviour at all beyond
     * being found or not found. It is now also the roof of a missile silo, and a silo roof has to
     * do the one thing a reactor lid never did: get out of the way.
     *
     * <p>Five steps rather than a boolean, and no block entity, because the animation is the
     * point. A blockstate the silo advances every few ticks gives a panel that visibly retracts,
     * and a core of thirty sealing blocks stays thirty block states rather than becoming thirty
     * ticking block entities to move a slab.
     */
    public static final IntegerProperty OPEN = IntegerProperty.create("open", 0, 4);

    public static final int FULLY_OPEN = 4;

    /** Half a block, sitting on the floor of its own space: a slab, as asked for. */
    /**
     * One shape per step, so an opening door stops being something you stand on.
     *
     * <p>The panel shortens along -Z, matching the models exactly. A shape that disagreed with the
     * model is the kind of bug where a rocket launches through a door that is visibly open and
     * hits something that is not there.
     *
     * <p>The last step is <b>nothing at all</b>, not a sliver. A panel the plate has finished
     * passing over is not a thinner panel, it is a panel that has gone somewhere else, and leaving
     * a one-pixel lip behind put a faint grid over the open half of every roof.
     */
    private static final VoxelShape[] SHAPES = {
            Block.box(0.0D, 0.0D, 0.0D, 16.0D, 8.0D, 16.0D),
            Block.box(0.0D, 0.0D, 0.0D, 16.0D, 8.0D, 12.0D),
            Block.box(0.0D, 0.0D, 0.0D, 16.0D, 8.0D, 8.0D),
            Block.box(0.0D, 0.0D, 0.0D, 16.0D, 8.0D, 4.0D),
            Shapes.empty()};

    public SealingBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(WATERLOGGED, false)
                .setValue(VENTING, false)
                .setValue(OPEN, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(WATERLOGGED, VENTING, OPEN);
    }

    /** How far this panel has slid back, 0 to {@link #FULLY_OPEN}. */
    public static int openness(BlockState state) {
        return state.hasProperty(OPEN) ? state.getValue(OPEN) : 0;
    }

    /** Whether the way out is clear. */
    public static boolean open(BlockState state) {
        return openness(state) >= FULLY_OPEN;
    }

    /** Whether this seal is venting, for anything that wants to react to that. */
    public static boolean venting(BlockState state) {
        return state.hasProperty(VENTING) && state.getValue(VENTING);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
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
        return SHAPES[Mth.clamp(openness(state), 0, FULLY_OPEN)];
    }

    /** Steam out of the seams, and the hiss that goes with it. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        super.animateTick(state, level, pos, random);
        if (venting(state)) {
            MachineSounds.venting(level, pos, random);
        }
    }
}
