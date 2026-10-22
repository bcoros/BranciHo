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
 * The Road Tool: draw a box, and the ground inside it becomes road that runs a particular way.
 *
 * <p>The same gesture as the Path Tool, because it is the same idea one step further on. Pavement
 * only had to say "people walk here"; a road has to say which way the traffic goes, where the
 * junctions are and where a car may be parked, and none of that fits on a box alone. So the box
 * still draws the shape and a separate panel — opened with R — decides what is being painted.
 *
 * <p>Splitting it that way keeps the drawing gesture identical to every other tool in the mod while
 * giving the extra state somewhere to live that is bigger than a corner of the HUD.
 */
public class RoadToolItem extends Item {

    public RoadToolItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        // Take the click before any block can act on it, so laying a street past a door does not
        // open the door.
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
        tooltip.add(Component.translatable("tooltip.citiesinlife.road_1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.citiesinlife.road_2").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.citiesinlife.road_3").withStyle(ChatFormatting.DARK_GRAY));
    }
}
