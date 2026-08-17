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
 * The pipe connect tool: draws the plumbing you are not meant to have to lay by hand.
 *
 * <p>It places nothing. The link it makes has no model and no block — running a pipe across a
 * riverbank and up a hillside is busywork, so the stretch between the intake and the city is drawn
 * rather than built. Everything from the end pump onward is real pipe, because that part is the
 * interesting one.
 *
 * <p>Holding it also lights up the links already drawn, which is the only way to see them at all.
 */
public class PipeLineToolItem extends Item {

    public PipeLineToolItem(Properties properties) {
        super(properties);
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
        tooltip.add(Component.translatable("tooltip.citiesinlife.pipe_1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.citiesinlife.pipe_2").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.citiesinlife.pipe_3").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.citiesinlife.pipe_4").withStyle(ChatFormatting.DARK_GRAY));
    }
}
