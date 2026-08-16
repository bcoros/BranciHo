package com.branciho.citiesinlife.registry;

import com.branciho.citiesinlife.CitiesInLife;
import com.branciho.citiesinlife.item.PlannerWandItem;
import com.branciho.citiesinlife.item.PowerLineToolItem;
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

    private ModItems() {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
