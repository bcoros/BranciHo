package com.branciho.livingcities.registry;

import com.branciho.livingcities.LivingCities;
import com.branciho.livingcities.item.CityPlannerToolItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {

    private ModItems() {
    }

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(LivingCities.MOD_ID);

    public static final DeferredItem<CityPlannerToolItem> CITY_PLANNER_TOOL = ITEMS.registerItem(
            "city_planner_tool",
            CityPlannerToolItem::new,
            new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));

    public static final DeferredItem<BlockItem> CITY_HALL_CORE_ITEM =
            ITEMS.registerSimpleBlockItem("city_hall_core", ModBlocks.CITY_HALL_CORE);

    public static final DeferredItem<BlockItem> ENTRANCE_MARKER_ITEM =
            ITEMS.registerSimpleBlockItem("entrance_marker", ModBlocks.ENTRANCE_MARKER);

    public static final DeferredItem<BlockItem> PATH_NODE_ITEM =
            ITEMS.registerSimpleBlockItem("path_node", ModBlocks.PATH_NODE);

    public static final DeferredItem<BlockItem> POWER_CABLE_ITEM =
            ITEMS.registerSimpleBlockItem("power_cable", ModBlocks.POWER_CABLE);

    public static final DeferredItem<BlockItem> TRANSMISSION_PYLON_ITEM =
            ITEMS.registerSimpleBlockItem("transmission_pylon", ModBlocks.TRANSMISSION_PYLON);

    public static final DeferredItem<BlockItem> SUBSTATION_ITEM =
            ITEMS.registerSimpleBlockItem("substation", ModBlocks.SUBSTATION);

    public static final DeferredItem<BlockItem> SOLAR_PANEL_ITEM =
            ITEMS.registerSimpleBlockItem("solar_panel", ModBlocks.SOLAR_PANEL);

    public static final DeferredItem<BlockItem> COAL_GENERATOR_ITEM =
            ITEMS.registerSimpleBlockItem("coal_generator", ModBlocks.COAL_GENERATOR);

    public static final DeferredItem<BlockItem> TRANSFORMER_ITEM =
            ITEMS.registerSimpleBlockItem("transformer", ModBlocks.TRANSFORMER);

    public static final DeferredItem<BlockItem> WIND_TURBINE_ITEM =
            ITEMS.registerSimpleBlockItem("wind_turbine", ModBlocks.WIND_TURBINE);

    public static final DeferredItem<BlockItem> WATER_PIPE_ITEM =
            ITEMS.registerSimpleBlockItem("water_pipe", ModBlocks.WATER_PIPE);

    public static final DeferredItem<BlockItem> WATER_PUMP_ITEM =
            ITEMS.registerSimpleBlockItem("water_pump", ModBlocks.WATER_PUMP);

    public static final DeferredItem<BlockItem> WATER_TOWER_ITEM =
            ITEMS.registerSimpleBlockItem("water_tower", ModBlocks.WATER_TOWER);

    public static final DeferredItem<BlockItem> PUMPING_STATION_ITEM =
            ITEMS.registerSimpleBlockItem("pumping_station", ModBlocks.PUMPING_STATION);

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}
