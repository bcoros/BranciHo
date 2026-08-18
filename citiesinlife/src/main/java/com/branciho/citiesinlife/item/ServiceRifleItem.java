package com.branciho.citiesinlife.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * The city's own rifle.
 *
 * <p>Here because a soldier who cannot shoot is a soldier who walks up to the enemy and punches
 * them, and a war fought entirely at arm's length is not a war. Any gun from any mod can be handed
 * to a soldier and they will fire it — but the ballistics are ours in that case, because a modded
 * gun's aiming and ammunition live in that mod's player-side code and nothing here can drive them on
 * an NPC's behalf. This is the one gun where what the player sees and what a soldier does are the
 * same thing all the way down.
 *
 * <p>Deliberately unremarkable: no ammunition, no reload, a short cooldown. It exists so the
 * military service works on its own, not to be a weapons mod.
 */
public class ServiceRifleItem extends Item {

    /** How long between shots. Fast enough to matter in a fight, slow enough not to be a laser. */
    private static final int COOLDOWN_TICKS = 10;

    public ServiceRifleItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        Gunfire.fire(level, player, 0.0D);
        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        player.swing(hand, true);
        return InteractionResultHolder.sidedSuccess(held, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.citiesinlife.rifle_1")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.citiesinlife.rifle_2")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
