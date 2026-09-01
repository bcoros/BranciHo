package com.branciho.citiesinlife.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;

import java.util.List;

/**
 * The power line tool: click one power block, then another, and a line runs between them.
 *
 * <p>Like the planner wand, the pending click lives on the client and the server is told only "these
 * two positions" — which it then re-checks for range, block type and duplication. The item itself
 * swallows the interaction so right-clicking a station does not open anything.
 */
public class PowerLineToolItem extends Item {

    public PowerLineToolItem(Properties properties) {
        super(properties);
    }

    /**
     * Take the click before the block ever sees it.
     *
     * <p>Cancelling the interaction on the client is not enough: the use packet still goes to the
     * server, which runs the block's own right-click handler before the item's. So right-clicking a
     * valve with this in hand was starting a link <em>and</em> working the valve, which is a
     * spectacular way to lose track of which branch of a network is shut. This hook runs first and
     * consumes the click outright.
     */
    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean canAttackBlock(net.minecraft.world.level.block.state.BlockState state,
                                  net.minecraft.world.level.Level level,
                                  net.minecraft.core.BlockPos pos,
                                  net.minecraft.world.entity.player.Player player) {
        return false;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.citiesinlife.line_1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.citiesinlife.line_2").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.citiesinlife.line_3").withStyle(ChatFormatting.DARK_GRAY));
    }
}
