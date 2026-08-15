package com.branciho.livingcities.block;

import com.branciho.livingcities.blockentity.CoalGeneratorBlockEntity;
import com.branciho.livingcities.power.PowerComponent;
import com.branciho.livingcities.power.PowerRole;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Coal generation: steady output for as long as it is fed, and the answer to solar going dark at night.
 *
 * <p>Fuel goes in by right-clicking with it, and hoppers work through the block entity's item handler.
 * There is deliberately no container screen yet - a menu, a slot layout and a synced burn gauge are a
 * lot of surface for an alpha, and right-clicking a generator with a stack of coal is a reasonable way
 * to load it in the meantime.
 */
public class CoalGeneratorBlock extends Block implements EntityBlock, PowerComponent {

    public static final MapCodec<CoalGeneratorBlock> CODEC = simpleCodec(CoalGeneratorBlock::new);

    /** Drives the lit texture and the glow, so a running plant is visible from across a valley. */
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public CoalGeneratorBlock(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any().setValue(LIT, Boolean.FALSE));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CoalGeneratorBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                           BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof CoalGeneratorBlockEntity generator) {
                generator.serverTick();
            }
        };
    }

    @Override
    public PowerRole powerRole() {
        return PowerRole.GENERATOR;
    }

    @Override
    public int generationKw(ServerLevel level, BlockPos pos, BlockState state) {
        return level.getBlockEntity(pos) instanceof CoalGeneratorBlockEntity generator
                ? generator.currentOutputKw()
                : 0;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (level.isClientSide()) {
            return ItemInteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof CoalGeneratorBlockEntity generator
                && generator.tryInsertFuel(stack, player)) {
            return ItemInteractionResult.CONSUME;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof CoalGeneratorBlockEntity generator) {
            generator.reportStatus(player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
