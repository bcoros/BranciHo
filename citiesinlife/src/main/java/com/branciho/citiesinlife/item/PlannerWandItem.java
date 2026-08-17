package com.branciho.citiesinlife.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * The Planner Wand: the only item in the mod, and the whole interface to it.
 *
 * <p>The selection itself is driven entirely from the client — the wand's job here is to swallow the
 * interaction so that right-clicking a chest while planning does not open the chest. All the state
 * lives client-side until the player commits, at which point one packet carries the request and the
 * server re-derives everything.
 */
public class PlannerWandItem extends Item {

    public PlannerWandItem(Properties properties) {
        super(properties);
    }

    /**
     * Consume the right-click without doing anything.
     *
     * <p>The client's interaction handler has already run by this point and updated the selection.
     * Returning success here stops the block underneath being activated and stops the arm swinging
     * as though something was placed.
     */
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
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return InteractionResultHolder.success(player.getItemInHand(hand));
    }

    /** Never let the wand break a block; left click is the confirm button while planning. */
    @Override
    public boolean canAttackBlock(net.minecraft.world.level.block.state.BlockState state, Level level,
                                  net.minecraft.core.BlockPos pos, Player player) {
        return false;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.citiesinlife.wand_1")
                .withStyle(net.minecraft.ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.citiesinlife.wand_2")
                .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.citiesinlife.wand_3")
                .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
    }
}
