package com.branciho.livingcities.block;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class CityHallCoreBlock extends Block {
    public CityHallCoreBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide()) {
            player.displayClientMessage(Component.literal("Living Cities City Hall Core").withStyle(ChatFormatting.GOLD), false);
            player.displayClientMessage(Component.literal("Create a city with: /livingcities create <name>").withStyle(ChatFormatting.YELLOW), false);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
