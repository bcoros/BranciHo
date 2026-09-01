package com.branciho.citiesinlife.item;

import net.minecraft.ChatFormatting;
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
 * The Military Tool: the only way anybody joins the army.
 *
 * <p>Every other service staffs itself. This one does not, because soldiers cost money and are the
 * one kind of person the player is meant to have opinions about individually — this one gets the
 * good rifle, that one is going on a course, and the one who keeps dying is being let go.
 *
 * <p>Like the Planner Wand, the item itself does almost nothing: it swallows the click so that
 * opening the screen does not also open whatever was behind it.
 */
public class MilitaryToolItem extends Item {

    public MilitaryToolItem(Properties properties) {
        super(properties);
    }

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
        tooltip.add(Component.translatable("tooltip.citiesinlife.military_1")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.citiesinlife.military_2")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.citiesinlife.military_3")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
