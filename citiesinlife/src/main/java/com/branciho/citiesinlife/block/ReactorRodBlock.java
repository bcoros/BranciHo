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
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import com.branciho.citiesinlife.sound.MachineSounds;
import net.minecraft.world.level.Level;

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

    /**
     * Whether the core this rod belongs to is past its limits.
     *
     * <p>Carried on the rods rather than held somewhere central because the rods are what a player
     * is standing next to, and the point of it is entirely local: a rod that knows the core is in
     * trouble can say so, loudly, to anybody in the building. The reactor sets it on every rod it
     * owns each simulation step, so it clears itself the moment the core comes back down.
     *
     * <p>Costs nothing when nothing is wrong. It changes twice in the life of a fault - once on
     * the way up and once on the way back - rather than every step.
     */
    public static final BooleanProperty CRITICAL = BooleanProperty.create("critical");

    /** The shortest column that will run. Below this a rod is decoration. */
    public static final int MIN_HEIGHT = 8;

    /** Above this a column stops adding output, so a reactor cannot be scaled to the sky. */
    public static final int MAX_HEIGHT = 32;

    private static final VoxelShape SHAPE = Block.box(5.0D, 0.0D, 5.0D, 11.0D, 16.0D, 11.0D);

    protected ReactorRodBlock(Properties properties) {
        super(properties);
        // Set here rather than left to whichever state the definition happens to build first, and
        // before the subclasses register their own defaults on top of this one.
        registerDefaultState(defaultBlockState().setValue(CRITICAL, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(WATERLOGGED, CRITICAL);
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

    /** Whether the core is past its limits, for anything that wants to react to that. */
    public static boolean critical(BlockState state) {
        return state.hasProperty(CRITICAL) && state.getValue(CRITICAL);
    }

    /** Whether this rod is standing in water, which every rod in a live core must be. */
    public static boolean submerged(BlockState state) {
        return state.hasProperty(WATERLOGGED) && state.getValue(WATERLOGGED);
    }

    /**
     * What a column sounds like.
     *
     * <p>Faint when everything is fine: a rod you have to be standing beside to hear, which is
     * what was asked for and is right for something that is working. Loud, fast and clicking when
     * it is not - and a core is dozens of rod blocks, every one of them saying it at once, so a
     * reactor in trouble is audible from outside the hall long before the alarm gets a look in.
     */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        super.animateTick(state, level, pos, random);
        boolean critical = critical(state);
        // A quiet rod is quiet enough that a whole core of them would still be a wall of sound
        // if every block spoke. Thinned out at rest; never thinned when it matters.
        if (!critical && random.nextInt(6) != 0) {
            return;
        }
        MachineSounds.rod(level, pos, random, critical);
    }
}
