package com.branciho.livingcities.item;

import com.branciho.livingcities.config.LivingCitiesConfig;
import com.branciho.livingcities.net.BuildingActions;
import com.branciho.livingcities.net.payload.AssignBuildingPayload;
import com.branciho.livingcities.registry.ModDataComponents;
import net.minecraft.server.level.ServerPlayer;
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

    /**
     * In the air: sneak to clear the selection, or use normally to register it as a building.
     *
     * <p>Registration is driven entirely from the server's own copy of the selection component rather
     * than from a packet carrying coordinates. The server already has the authoritative corners — it
     * wrote them in {@link #useOn} — so there is simply nothing here for a modified client to lie about.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        SelectionData selection = selectionOf(stack);

        if (player.isShiftKeyDown()) {
            if (!level.isClientSide()) {
                stack.set(ModDataComponents.SELECTION.get(), SelectionData.EMPTY);
                player.displayClientMessage(
                        Component.translatable("message.livingcities.selection_cleared").withStyle(ChatFormatting.GRAY), true);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }

        if (!selection.isComplete()) {
            if (!level.isClientSide()) {
                player.displayClientMessage(
                        Component.translatable("message.livingcities.selection_incomplete").withStyle(ChatFormatting.GRAY), true);
            }
            return InteractionResultHolder.pass(stack);
        }

        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            BuildingActions.assignBuilding(serverPlayer, new AssignBuildingPayload(
                    selection.pointA().orElseThrow(), selection.pointB().orElseThrow(), ""));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
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
