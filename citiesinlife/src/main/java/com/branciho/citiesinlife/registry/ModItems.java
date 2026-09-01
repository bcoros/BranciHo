package com.branciho.citiesinlife.registry;

import com.branciho.citiesinlife.CitiesInLife;
import com.branciho.citiesinlife.item.GuideItem;
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
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Every item and block item this mod adds. */
public final class ModItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CitiesInLife.MOD_ID);

    /**
     * The manual.
     *
     * <p>First in the list because it is the first thing a new player should be handed, and the
     * only item in the mod that does nothing to the world at all.
     */
    public static final DeferredItem<GuideItem> TUTORIAL_BOOK = ITEMS.register("tutorial_book",
            () -> new GuideItem(new Item.Properties().stacksTo(1)));

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

    public static final DeferredItem<BlockItem> CITY_FLAG =
            ITEMS.registerSimpleBlockItem("city_flag", ModBlocks.CITY_FLAG);

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

    // ------------------------------------------------------------- the reactor

    public static final DeferredItem<BlockItem> FUEL_ROD =
            ITEMS.registerSimpleBlockItem("fuel_rod", ModBlocks.FUEL_ROD);

    public static final DeferredItem<BlockItem> CONTROL_ROD =
            ITEMS.registerSimpleBlockItem("control_rod", ModBlocks.CONTROL_ROD);

    public static final DeferredItem<BlockItem> SEALING_BLOCK =
            ITEMS.registerSimpleBlockItem("sealing_block", ModBlocks.SEALING_BLOCK);

    public static final DeferredItem<BlockItem> URANIUM_STORAGE =
            ITEMS.registerSimpleBlockItem("uranium_storage", ModBlocks.URANIUM_STORAGE);

    public static final DeferredItem<BlockItem> NUCLEAR_TURBINE =
            ITEMS.registerSimpleBlockItem("nuclear_turbine", ModBlocks.NUCLEAR_TURBINE);

    public static final DeferredItem<BlockItem> INPUT_WATER_PORT =
            ITEMS.registerSimpleBlockItem("input_water_port", ModBlocks.INPUT_WATER_PORT);

    public static final DeferredItem<BlockItem> OUTPUT_COOLED_PORT =
            ITEMS.registerSimpleBlockItem("output_cooled_port", ModBlocks.OUTPUT_COOLED_PORT);

    public static final DeferredItem<BlockItem> INPUT_COOLED_PORT =
            ITEMS.registerSimpleBlockItem("input_cooled_port", ModBlocks.INPUT_COOLED_PORT);

    public static final DeferredItem<BlockItem> OUTPUT_HEATED_PORT =
            ITEMS.registerSimpleBlockItem("output_heated_port", ModBlocks.OUTPUT_HEATED_PORT);

    public static final DeferredItem<BlockItem> STEAM_EMITTER =
            ITEMS.registerSimpleBlockItem("steam_emitter", ModBlocks.STEAM_EMITTER);

    public static final DeferredItem<BlockItem> PRESSURIZED_PIPE =
            ITEMS.registerSimpleBlockItem("pressurized_pipe", ModBlocks.PRESSURIZED_PIPE);

    public static final DeferredItem<BlockItem> COOLER_LEVER =
            ITEMS.registerSimpleBlockItem("cooler_lever", ModBlocks.COOLER_LEVER);

    public static final DeferredItem<BlockItem> HEAT_LEVER =
            ITEMS.registerSimpleBlockItem("heat_lever", ModBlocks.HEAT_LEVER);

    public static final DeferredItem<BlockItem> PRESSURE_LEVER =
            ITEMS.registerSimpleBlockItem("pressure_lever", ModBlocks.PRESSURE_LEVER);

    public static final DeferredItem<BlockItem> TURBINE_POWER =
            ITEMS.registerSimpleBlockItem("turbine_power", ModBlocks.TURBINE_POWER);

    public static final DeferredItem<BlockItem> MAIN_MONITOR =
            ITEMS.registerSimpleBlockItem("main_monitor", ModBlocks.MAIN_MONITOR);

    /**
     * A bucket of sewage.
     *
     * <p>Registered because a fluid without one is a fluid you can neither pick up nor place, and
     * {@code LiquidBlock.pickupBlock} hands out whatever the fluid names as its bucket regardless.
     * Stacks to one, like every other bucket.
     */
    public static final DeferredItem<BucketItem> SEWAGE_BUCKET = ITEMS.register("sewage_bucket",
            () -> new BucketItem(ModFluids.SEWAGE.get(),
                    new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));

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

    /**
     * A rocket stage, and a block of enriched uranium: the two things you make on the way.
     *
     * <p>They exist because the price of a missile does not fit in a crafting grid. Twenty-four
     * blocks of iron, sixty-four TNT and a block of redstone is thirty-odd stacks, and nine slots
     * is nine slots — so the cost is paid in eight stages and six cores, which comes out at the
     * same total and makes building one a small project rather than a single click.
     */
    public static final DeferredItem<Item> MISSILE_STAGE =
            ITEMS.register("missile_stage", () -> new Item(new Item.Properties()));

    public static final DeferredItem<Item> URANIUM_CORE =
            ITEMS.register("uranium_core", () -> new Item(new Item.Properties()));

    public static final DeferredItem<BlockItem> BALLISTIC_MISSILE =
            ITEMS.registerSimpleBlockItem("ballistic_missile", ModBlocks.BALLISTIC_MISSILE);

    public static final DeferredItem<BlockItem> NUCLEAR_MISSILE =
            ITEMS.registerSimpleBlockItem("nuclear_missile", ModBlocks.NUCLEAR_MISSILE);

    public static final DeferredItem<BlockItem> INTERCEPTOR_MISSILE =
            ITEMS.registerSimpleBlockItem("interceptor_missile", ModBlocks.INTERCEPTOR_MISSILE);

    public static final DeferredItem<BlockItem> SIREN =
            ITEMS.registerSimpleBlockItem("siren", ModBlocks.SIREN);

    public static final DeferredItem<BlockItem> HOLOGRAM_MAP =
            ITEMS.registerSimpleBlockItem("hologram_map", ModBlocks.HOLOGRAM_MAP);

    public static final DeferredItem<BlockItem> MEETING_BUTTON =
            ITEMS.registerSimpleBlockItem("meeting_button", ModBlocks.MEETING_BUTTON);

    public static final DeferredItem<BlockItem> HUSH_BUTTON =
            ITEMS.registerSimpleBlockItem("hush_button", ModBlocks.HUSH_BUTTON);

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
