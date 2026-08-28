package com.branciho.citiesinlife.block;

import com.branciho.citiesinlife.nuclear.ReactorReadout;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;

/**
 * The control-room screen.
 *
 * <p>The one place that shows temperature, pressure, fuel, output and what is currently going
 * wrong, all at once, with eighty seconds of trend behind each gauge. It is a convenience rather
 * than a component: the reactor runs perfectly well without one, and every other block in the
 * plant will answer the same question when clicked. Making the monitor a precondition would mean a
 * player could not find out what was missing until they had built the thing that tells them.
 */
public class MainMonitorBlock extends Block {

    public static final MapCodec<MainMonitorBlock> CODEC = simpleCodec(MainMonitorBlock::new);

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    /** A panel on the wall, thicker than a lever because it is a screen in a housing. */
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        SHAPES.put(Direction.NORTH, Block.box(1.0D, 1.0D, 10.0D, 15.0D, 15.0D, 16.0D));
        SHAPES.put(Direction.SOUTH, Block.box(1.0D, 1.0D, 0.0D, 15.0D, 15.0D, 6.0D));
        SHAPES.put(Direction.WEST, Block.box(10.0D, 1.0D, 1.0D, 16.0D, 15.0D, 15.0D));
        SHAPES.put(Direction.EAST, Block.box(0.0D, 1.0D, 1.0D, 6.0D, 15.0D, 15.0D));
    }

    public MainMonitorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext context) {
        return SHAPES.getOrDefault(state.getValue(FACING), SHAPES.get(Direction.NORTH));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            player.displayClientMessage(ReactorReadout.describe(level, pos), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
