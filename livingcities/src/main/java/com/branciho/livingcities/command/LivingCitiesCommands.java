package com.branciho.livingcities.command;

import com.branciho.livingcities.LivingCities;
import com.branciho.livingcities.building.Building;
import com.branciho.livingcities.city.City;
import com.branciho.livingcities.net.BuildingActions;
import com.branciho.livingcities.city.CityRegistry;
import com.branciho.livingcities.net.ServerPayloadHandler;
import com.branciho.livingcities.net.payload.ClaimChunkPayload;
import com.branciho.livingcities.net.payload.RemoveBuildingPayload;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Admin and debug commands. Kept small; the real interface is the management screen. */
@EventBusSubscriber(modid = LivingCities.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class LivingCitiesCommands {

    private LivingCitiesCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("livingcities")
                .then(Commands.literal("here").executes(LivingCitiesCommands::here))
                .then(Commands.literal("building").executes(LivingCitiesCommands::building))
                .then(Commands.literal("unassign").executes(LivingCitiesCommands::unassign))
                .then(Commands.literal("claim").executes(context -> claim(context, true)))
                .then(Commands.literal("unclaim").executes(context -> claim(context, false)))
                .then(Commands.literal("list")
                        .requires(source -> source.hasPermission(2))
                        .executes(LivingCitiesCommands::list)));
    }

    /**
     * Open the panel for the building the player is standing in.
     *
     * <p>Without this the panel was reachable exactly once, at the moment of registration - and at that
     * moment the scan has not finished, so it opens empty. Closing it stranded the building with no way
     * back to its zoning controls, which made the mod's central feature look broken.
     */
    private static int building(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("message.livingcities.players_only"));
            return 0;
        }
        CityRegistry registry = CityRegistry.get(source.getServer());
        Building building = registry.buildingAt(player.serverLevel().dimension(), player.blockPosition());
        if (building == null) {
            source.sendFailure(Component.translatable("message.livingcities.not_in_building"));
            return 0;
        }
        BuildingActions.sendDetail(player, building);
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Delete the registration of the building the player is standing in.
     *
     * <p>The panel has a button for this, but the panel needs the registration to still be findable.
     * A ghost left by a demolished building is exactly the case where it is not, so this reaches it
     * from a position instead.
     */
    private static int unassign(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("message.livingcities.players_only"));
            return 0;
        }
        CityRegistry registry = CityRegistry.get(source.getServer());
        Building building = registry.buildingAt(player.serverLevel().dimension(), player.blockPosition());
        if (building == null) {
            source.sendFailure(Component.translatable("message.livingcities.not_in_building"));
            return 0;
        }
        BuildingActions.removeBuilding(player, new RemoveBuildingPayload(building.id()));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Claim or release the chunk the player is standing in.
     *
     * <p>Territory rules, pricing and permission checks all live in the packet handler already; this
     * only builds the same request the UI will eventually send, so there is exactly one code path
     * deciding whether a claim is allowed.
     */
    private static int claim(CommandContext<CommandSourceStack> context, boolean claiming) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("message.livingcities.players_only"));
            return 0;
        }
        ChunkPos chunk = player.chunkPosition();
        ServerPayloadHandler.claimChunk(player, new ClaimChunkPayload(chunk.x, chunk.z, claiming));
        return Command.SINGLE_SUCCESS;
    }

    private static int here(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("message.livingcities.players_only"));
            return 0;
        }
        CityRegistry registry = CityRegistry.get(source.getServer());
        ChunkPos chunk = player.chunkPosition();
        City city = registry.byChunk(player.serverLevel().dimension(), chunk);

        if (city == null) {
            source.sendSuccess(() -> Component.translatable("message.livingcities.unclaimed_chunk", chunk.x, chunk.z)
                    .withStyle(ChatFormatting.GRAY), false);
        } else {
            source.sendSuccess(() -> Component.literal(city.name())
                    .withStyle(ChatFormatting.AQUA)
                    .append(Component.literal(" - " + city.stats().population() + " residents, "
                            + city.claimCount() + " chunks, "
                            + ServerPayloadHandler.formatMoney(city.treasuryCents()))
                            .withStyle(ChatFormatting.GRAY)), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        CityRegistry registry = CityRegistry.get(server);

        if (registry.cities().isEmpty()) {
            source.sendSuccess(() -> Component.translatable("message.livingcities.no_cities_yet")
                    .withStyle(ChatFormatting.GRAY), false);
            return Command.SINGLE_SUCCESS;
        }

        for (City city : registry.cities()) {
            source.sendSuccess(() -> Component.literal(city.name())
                    .withStyle(ChatFormatting.AQUA)
                    .append(Component.literal(" pop " + city.stats().population()
                            + ", chunks " + city.claimCount()
                            + ", treasury " + ServerPayloadHandler.formatMoney(city.treasuryCents()))
                            .withStyle(ChatFormatting.GRAY)), false);
        }
        return registry.cities().size();
    }
}
