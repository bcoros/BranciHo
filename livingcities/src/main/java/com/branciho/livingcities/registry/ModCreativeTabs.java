package com.branciho.livingcities.registry;

import com.branciho.livingcities.LivingCities;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class ModCreativeTabs {

    private ModCreativeTabs() {
    }

    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, LivingCities.MOD_ID);

    public static final Supplier<CreativeModeTab> MAIN = CREATIVE_TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.livingcities"))
            .icon(() -> new ItemStack(ModItems.CITY_PLANNER_TOOL.get()))
            .displayItems((parameters, output) -> {
                output.accept(ModItems.CITY_PLANNER_TOOL.get());
                output.accept(ModItems.CITY_HALL_CORE_ITEM.get());
                output.accept(ModItems.ENTRANCE_MARKER_ITEM.get());
                output.accept(ModItems.PATH_NODE_ITEM.get());
                output.accept(ModItems.POWER_CABLE_ITEM.get());
                output.accept(ModItems.TRANSMISSION_PYLON_ITEM.get());
                output.accept(ModItems.SUBSTATION_ITEM.get());
                output.accept(ModItems.SOLAR_PANEL_ITEM.get());
                output.accept(ModItems.COAL_GENERATOR_ITEM.get());
                output.accept(ModItems.WIND_TURBINE_ITEM.get());
                output.accept(ModItems.TRANSFORMER_ITEM.get());
                output.accept(ModItems.WATER_PIPE_ITEM.get());
                output.accept(ModItems.WATER_PUMP_ITEM.get());
                output.accept(ModItems.WATER_TOWER_ITEM.get());
                output.accept(ModItems.PUMPING_STATION_ITEM.get());
            })
            .build());

    public static void register(IEventBus bus) {
        CREATIVE_TABS.register(bus);
    }
}
