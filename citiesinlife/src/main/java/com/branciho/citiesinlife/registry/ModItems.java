package com.branciho.citiesinlife.registry;

import com.branciho.citiesinlife.CitiesInLife;
import com.branciho.citiesinlife.item.ExtinguisherItem;
import com.branciho.citiesinlife.item.MilitaryToolItem;
import com.branciho.citiesinlife.item.PathToolItem;
import com.branciho.citiesinlife.item.RoadToolItem;
import com.branciho.citiesinlife.item.UpgradeToolItem;
import com.branciho.citiesinlife.item.PlannerWandItem;
import com.branciho.citiesinlife.item.PipeLineToolItem;
import com.branciho.citiesinlife.item.PowerLineToolItem;
import com.branciho.citiesinlife.item.WarPlannerWandItem;
import com.branciho.citiesinlife.item.RepairToolItem;
import com.branciho.citiesinlife.item.ServiceRifleItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Every item and block item this mod adds. */
public final class ModItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CitiesInLife.MOD_ID);

    public static final DeferredItem<PlannerWandItem> PLANNER_WAND = ITEMS.register("planner_wand",
            () -> new PlannerWandItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<PowerLineToolItem> POWER_LINE_TOOL = ITEMS.register("power_line_tool",
            () -> new PowerLineToolItem(new Item.Properties().stacksTo(1)));

    // registerSimpleBlockItem returns DeferredItem<BlockItem>; declaring these as DeferredItem<Item>
    // compiles against some IDE indexes and then fails the real build.
    public static final DeferredItem<BlockItem> SOLAR_PANEL =
            ITEMS.registerSimpleBlockItem("solar_panel", ModBlocks.SOLAR_PANEL);

    public static final DeferredItem<BlockItem> POWER_MAST =
            ITEMS.registerSimpleBlockItem("power_mast", ModBlocks.POWER_MAST);

    public static final DeferredItem<BlockItem> GRID_PYLON =
            ITEMS.registerSimpleBlockItem("grid_pylon", ModBlocks.GRID_PYLON);

    public static final DeferredItem<BlockItem> TOURIST_AIRPLANE =
            ITEMS.registerSimpleBlockItem("tourist_airplane", ModBlocks.TOURIST_AIRPLANE);

    public static final DeferredItem<BlockItem> TRANSPORT_AIRPLANE =
            ITEMS.registerSimpleBlockItem("transport_airplane", ModBlocks.TRANSPORT_AIRPLANE);

    public static final DeferredItem<BlockItem> TRANSIT_STATION =
            ITEMS.registerSimpleBlockItem("transit_station", ModBlocks.TRANSIT_STATION);

    public static final DeferredItem<BlockItem> FACTORY_OUTPUT =
            ITEMS.registerSimpleBlockItem("factory_output", ModBlocks.FACTORY_OUTPUT);

    public static final DeferredItem<BlockItem> BOILER =
            ITEMS.registerSimpleBlockItem("boiler", ModBlocks.BOILER);

    public static final DeferredItem<BlockItem> CHIMNEY =
            ITEMS.registerSimpleBlockItem("chimney", ModBlocks.CHIMNEY);

    public static final DeferredItem<BlockItem> TURBINE =
            ITEMS.registerSimpleBlockItem("turbine", ModBlocks.TURBINE);

    public static final DeferredItem<PipeLineToolItem> PIPE_LINE_TOOL = ITEMS.register("pipe_line_tool",
            () -> new PipeLineToolItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<BlockItem> STARTER_PUMP =
            ITEMS.registerSimpleBlockItem("starter_pump", ModBlocks.STARTER_PUMP);

    public static final DeferredItem<BlockItem> PUMP =
            ITEMS.registerSimpleBlockItem("pump", ModBlocks.PUMP);

    public static final DeferredItem<BlockItem> END_PUMP =
            ITEMS.registerSimpleBlockItem("end_pump", ModBlocks.END_PUMP);

    public static final DeferredItem<BlockItem> WATER_PIPE =
            ITEMS.registerSimpleBlockItem("water_pipe", ModBlocks.WATER_PIPE);

    public static final DeferredItem<BlockItem> PIPE_CONNECTOR =
            ITEMS.registerSimpleBlockItem("pipe_connector", ModBlocks.PIPE_CONNECTOR);

    public static final DeferredItem<BlockItem> VALVE =
            ITEMS.registerSimpleBlockItem("valve", ModBlocks.VALVE);

    public static final DeferredItem<BlockItem> WATER_STORAGE =
            ITEMS.registerSimpleBlockItem("water_storage", ModBlocks.WATER_STORAGE);

    public static final DeferredItem<RepairToolItem> REPAIR_TOOL = ITEMS.register("repair_tool",
            () -> new RepairToolItem(new Item.Properties().stacksTo(1).durability(0)));

    public static final DeferredItem<BlockItem> INDESTRUCTIBLE_PIPE =
            ITEMS.registerSimpleBlockItem("indestructible_pipe", ModBlocks.INDESTRUCTIBLE_PIPE);

    public static final DeferredItem<BlockItem> WINDMILL_WHITE =
            ITEMS.registerSimpleBlockItem("windmill_white", ModBlocks.WINDMILL_WHITE);

    public static final DeferredItem<BlockItem> WINDMILL_BLACK =
            ITEMS.registerSimpleBlockItem("windmill_black", ModBlocks.WINDMILL_BLACK);

    public static final DeferredItem<BlockItem> WINDMILL_BLUE =
            ITEMS.registerSimpleBlockItem("windmill_blue", ModBlocks.WINDMILL_BLUE);

    public static final DeferredItem<BlockItem> WINDMILL_GREEN =
            ITEMS.registerSimpleBlockItem("windmill_green", ModBlocks.WINDMILL_GREEN);

    public static final DeferredItem<ExtinguisherItem> EXTINGUISHER = ITEMS.register("extinguisher",
            () -> new ExtinguisherItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<PathToolItem> PATH_TOOL = ITEMS.register("path_tool",
            () -> new PathToolItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<RoadToolItem> ROAD_TOOL = ITEMS.register("road_tool",
            () -> new RoadToolItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<BlockItem> OFFICE_SPACE =
            ITEMS.registerSimpleBlockItem("office_space", ModBlocks.OFFICE_SPACE);

    public static final DeferredItem<BlockItem> REGISTER_COUNTER =
            ITEMS.registerSimpleBlockItem("register_counter", ModBlocks.REGISTER_COUNTER);

    public static final DeferredItem<BlockItem> ALARM =
            ITEMS.registerSimpleBlockItem("alarm", ModBlocks.ALARM);

    public static final DeferredItem<BlockItem> END_PIPE =
            ITEMS.registerSimpleBlockItem("end_pipe", ModBlocks.END_PIPE);

    public static final DeferredItem<BlockItem> URANIUM_ORE =
            ITEMS.registerSimpleBlockItem("uranium_ore", ModBlocks.URANIUM_ORE);

    public static final DeferredItem<BlockItem> DEEPSLATE_URANIUM_ORE =
            ITEMS.registerSimpleBlockItem("deepslate_uranium_ore", ModBlocks.DEEPSLATE_URANIUM_ORE);

    /** What the ore drops. Smelts into the refined article the reactor actually eats. */
    public static final DeferredItem<Item> RAW_URANIUM = ITEMS.register("raw_uranium",
            () -> new Item(new Item.Properties()));

    /**
     * Reactor fuel. One of these is exactly one fuel rod block's worth.
     *
     * <p>That equivalence is deliberate and it is the whole of the supply chain: a player can count
     * the blocks in their core and know how many they need, without a wiki.
     */
    public static final DeferredItem<Item> URANIUM = ITEMS.register("uranium",
            () -> new Item(new Item.Properties()));

    public static final DeferredItem<BlockItem> SEWAGE_COLLECTOR =
            ITEMS.registerSimpleBlockItem("sewage_collector", ModBlocks.SEWAGE_COLLECTOR);

    public static final DeferredItem<UpgradeToolItem> UPGRADE_TOOL = ITEMS.register("upgrade_tool",
            () -> new UpgradeToolItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<BlockItem> SERVICE_SPAWNER =
            ITEMS.registerSimpleBlockItem("service_spawner", ModBlocks.SERVICE_SPAWNER);

    public static final DeferredItem<WarPlannerWandItem> WAR_PLANNER_WAND =
            ITEMS.register("war_planner_wand",
                    () -> new WarPlannerWandItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<MilitaryToolItem> MILITARY_TOOL = ITEMS.register("military_tool",
            () -> new MilitaryToolItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<ServiceRifleItem> SERVICE_RIFLE = ITEMS.register("service_rifle",
            () -> new ServiceRifleItem(new Item.Properties().stacksTo(1)));

    private ModItems() {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
