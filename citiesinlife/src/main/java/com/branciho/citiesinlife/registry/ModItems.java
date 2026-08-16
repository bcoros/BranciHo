package com.branciho.citiesinlife.registry;

import com.branciho.citiesinlife.CitiesInLife;
import com.branciho.citiesinlife.item.PlannerWandItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Everything this mod adds. There is one item, and that is the point. */
public final class ModItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CitiesInLife.MOD_ID);

    public static final DeferredItem<PlannerWandItem> PLANNER_WAND = ITEMS.register("planner_wand",
            () -> new PlannerWandItem(new Item.Properties().stacksTo(1)));

    private ModItems() {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
