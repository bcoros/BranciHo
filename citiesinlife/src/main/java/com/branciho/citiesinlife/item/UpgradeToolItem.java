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
 * The Upgrade Tool: sneak and left click a machine to buy it a level.
 *
 * <p>Sneak + left click rather than plain right click, and that is not arbitrary. A right click
 * would collide with every machine that already answers one - the pump reports whether it is wet,
 * the tank reports its level, the sewer reports whether it has an outfall - and losing those to an
 * accidental purchase would be a bad trade. It is the same gesture the Pipe Connect Tool uses to
 * plumb a tap into a chest, for the same reason.
 *
 * <p>Left click, not right, because sneaking with something in your hand never reaches a block's
 * right-click handler at all: {@code ServerPlayerGameMode.useItemOn} skips block interaction
 * entirely when the player is sneaking and holding anything. That trap has already cost this mod
 * one rewrite, on the transport airport.
 */
public class UpgradeToolItem extends Item {

    public UpgradeToolItem(Properties properties) {
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
    public boolean canAttackBlock(net.minecraft.world.level.block.state.BlockState state,
                                  net.minecraft.world.level.Level level,
                                  net.minecraft.core.BlockPos pos,
                                  net.minecraft.world.entity.player.Player player) {
        // Never mines anything. An upgrade tool that broke the machine it missed would be cruel.
        return false;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.citiesinlife.upgrade_1")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.citiesinlife.upgrade_2")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.citiesinlife.upgrade_3")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
