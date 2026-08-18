package com.branciho.citiesinlife.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * The Planner Wand's red twin.
 *
 * <p>Identical to hold and identical to use, and that is the design: taking a building you have
 * fought for should be the same gesture as registering one you built, because it is the same
 * decision — this box is a building of this type in my city. The only differences are the colour and
 * that it refuses to work anywhere except on ground you have actually taken.
 *
 * <p>It does not require a rewrite. A conqueror may take a residential block and leave it exactly as
 * it was, which is usually what you want: the point of taking a city is that it is a city.
 */
public class WarPlannerWandItem extends PlannerWandItem {

    public WarPlannerWandItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.citiesinlife.war_wand_1")
                .withStyle(ChatFormatting.RED));
        tooltip.add(Component.translatable("tooltip.citiesinlife.war_wand_2")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.citiesinlife.war_wand_3")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
