package com.branciho.livingcities.command;

import com.branciho.livingcities.LivingCities;
import com.branciho.livingcities.city.City;
import com.branciho.livingcities.city.CityRegistry;
import com.branciho.livingcities.net.ServerPayloadHandler;
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
                .then(Commands.literal("list")
                        .requires(source -> source.hasPermission(2))
                        .executes(LivingCitiesCommands::list)));
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
