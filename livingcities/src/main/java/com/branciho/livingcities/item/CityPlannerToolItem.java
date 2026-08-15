package com.branciho.livingcities.item;

import com.branciho.livingcities.config.LivingCitiesConfig;
import com.branciho.livingcities.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
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
 * The selection tool that turns a player-built structure into a registered building.
 *
 * <p>Right-click a block to set corner A, sneak-right-click to set corner B, and use in the air to
 * clear. The selection lives on the item as a data component, so it survives relogs and is per-tool.
 *
 * <p>This item only ever <em>records</em> a selection. Registering a building from it is a separate
 * server-validated action, because the selection is client-visible data and must never be trusted.
 */
public class CityPlannerToolItem extends Item {

    public CityPlannerToolItem(Properties properties) {
        super(properties);
    }

    public static SelectionData selectionOf(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.SELECTION.get(), SelectionData.EMPTY);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        ItemStack stack = context.getItemInHand();
        BlockPos pos = context.getClickedPos();
        SelectionData current = selectionOf(stack);
        SelectionData updated = player.isShiftKeyDown() ? current.withB(pos) : current.withA(pos);

        if (!context.getLevel().isClientSide()) {
            stack.set(ModDataComponents.SELECTION.get(), updated);
            feedback(player, updated, player.isShiftKeyDown() ? "B" : "A", pos);
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide()) {
                stack.set(ModDataComponents.SELECTION.get(), SelectionData.EMPTY);
                player.displayClientMessage(
                        Component.translatable("message.livingcities.selection_cleared").withStyle(ChatFormatting.GRAY), true);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
        return InteractionResultHolder.pass(stack);
    }

    private void feedback(Player player, SelectionData selection, String corner, BlockPos pos) {
        Component message;
        if (selection.isComplete()) {
            long volume = selection.volume();
            long maxVolume = LivingCitiesConfig.SERVER.maxSelectionVolume.get();
            ChatFormatting colour = volume > maxVolume ? ChatFormatting.RED : ChatFormatting.GREEN;
            message = Component.translatable("message.livingcities.selection_complete",
                    corner, pos.getX(), pos.getY(), pos.getZ(), volume).withStyle(colour);
        } else {
            message = Component.translatable("message.livingcities.selection_corner",
                    corner, pos.getX(), pos.getY(), pos.getZ()).withStyle(ChatFormatting.AQUA);
        }
        player.displayClientMessage(message, true);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        SelectionData selection = selectionOf(stack);
        if (selection.isComplete()) {
            tooltip.add(Component.translatable("tooltip.livingcities.selection_volume", selection.volume())
                    .withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.livingcities.planner_hint").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
