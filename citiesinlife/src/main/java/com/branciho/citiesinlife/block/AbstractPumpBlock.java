package com.branciho.citiesinlife.block;

import com.branciho.citiesinlife.water.WaterGrid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import com.branciho.citiesinlife.water.WaterBlock;

/**
 * What the three pumps have in common: a facing, and links that die with the block.
 *
 * <p>They are otherwise separate blocks rather than one block with a mode, because which pump you are
 * holding should be obvious from the item in your hand rather than from a state you have to check.
 */
public abstract class AbstractPumpBlock extends Block implements WaterBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    protected AbstractPumpBlock(Properties properties) {
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

    /**
     * Right click a pump to ask it how it is doing.
     *
     * <p>Only the starter pump has anything to say, but it has something important to say: a pump
     * that cannot reach water is completely silent otherwise. It links up, it reports "connected",
     * and then it lifts nothing — and the only symptom is the city panel reading zero water, which
     * points at the pipework rather than at the pump standing one block too far from the river.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            player.displayClientMessage(statusOf(level, pos), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** What this pump would say if asked. Overridden by the intake, which can actually be dry. */
    protected Component statusOf(Level level, BlockPos pos) {
        return Component.translatable("message.citiesinlife.pump_relay");
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof Player player) {
            Component status = statusOf(level, pos);
            if (status != null) {
                player.displayClientMessage(status, true);
            }
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            // A link to a block that is gone would keep a phantom pump feeding the city.
            WaterGrid.get(serverLevel.getServer()).removeNode(level.dimension(), pos);
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
