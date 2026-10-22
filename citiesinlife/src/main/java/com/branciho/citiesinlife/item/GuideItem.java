package com.branciho.citiesinlife.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The manual.
 *
 * <p>Everything in this mod is discoverable by punching it - every block says what it wants and
 * every refusal names the block and the coordinates. That is the right way round for fixing
 * something you have already half built, and completely useless for the question that comes first,
 * which is "what am I supposed to build".
 *
 * <p>Opens on the client and nowhere else. There is nothing here the server needs to know about:
 * the book has no state, holds nothing, and reading it changes nothing.
 */
public class GuideItem extends Item {

    public GuideItem(Properties properties) {
        super(properties);
    }

    /**
     * Nothing happens here, and that is deliberate.
     *
     * <p>Opening a screen means touching client-only classes, and an item class is loaded on a
     * dedicated server. The client watches for a right click with this in hand and opens the book
     * itself, the same way the planner wand and the military tool already do.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }
}
