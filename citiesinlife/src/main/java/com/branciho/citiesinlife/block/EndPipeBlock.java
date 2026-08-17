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
import net.minecraft.world.level.LevelAccessor;
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
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

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
 * boiler, since a boiler hands back nothing at all and this hands it a full one.
 *
 * <p>Not to be confused with the End <em>Pump</em>, which is the seam where drawn links become real
 * pipe. This is the other end of the pipes entirely.
 */
public class EndPipeBlock extends BaseEntityBlock implements WaterBlock {

    public static final MapCodec<EndPipeBlock> CODEC = simpleCodec(EndPipeBlock::new);

    /** Which way the spout points. Water comes out of this side, and no pipe joins on it. */
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    /**
     * Built to the ordinary pipe's measurements on purpose.
     *
     * <p>A six-wide core with six-wide arms is what every other conduit in the mod is, so a tap
     * bolted onto the end of a run lines up with it instead of bulging out of the joint.
     */
    private static final VoxelShape CORE = Block.box(5.0D, 5.0D, 5.0D, 11.0D, 11.0D, 11.0D);

    private static final Map<Direction, VoxelShape> ARMS = new EnumMap<>(Direction.class);
    private static final Map<Direction, VoxelShape> SPOUTS = new EnumMap<>(Direction.class);

    static {
        ARMS.put(Direction.DOWN, Block.box(5.0D, 0.0D, 5.0D, 11.0D, 5.0D, 11.0D));
        ARMS.put(Direction.UP, Block.box(5.0D, 11.0D, 5.0D, 11.0D, 16.0D, 11.0D));
        ARMS.put(Direction.NORTH, Block.box(5.0D, 5.0D, 0.0D, 11.0D, 11.0D, 5.0D));
        ARMS.put(Direction.SOUTH, Block.box(5.0D, 5.0D, 11.0D, 11.0D, 11.0D, 16.0D));
        ARMS.put(Direction.WEST, Block.box(0.0D, 5.0D, 5.0D, 5.0D, 11.0D, 11.0D));
        ARMS.put(Direction.EAST, Block.box(11.0D, 5.0D, 5.0D, 16.0D, 11.0D, 11.0D));

        // The nozzle is slimmer than an arm, so which end pours is obvious without reading a tooltip.
        SPOUTS.put(Direction.DOWN, Block.box(6.0D, 0.0D, 6.0D, 10.0D, 5.0D, 10.0D));
        SPOUTS.put(Direction.UP, Block.box(6.0D, 11.0D, 6.0D, 10.0D, 16.0D, 10.0D));
        SPOUTS.put(Direction.NORTH, Block.box(6.0D, 6.0D, 0.0D, 10.0D, 10.0D, 5.0D));
        SPOUTS.put(Direction.SOUTH, Block.box(6.0D, 6.0D, 11.0D, 10.0D, 10.0D, 16.0D));
        SPOUTS.put(Direction.WEST, Block.box(0.0D, 6.0D, 6.0D, 5.0D, 10.0D, 10.0D));
        SPOUTS.put(Direction.EAST, Block.box(11.0D, 6.0D, 6.0D, 16.0D, 10.0D, 10.0D));
    }

    public EndPipeBlock(Properties properties) {
        super(properties);
        BlockState state = stateDefinition.any().setValue(FACING, Direction.NORTH);
        for (Direction direction : Direction.values()) {
            state = state.setValue(WaterPipeBlock.property(direction), false);
        }
        registerDefaultState(state);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, WaterPipeBlock.NORTH, WaterPipeBlock.EAST, WaterPipeBlock.SOUTH,
                WaterPipeBlock.WEST, WaterPipeBlock.UP, WaterPipeBlock.DOWN);
    }

    /**
     * The spout points out of the face you clicked.
     *
     * <p>It used to point wherever the player happened to be looking, which put the nozzle in the
     * ground about as often as not. Clicking the end of a pipe run now does the obvious thing: the
     * back of the tap meets the pipe and the spout points away from it.
     */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getClickedFace();
        BlockState state = defaultBlockState().setValue(FACING, facing);
        for (Direction direction : Direction.values()) {
            state = state.setValue(WaterPipeBlock.property(direction),
                    direction != facing && connectsTo(context.getLevel(), context.getClickedPos(), direction));
        }
        return state;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour,
                                     LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        if (direction == state.getValue(FACING)) {
            return state;
        }
        return state.setValue(WaterPipeBlock.property(direction), connectsTo(level, pos, direction));
    }

    private static boolean connectsTo(BlockGetter level, BlockPos pos, Direction direction) {
        BlockPos neighbourPos = pos.relative(direction);
        BlockState neighbour = level.getBlockState(neighbourPos);
        return neighbour.getBlock() instanceof WaterBlock block
                && block.joinsAutomatically(level, neighbourPos, neighbour, direction.getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        BlockState rotated = state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            rotated = rotated.setValue(WaterPipeBlock.property(rotation.rotate(direction)),
                    state.getValue(WaterPipeBlock.property(direction)));
        }
        return rotated;
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
        VoxelShape shape = Shapes.or(CORE, SPOUTS.get(state.getValue(FACING)));
        for (Direction direction : Direction.values()) {
            if (direction != state.getValue(FACING) && state.getValue(WaterPipeBlock.property(direction))) {
                shape = Shapes.or(shape, ARMS.get(direction));
            }
        }
        return shape;
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
