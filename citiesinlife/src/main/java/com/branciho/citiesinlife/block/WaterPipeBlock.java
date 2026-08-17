package com.branciho.citiesinlife.block;

import com.branciho.citiesinlife.water.WaterBlock;
import com.branciho.citiesinlife.water.WaterRole;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

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
 */
public class WaterPipeBlock extends Block implements WaterBlock {

    public static final BooleanProperty NORTH = BooleanProperty.create("north");
    public static final BooleanProperty EAST = BooleanProperty.create("east");
    public static final BooleanProperty SOUTH = BooleanProperty.create("south");
    public static final BooleanProperty WEST = BooleanProperty.create("west");
    public static final BooleanProperty UP = BooleanProperty.create("up");
    public static final BooleanProperty DOWN = BooleanProperty.create("down");

    private static final Map<Direction, BooleanProperty> BY_DIRECTION = new EnumMap<>(Direction.class);

    static {
        BY_DIRECTION.put(Direction.NORTH, NORTH);
        BY_DIRECTION.put(Direction.EAST, EAST);
        BY_DIRECTION.put(Direction.SOUTH, SOUTH);
        BY_DIRECTION.put(Direction.WEST, WEST);
        BY_DIRECTION.put(Direction.UP, UP);
        BY_DIRECTION.put(Direction.DOWN, DOWN);
    }

    public static BooleanProperty property(Direction direction) {
        return BY_DIRECTION.get(direction);
    }

    private static final VoxelShape CORE = Block.box(5.0D, 5.0D, 5.0D, 11.0D, 11.0D, 11.0D);
    private static final Map<Direction, VoxelShape> ARMS = new EnumMap<>(Direction.class);

    static {
        ARMS.put(Direction.DOWN, Block.box(5.0D, 0.0D, 5.0D, 11.0D, 5.0D, 11.0D));
        ARMS.put(Direction.UP, Block.box(5.0D, 11.0D, 5.0D, 11.0D, 16.0D, 11.0D));
        ARMS.put(Direction.NORTH, Block.box(5.0D, 5.0D, 0.0D, 11.0D, 11.0D, 5.0D));
        ARMS.put(Direction.SOUTH, Block.box(5.0D, 5.0D, 11.0D, 11.0D, 11.0D, 16.0D));
        ARMS.put(Direction.WEST, Block.box(0.0D, 5.0D, 5.0D, 5.0D, 11.0D, 11.0D));
        ARMS.put(Direction.EAST, Block.box(11.0D, 5.0D, 5.0D, 16.0D, 11.0D, 11.0D));
    }

    public WaterPipeBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(NORTH, false)
                .setValue(EAST, false)
                .setValue(SOUTH, false)
                .setValue(WEST, false)
                .setValue(UP, false)
                .setValue(DOWN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState();
        for (Direction direction : Direction.values()) {
            state = state.setValue(property(direction),
                    connectsTo(context.getLevel(), context.getClickedPos(), direction));
        }
        return state;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour,
                                     LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        return state.setValue(property(direction), connectsTo(level, pos, direction));
    }

    /**
     * Whether the thing on that side is something to join to.
     *
     * <p>Only the neighbour's opinion is asked here, not this pipe's own - a pipe always offers all
     * six faces. Asking both would be circular during placement, when neither state exists yet.
     */
    private static boolean connectsTo(BlockGetter level, BlockPos pos, Direction direction) {
        BlockPos neighbourPos = pos.relative(direction);
        BlockState neighbour = level.getBlockState(neighbourPos);
        return neighbour.getBlock() instanceof WaterBlock block
                && block.joinsAutomatically(level, neighbourPos, neighbour, direction.getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape shape = CORE;
        for (Direction direction : Direction.values()) {
            if (state.getValue(property(direction))) {
                shape = Shapes.or(shape, ARMS.get(direction));
            }
        }
        return shape;
    }

    @Override
    public WaterRole waterRole() {
        return WaterRole.CONDUIT;
    }

    @Override
    public boolean joinsAutomatically(BlockGetter level, BlockPos pos, BlockState state, Direction side) {
        return true;
    }
}
