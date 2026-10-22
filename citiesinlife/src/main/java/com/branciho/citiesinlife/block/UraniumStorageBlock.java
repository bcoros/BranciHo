package com.branciho.citiesinlife.block;

import com.branciho.citiesinlife.blockentity.UraniumStorageBlockEntity;
import com.branciho.citiesinlife.registry.ModItems;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Where the uranium goes in.
 *
 * <p>Right click with uranium to load it, empty-handed to read the gauge. Deliberately not a
 * container with a menu: a chest full of fuel invites hoppers and automation, and the point of the
 * store is that somebody has to walk down to the reactor every three quarters of an hour.
 *
 * <p>It is also the thermal centre of the plant. The cooled input and the heated output both have
 * to stand against it, which is what turns "put the cooling blocks somewhere" into a layout with a
 * middle.
 */
public class UraniumStorageBlock extends BaseEntityBlock {

    public static final MapCodec<UraniumStorageBlock> CODEC = simpleCodec(UraniumStorageBlock::new);

    public UraniumStorageBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new UraniumStorageBlockEntity(pos, state);
    }

    /**
     * Loading fuel.
     *
     * <p>Swallows as much of the held stack as will fit rather than one at a time, because a core
     * takes thirty-two of these at a minimum and clicking thirty-two times is not a game mechanic.
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                              BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hit) {
        if (!stack.is(ModItems.URANIUM.get())) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof UraniumStorageBlockEntity store)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        int taken = Math.min(stack.getCount(), store.roomForItems());
        if (taken <= 0) {
            player.displayClientMessage(store.status(), true);
            return ItemInteractionResult.CONSUME;
        }
        store.addItems(taken);
        if (!player.getAbilities().instabuild) {
            stack.shrink(taken);
        }
        level.playSound(null, pos, SoundEvents.BONE_BLOCK_PLACE, SoundSource.BLOCKS, 0.7F, 0.8F);
        player.displayClientMessage(store.status(), true);
        return ItemInteractionResult.CONSUME;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!level.isClientSide
                && level.getBlockEntity(pos) instanceof UraniumStorageBlockEntity store) {
            player.displayClientMessage(store.status(), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof UraniumStorageBlockEntity store) {
            return Math.round(store.fraction() * 15.0F);
        }
        return 0;
    }
}
