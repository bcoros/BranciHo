package com.branciho.livingcities;

import com.branciho.livingcities.registry.ModBlocks;
import com.branciho.livingcities.registry.ModItems;
import com.mojang.logging.LogUtils;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import org.slf4j.Logger;

@Mod(LivingCities.MOD_ID)
public final class LivingCities {
    public static final String MOD_ID = "livingcities";
    public static final Logger LOGGER = LogUtils.getLogger();

    public LivingCities(IEventBus modBus, ModContainer container) {
        ModBlocks.register(modBus);
        ModItems.register(modBus);
        modBus.addListener(this::addCreativeItems);
        LOGGER.info("Living Cities Alpha 1 loading");
    }

    private void addCreativeItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(ModItems.CITY_HALL_CORE_ITEM);
        }
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ModItems.CITY_PLANNER_TOOL);
        }
    }
}
