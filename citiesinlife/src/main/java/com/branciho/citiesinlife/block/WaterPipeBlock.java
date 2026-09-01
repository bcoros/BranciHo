package com.branciho.citiesinlife.block;

import com.branciho.citiesinlife.water.WaterBlock;
import com.branciho.citiesinlife.water.WaterGrid;
import com.branciho.citiesinlife.water.WaterRole;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/**
 * A water pipe, which joins whatever it is touching without being told to.
 *
 * <p>This is the opposite decision from power on purpose. A power line is worth drawing by hand
 * because where it runs is the whole question; a pipe is not, because a pipe run through a city is
 * built the way you build anything else — by placing blocks next to each other and looking at it.
 * So pipes snap together like redstone, and the tool has no business between two of them.
 *
 * <p>Both ends must agree before water passes, which is what lets a valve shut one branch off
 * without the pipe pointed at it pretending otherwise.
 *
 * <p>Where there is no pipe to join but there is something solid, it puts a leg down instead. That
 * is cosmetic and it matters: the barrel of a pipe is six pixels across and centred in its block, so
 * without legs a run laid on the ground hovers above it and a run bolted under a roof hangs from
 * nothing at all.
 */
public class WaterPipeBlock extends Block implements WaterBlock {

    public static final EnumProperty<PipeJoin> NORTH = EnumProperty.create("north", PipeJoin.class);
    public static final EnumProperty<PipeJoin> EAST = EnumProperty.create("east", PipeJoin.class);
    public static final EnumProperty<PipeJoin> SOUTH = EnumProperty.create("south", PipeJoin.class);
    public static final EnumProperty<PipeJoin> WEST = EnumProperty.create("west", PipeJoin.class);
    public static final EnumProperty<PipeJoin> UP = EnumProperty.create("up", PipeJoin.class);
    public static final EnumProperty<PipeJoin> DOWN = EnumProperty.create("down", PipeJoin.class);

    /**
     * Whether this length has split.
     *
     * <p>A leak does not cut the run dead. It bleeds off part of what the network delivers and drips
     * where you can see it, so a city that suddenly has less water than it did is a thing you go and
     * look for rather than a thing that simply stopped.
     */
    public static final BooleanProperty LEAKING = BooleanProperty.create("leaking");

    /**
     * Units a split pipe wastes per step, and the odds of one splitting on a random tick.
     *
     * <p>Both deliberately mild. The first pass at this had a leak cost twelve units — most of a
     * starter pump's whole output — on odds that meant a network of any size sprang several a
     * minute. Leaks arrived faster than anyone could walk the line with a wrench, so an ordinary
     * pipe run drifted to permanently dry and the only winning move was to build the indestructible
     * ones and never think about it again. A leak is meant to be a job to notice, not a countdown.
     */
    public static final int LEAK_LOSS = 4;
    private static final int LEAK_ODDS = 48;

    private static final Map<Direction, EnumProperty<PipeJoin>> BY_DIRECTION =
            new EnumMap<>(Direction.class);

    static {
        BY_DIRECTION.put(Direction.NORTH, NORTH);
        BY_DIRECTION.put(Direction.EAST, EAST);
        BY_DIRECTION.put(Direction.SOUTH, SOUTH);
        BY_DIRECTION.put(Direction.WEST, WEST);
        BY_DIRECTION.put(Direction.UP, UP);
        BY_DIRECTION.put(Direction.DOWN, DOWN);
    }

    public static EnumProperty<PipeJoin> property(Direction direction) {
        return BY_DIRECTION.get(direction);
    }

    private static final VoxelShape CORE = Block.box(5.0D, 5.0D, 5.0D, 11.0D, 11.0D, 11.0D);
    private static final Map<Direction, VoxelShape> ARMS = new EnumMap<>(Direction.class);

    /**
     * The bracket a pipe puts down onto a solid neighbour.
     *
     * <p>Thinner than an arm and reaching the whole way to the face, because that is the difference
     * you are meant to read at a glance: an arm is the pipe continuing, a leg is the pipe being held
     * up by something that is not a pipe.
     */
    private static final Map<Direction, VoxelShape> LEGS = new EnumMap<>(Direction.class);

    static {
        ARMS.put(Direction.DOWN, Block.box(5.0D, 0.0D, 5.0D, 11.0D, 5.0D, 11.0D));
        ARMS.put(Direction.UP, Block.box(5.0D, 11.0D, 5.0D, 11.0D, 16.0D, 11.0D));
        ARMS.put(Direction.NORTH, Block.box(5.0D, 5.0D, 0.0D, 11.0D, 11.0D, 5.0D));
        ARMS.put(Direction.SOUTH, Block.box(5.0D, 5.0D, 11.0D, 11.0D, 11.0D, 16.0D));
        ARMS.put(Direction.WEST, Block.box(0.0D, 5.0D, 5.0D, 5.0D, 11.0D, 11.0D));
        ARMS.put(Direction.EAST, Block.box(11.0D, 5.0D, 5.0D, 16.0D, 11.0D, 11.0D));

        LEGS.put(Direction.DOWN, Block.box(6.0D, 0.0D, 6.0D, 10.0D, 5.0D, 10.0D));
        LEGS.put(Direction.UP, Block.box(6.0D, 11.0D, 6.0D, 10.0D, 16.0D, 10.0D));
        LEGS.put(Direction.NORTH, Block.box(6.0D, 6.0D, 0.0D, 10.0D, 10.0D, 5.0D));
        LEGS.put(Direction.SOUTH, Block.box(6.0D, 6.0D, 11.0D, 10.0D, 10.0D, 16.0D));
        LEGS.put(Direction.WEST, Block.box(0.0D, 6.0D, 6.0D, 5.0D, 10.0D, 10.0D));
        LEGS.put(Direction.EAST, Block.box(11.0D, 6.0D, 6.0D, 16.0D, 10.0D, 10.0D));
    }

    public WaterPipeBlock(Properties properties) {
        super(properties);
        BlockState state = stateDefinition.any().setValue(LEAKING, false);
        for (Direction direction : Direction.values()) {
            state = state.setValue(property(direction), PipeJoin.NONE);
        }
        registerDefaultState(state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN, LEAKING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState();
        for (Direction direction : Direction.values()) {
            state = state.setValue(property(direction),
                    joinFor(context.getLevel(), context.getClickedPos(), direction));
        }
        return state;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour,
                                     LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        return state.setValue(property(direction), joinFor(level, pos, direction));
    }

    /**
     * What the thing on that side is worth to this pipe.
     *
     * <p>Only the neighbour's opinion is asked about water, not this pipe's own - a pipe always
     * offers all six faces. Asking both would be circular during placement, when neither state
     * exists yet.
     *
     * <p>Anything else with a solid face gets a leg. A sturdy face is the right test rather than
     * "is a full block": it means there is something there for a bracket to actually be bolted to,
     * so a pipe braces against a slab's flat top and not against the open side of a fence.
     */
    public static PipeJoin joinFor(BlockGetter level, BlockPos pos, Direction direction) {
        BlockPos neighbourPos = pos.relative(direction);
        BlockState neighbour = level.getBlockState(neighbourPos);
        if (neighbour.getBlock() instanceof WaterBlock block
                && block.joinsAutomatically(level, neighbourPos, neighbour, direction.getOpposite())) {
            return PipeJoin.PIPE;
        }
        return neighbour.isFaceSturdy(level, neighbourPos, direction.getOpposite())
                ? PipeJoin.LEG
                : PipeJoin.NONE;
    }

    /** The shape of a pipe's core plus whatever it has grown on each side. */
    public static VoxelShape shapeOf(BlockState state, @Nullable Direction spout) {
        VoxelShape shape = CORE;
        for (Direction direction : Direction.values()) {
            if (direction == spout) {
                continue;
            }
            switch (state.getValue(property(direction))) {
                case PIPE -> shape = Shapes.or(shape, ARMS.get(direction));
                case LEG -> shape = Shapes.or(shape, LEGS.get(direction));
                case NONE -> {
                }
            }
        }
        return shape;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeOf(state, null);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            // A conduit is a legal end of a hand-drawn link, so breaking one has to take its links
            // with it. Without this the link outlives the block, cannot be cut - the cut gesture
            // needs a block to click and the server rejects a position that is no longer a water
            // block - and silently adopts whatever gets placed there next.
            WaterGrid.get(serverLevel.getServer()).removeNode(level.dimension(), pos);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    /** Whether this kind of pipe can split at all. The indestructible one says no. */
    public boolean canLeak() {
        return true;
    }

    /**
     * Pipes wear out.
     *
     * <p>Only a pipe that is actually part of a run can split - a single length lying in a chest
     * room has nothing going through it, and neither does one that is only propped against a wall -
     * and only slowly, because a water network you have to patrol constantly would be a chore rather
     * than a system.
     */
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        state = reseat(state, level, pos, null);
        if (!canLeak() || state.getValue(LEAKING)) {
            return;
        }
        int joints = 0;
        for (Direction direction : Direction.values()) {
            if (state.getValue(property(direction)).carriesWater()) {
                joints++;
            }
        }
        if (joints < 2 || random.nextInt(LEAK_ODDS) != 0) {
            return;
        }
        level.setBlock(pos, state.setValue(LEAKING, true), Block.UPDATE_ALL);
    }

    /**
     * Recompute what this pipe is touching and, if it has changed, say so.
     *
     * <p>Nothing sends a block update to a pipe when the world simply loads, so a pipe whose sides
     * were saved under an older rule set — before legs existed, when a side was a plain yes or no —
     * would come back with every side open and stay that way until somebody happened to place a
     * block beside it. This is the slow sweep that puts them right on their own.
     *
     * @return the state now in the world, which is the one the caller should go on using
     */
    public static BlockState reseat(BlockState state, ServerLevel level, BlockPos pos,
                                    @Nullable Direction spout) {
        BlockState wanted = state;
        for (Direction direction : Direction.values()) {
            if (direction == spout) {
                continue;
            }
            wanted = wanted.setValue(property(direction), joinFor(level, pos, direction));
        }
        if (wanted == state) {
            return state;
        }
        level.setBlock(pos, wanted, Block.UPDATE_ALL);
        return wanted;
    }

    /** A split pipe drips, which is the only way to find one in a long run. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LEAKING)) {
            return;
        }
        level.addParticle(ParticleTypes.DRIPPING_WATER,
                pos.getX() + 0.2D + random.nextDouble() * 0.6D,
                pos.getY() + 0.3D,
                pos.getZ() + 0.2D + random.nextDouble() * 0.6D,
                0.0D, 0.0D, 0.0D);
    }

    @Override
    public WaterRole waterRole() {
        return WaterRole.CONDUIT;
    }

    @Override
    public int waterLoss(BlockGetter level, BlockPos pos, BlockState state) {
        return state.getValue(LEAKING) ? LEAK_LOSS : 0;
    }

    @Override
    public boolean joinsAutomatically(BlockGetter level, BlockPos pos, BlockState state, Direction side) {
        return true;
    }
}
