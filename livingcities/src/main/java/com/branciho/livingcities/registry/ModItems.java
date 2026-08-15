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

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}
