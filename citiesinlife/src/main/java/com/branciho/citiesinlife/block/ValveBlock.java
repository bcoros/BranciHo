package com.branciho.citiesinlife.block;

import com.branciho.citiesinlife.water.WaterBlock;
import com.branciho.citiesinlife.water.WaterRole;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A valve: a length of pipe with a handle, and the only way to say no to water.
 *
 * <p>It lies along one axis and conducts along that axis only, so it is a piece of pipe with an
 * opinion rather than a junction. Shut it and water stops going that way, which is also how a branch
 * is chosen: put a valve on each leg of a split and close the ones you do not want fed.
 *
 * <p>Right click to work the handle. There is no menu and no redstone, because the whole point is
 * that you can see the state of the network by looking at it.
 */
public class ValveBlock extends Block implements WaterBlock {

    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;

    private static final VoxelShape SHAPE_X = Block.box(0.0D, 4.0D, 4.0D, 16.0D, 12.0D, 12.0D);
    private static final VoxelShape SHAPE_Y = Block.box(4.0D, 0.0D, 4.0D, 12.0D, 16.0D, 12.0D);
    private static final VoxelShape SHAPE_Z = Block.box(4.0D, 4.0D, 0.0D, 12.0D, 12.0D, 16.0D);

    public ValveBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(AXIS, Direction.Axis.Y)
                .setValue(OPEN, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, OPEN);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Along the face you clicked, the way a log lies.
        return defaultBlockState().setValue(AXIS, context.getClickedFace().getAxis());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        boolean nowOpen = !state.getValue(OPEN);
        level.setBlock(pos, state.setValue(OPEN, nowOpen), Block.UPDATE_ALL);
        level.playSound(null, pos,
                nowOpen ? SoundEvents.IRON_TRAPDOOR_OPEN : SoundEvents.IRON_TRAPDOOR_CLOSE,
                SoundSource.BLOCKS, 0.6F, nowOpen ? 1.1F : 0.9F);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(AXIS)) {
            case X -> SHAPE_X;
            case Z -> SHAPE_Z;
            default -> SHAPE_Y;
        };
    }

    @Override
    public WaterRole waterRole() {
        return WaterRole.CONDUIT;
    }

    @Override
    public boolean joinsAutomatically(BlockGetter level, BlockPos pos, BlockState state, Direction side) {
        return state.getValue(OPEN) && side.getAxis() == state.getValue(AXIS);
    }
}
