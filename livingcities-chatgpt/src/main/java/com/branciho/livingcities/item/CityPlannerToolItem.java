package com.branciho.livingcities.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CityPlannerToolItem extends Item {
    private static final Map<UUID, Selection> SELECTIONS = new ConcurrentHashMap<>();

    public CityPlannerToolItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;

        BlockPos clicked = context.getClickedPos().immutable();
        if (!context.getLevel().isClientSide()) {
            Selection old = SELECTIONS.getOrDefault(player.getUUID(), Selection.EMPTY);
            Selection updated = player.isShiftKeyDown() ? old.withB(clicked) : old.withA(clicked);
            SELECTIONS.put(player.getUUID(), updated);
            String label = player.isShiftKeyDown() ? "B" : "A";
            player.displayClientMessage(Component.literal("Planner point " + label + ": " + format(clicked))
                    .withStyle(ChatFormatting.AQUA), true);
            if (updated.complete()) {
                player.displayClientMessage(Component.literal("Selection ready. Use /livingcities building add <type> <name>")
                        .withStyle(ChatFormatting.GREEN), false);
            }
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide());
    }

    public static Selection selection(UUID playerId) {
        return SELECTIONS.getOrDefault(playerId, Selection.EMPTY);
    }

    public static void clear(UUID playerId) {
        SELECTIONS.remove(playerId);
    }

    private static String format(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    public record Selection(BlockPos a, BlockPos b) {
        public static final Selection EMPTY = new Selection(null, null);
        public Selection withA(BlockPos value) { return new Selection(value, b); }
        public Selection withB(BlockPos value) { return new Selection(a, value); }
        public boolean complete() { return a != null && b != null; }
        public BlockPos min() { return new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ())); }
        public BlockPos max() { return new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ())); }
        public long volume() {
            BlockPos min = min(); BlockPos max = max();
            return (long)(max.getX() - min.getX() + 1) * (max.getY() - min.getY() + 1) * (max.getZ() - min.getZ() + 1);
        }
    }
}
