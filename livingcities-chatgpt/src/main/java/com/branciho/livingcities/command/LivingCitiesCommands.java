package com.branciho.livingcities.command;

import com.branciho.livingcities.LivingCities;
import com.branciho.livingcities.city.Building;
import com.branciho.livingcities.city.BuildingScanner;
import com.branciho.livingcities.city.BuildingType;
import com.branciho.livingcities.city.City;
import com.branciho.livingcities.city.CityData;
import com.branciho.livingcities.item.CityPlannerToolItem;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.List;
import java.util.UUID;

@EventBusSubscriber(modid = LivingCities.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class LivingCitiesCommands {
    private static final long MAX_SELECTION_VOLUME = 2_000_000L;
    private LivingCitiesCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("livingcities")
                .then(Commands.literal("create")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(LivingCitiesCommands::create)))
                .then(Commands.literal("status").executes(LivingCitiesCommands::status))
                .then(Commands.literal("claim").executes(LivingCitiesCommands::claim))
                .then(Commands.literal("building")
                        .then(Commands.literal("add")
                                .then(Commands.argument("type", StringArgumentType.word())
                                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                                .executes(LivingCitiesCommands::addBuilding)))))
                .then(Commands.literal("planner").then(Commands.literal("clear").executes(LivingCitiesCommands::clearPlanner))));
    }

    private static int create(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) return 0;
        CityData data = CityData.get(context.getSource().getServer());
        if (data.cityOwnedBy(player.getUUID()) != null) {
            context.getSource().sendFailure(Component.literal("You already own a city in Alpha 1."));
            return 0;
        }
        if (!hasCityHallNearby(player.serverLevel(), player.blockPosition())) {
            context.getSource().sendFailure(Component.literal("Place a City Hall Core within 16 blocks first."));
            return 0;
        }
        String name = StringArgumentType.getString(context, "name").trim();
        if (name.isEmpty() || name.length() > 48) {
            context.getSource().sendFailure(Component.literal("City name must be 1-48 characters."));
            return 0;
        }
        String dimension = player.serverLevel().dimension().location().toString();
        City city = new City(UUID.randomUUID(), player.getUUID(), name, dimension, 100_000.0, 25);
        city.claim(player.chunkPosition());
        data.addCity(city);
        context.getSource().sendSuccess(() -> Component.literal("Created " + name + " with $100,000 and claimed this chunk.")
                .withStyle(ChatFormatting.GREEN), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int status(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) return 0;
        CityData data = CityData.get(context.getSource().getServer());
        City city = data.cityOwnedBy(player.getUUID());
        if (city == null) {
            context.getSource().sendFailure(Component.literal("You do not own a Living Cities city yet."));
            return 0;
        }
        List<Building> buildings = data.buildingsOf(city.id());
        int housing = buildings.stream().mapToInt(Building::housing).sum();
        int jobs = buildings.stream().mapToInt(Building::jobs).sum();
        context.getSource().sendSuccess(() -> Component.literal("=== " + city.name() + " ===").withStyle(ChatFormatting.GOLD), false);
        context.getSource().sendSuccess(() -> Component.literal("Population: " + city.population() + " | Housing: " + housing + " | Jobs: " + jobs), false);
        context.getSource().sendSuccess(() -> Component.literal(String.format("Treasury: $%,.0f | Territory: %d chunks | Buildings: %d", city.treasury(), city.chunks().size(), buildings.size())), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int claim(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) return 0;
        CityData data = CityData.get(context.getSource().getServer());
        City city = data.cityOwnedBy(player.getUUID());
        if (city == null) return fail(context, "Create a city first.");
        ChunkPos target = player.chunkPosition();
        String dimension = player.serverLevel().dimension().location().toString();
        if (!city.dimension().equals(dimension)) return fail(context, "Alpha 1 territory must stay in the city's dimension.");
        City occupied = data.cityAt(dimension, target);
        if (occupied != null) return fail(context, "That chunk is already claimed by " + occupied.name() + ".");
        if (!city.adjacentToClaim(target)) return fail(context, "New territory must touch your existing border.");
        double cost = 1000.0 + city.chunks().size() * 250.0;
        if (!city.spend(cost)) return fail(context, "Your city needs $" + (long) cost + " to claim this chunk.");
        city.claim(target);
        data.setDirty();
        context.getSource().sendSuccess(() -> Component.literal("Claimed chunk " + target.x + ", " + target.z + " for $" + (long) cost).withStyle(ChatFormatting.GREEN), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int addBuilding(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) return 0;
        CityData data = CityData.get(context.getSource().getServer());
        City city = data.cityOwnedBy(player.getUUID());
        if (city == null) return fail(context, "Create a city first.");
        BuildingType type;
        try {
            type = BuildingType.parse(StringArgumentType.getString(context, "type"));
        } catch (IllegalArgumentException ex) {
            return fail(context, "Type must be residential, commercial, office, or industrial.");
        }
        CityPlannerToolItem.Selection selection = CityPlannerToolItem.selection(player.getUUID());
        if (!selection.complete()) return fail(context, "Select point A and point B with the City Planner first.");
        if (selection.volume() > MAX_SELECTION_VOLUME) return fail(context, "Selection is too large for Alpha 1 (max 2,000,000 blocks).");
        BlockPos min = selection.min();
        BlockPos max = selection.max();
        String dimension = player.serverLevel().dimension().location().toString();
        if (!city.dimension().equals(dimension)) return fail(context, "The building must be in your city's dimension.");
        for (ChunkPos chunk : chunksTouched(min, max)) {
            if (!city.owns(chunk)) return fail(context, "The entire building must be inside your claimed city chunks.");
        }
        String name = StringArgumentType.getString(context, "name").trim();
        BuildingScanner.ScanResult scan = BuildingScanner.scan(player.serverLevel(), min, max, type);
        Building building = new Building(UUID.randomUUID(), city.id(), name, type, min, max,
                scan.floors(), scan.usableArea(), scan.housing(), scan.jobs());
        data.addBuilding(building);
        CityPlannerToolItem.clear(player.getUUID());
        context.getSource().sendSuccess(() -> Component.literal("Registered " + name + " as " + type.name().toLowerCase()
                + " | floors " + scan.floors() + " | usable area " + scan.usableArea()
                + " | housing " + scan.housing() + " | jobs " + scan.jobs()).withStyle(ChatFormatting.GREEN), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int clearPlanner(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) return 0;
        CityPlannerToolItem.clear(player.getUUID());
        context.getSource().sendSuccess(() -> Component.literal("Planner selection cleared."), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int fail(CommandContext<CommandSourceStack> context, String text) {
        context.getSource().sendFailure(Component.literal(text));
        return 0;
    }

    private static boolean hasCityHallNearby(ServerLevel level, BlockPos center) {
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-16, -8, -16), center.offset(16, 8, 16))) {
            if (level.getBlockState(pos).is(com.branciho.livingcities.registry.ModBlocks.CITY_HALL_CORE.get())) return true;
        }
        return false;
    }

    private static java.util.List<ChunkPos> chunksTouched(BlockPos min, BlockPos max) {
        java.util.List<ChunkPos> chunks = new java.util.ArrayList<>();
        int minX = min.getX() >> 4, maxX = max.getX() >> 4;
        int minZ = min.getZ() >> 4, maxZ = max.getZ() >> 4;
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) chunks.add(new ChunkPos(x, z));
        return chunks;
    }
}
