package com.branciho.livingcities.registry;

import com.branciho.livingcities.LivingCities;
import net.minecraft.world.item.BlockItem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Every item this mod adds, including the block items. */
public final class ModItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(LivingCities.MOD_ID);

    // registerSimpleBlockItem returns DeferredItem<BlockItem>, not DeferredItem<Item>. Declaring it
    // as the latter compiles locally against some IDE indexes and then fails the real build.
    public static final DeferredItem<BlockItem> CITY_HALL_CORE =
            ITEMS.registerSimpleBlockItem("city_hall_core", ModBlocks.CITY_HALL_CORE);

    private ModItems() {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
