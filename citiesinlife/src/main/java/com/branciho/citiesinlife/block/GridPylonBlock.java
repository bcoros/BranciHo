package com.branciho.citiesinlife.block;

import com.branciho.citiesinlife.power.MastBlock;
import com.branciho.citiesinlife.power.PowerBlock;
import com.branciho.citiesinlife.power.PowerGrid;
import com.branciho.citiesinlife.power.PowerRole;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The steel lattice transmission pylon: seven blocks of tower and a quarter of a kilometre of reach.
 *
 * <p>The wooden mast is the thing that lines a village road. This is the thing that crosses the
 * countryside between them, and the difference is entirely one of scale: 250 blocks against 64, and
 * three levels of crossarm against one. A city that wants power from a station somewhere else
 * entirely now has a way to fetch it that does not involve a mast every sixty blocks.
 *
 * <p>Deliberately not a subclass of {@link PowerMastBlock}. The two share a shape of problem - one
 * node wearing several block positions - but every number differs, and the segment property has a
 * different range, which is not something a subclass can override. What they genuinely share is
 * {@link MastBlock}, so the wire renderer treats them alike.
 *
 * <p>The legs do not fill their block. You can walk underneath a pylon, which is what makes it read
 * as a tower standing in a field rather than a pillar sunk into one.
 */
public class GridPylonBlock extends Block implements PowerBlock, MastBlock {

    /** 0 is the foot, 6 is the earth-wire peak. */
    public static final IntegerProperty SEGMENT = IntegerProperty.create("segment", 0, 6);

    /** Which way the crossarms point. The tower is square; the arms are not. */
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public static final int HEIGHT = 7;

    /**
     * How far one pylon can throw a line to another.
     *
     * <p>250, as asked for. Worth knowing what that number costs: a link uses the shorter of its two
     * ends' ranges, so this only applies pylon to pylon. A pylon talking to a power station still
     * gets the station's own reach, which is what stops this becoming a way to wire a whole world
     * from one block.
     */
    public static final int PYLON_RANGE = 250;

    /** Where the legs sit at each level, in sixteenths from the block edge. Tapering inward. */
    private static final double[] LEG_INSET = {1.0D, 2.5D, 4.0D, 5.0D, 5.5D, 5.5D, 5.5D};

    private static final double LEG_THICKNESS = 2.0D;

    private static final VoxelShape[] SHAPES = buildShapes();

    public GridPylonBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(SEGMENT, 0)
                .setValue(FACING, Direction.NORTH));
    }

    /**
     * Four legs per level, and nothing else.
     *
     * <p>The crossarms are drawn but not collidable. A three-block-wide arm sticking out at head
     * height would be a wall you cannot see the far side of, and every player who built a line of
     * pylons along a road would find the road blocked.
     */
    private static VoxelShape[] buildShapes() {
        VoxelShape[] shapes = new VoxelShape[HEIGHT];
        for (int segment = 0; segment < HEIGHT; segment++) {
            double near = LEG_INSET[segment];
            double far = 16.0D - near - LEG_THICKNESS;
            VoxelShape shape = Shapes.empty();
            for (double x : new double[]{near, far}) {
                for (double z : new double[]{near, far}) {
                    shape = Shapes.or(shape, Block.box(
                            x, 0.0D, z, x + LEG_THICKNESS, 16.0D, z + LEG_THICKNESS));
                }
            }
            shapes[segment] = shape;
        }
        return shapes;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SEGMENT, FACING);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        // Seven blocks is a lot to ask for, so refuse cleanly rather than leaving a stump. The item
        // stays in the player's hand, which is the same thing the wooden mast does.
        for (int offset = 1; offset < HEIGHT; offset++) {
            if (!level.getBlockState(pos.above(offset)).canBeReplaced()) {
                return null;
            }
        }
        if (pos.getY() + HEIGHT > level.getMaxBuildHeight()) {
            return null;
        }
        return defaultBlockState()
                .setValue(SEGMENT, 0)
                .setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            @Nullable LivingEntity placer, ItemStack stack) {
        for (int offset = 1; offset < HEIGHT; offset++) {
            level.setBlock(pos.above(offset), state.setValue(SEGMENT, offset), Block.UPDATE_ALL);
        }
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        int segment = state.getValue(SEGMENT);
        if (segment == 0) {
            return true;
        }
        BlockState below = level.getBlockState(pos.below());
        return below.getBlock() instanceof GridPylonBlock && below.getValue(SEGMENT) == segment - 1;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour,
                                     LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        if (!canSurvive(state, level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return state;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        // Break any part and the whole tower comes down. Seven segments of floating lattice with no
        // obvious way to reach the top would be worse than no pylon at all.
        BlockPos base = baseOf(level, pos, state);
        for (int offset = 0; offset < HEIGHT; offset++) {
            BlockPos segmentPos = base.above(offset);
            BlockState segmentState = level.getBlockState(segmentPos);
            if (segmentState.getBlock() instanceof GridPylonBlock && !segmentPos.equals(pos)) {
                level.setBlock(segmentPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState,
                            boolean moved) {
        if (!state.is(newState.getBlock()) && state.getValue(SEGMENT) == 0
                && level instanceof ServerLevel serverLevel) {
            PowerGrid.get(serverLevel.getServer()).removeNode(level.dimension(), pos);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext context) {
        return SHAPES[state.getValue(SEGMENT)];
    }

    @Override
    public PowerRole powerRole() {
        return PowerRole.RELAY;
    }

    @Override
    public int linkRange() {
        return PYLON_RANGE;
    }

    @Override
    public int mastHeight() {
        return HEIGHT;
    }

    @Override
    public BlockPos networkPos(BlockGetter level, BlockPos pos, BlockState state) {
        return baseOf(level, pos, state);
    }

    /** The foot of the pylon this position belongs to. */
    private static BlockPos baseOf(BlockGetter level, BlockPos pos, BlockState state) {
        int segment = state.getBlock() instanceof GridPylonBlock ? state.getValue(SEGMENT) : 0;
        return pos.below(segment);
    }
}
