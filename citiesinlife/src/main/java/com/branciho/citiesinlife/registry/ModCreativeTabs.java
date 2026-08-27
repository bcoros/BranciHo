package com.branciho.citiesinlife.registry;

import com.branciho.citiesinlife.CitiesInLife;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CitiesInLife.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + CitiesInLife.MOD_ID))
                    .icon(() -> new ItemStack(ModItems.PLANNER_WAND.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.PLANNER_WAND.get());
                        output.accept(ModItems.PATH_TOOL.get());
                        output.accept(ModItems.ROAD_TOOL.get());
                        output.accept(ModItems.POWER_LINE_TOOL.get());
                        output.accept(ModItems.SOLAR_PANEL.get());
                        output.accept(ModItems.POWER_MAST.get());
                        output.accept(ModItems.BOILER.get());
                        output.accept(ModItems.TURBINE.get());
                        output.accept(ModItems.CHIMNEY.get());
                        output.accept(ModItems.WINDMILL_WHITE.get());
                        output.accept(ModItems.WINDMILL_BLACK.get());
                        output.accept(ModItems.WINDMILL_BLUE.get());
                        output.accept(ModItems.WINDMILL_GREEN.get());
                        output.accept(ModItems.REPAIR_TOOL.get());
                        output.accept(ModItems.EXTINGUISHER.get());
                        output.accept(ModItems.ALARM.get());
                        output.accept(ModItems.TRANSIT_STATION.get());
                        output.accept(ModItems.FACTORY_OUTPUT.get());
                        output.accept(ModItems.PIPE_LINE_TOOL.get());
                        output.accept(ModItems.STARTER_PUMP.get());
                        output.accept(ModItems.PUMP.get());
                        output.accept(ModItems.END_PUMP.get());
                        output.accept(ModItems.WATER_PIPE.get());
                        output.accept(ModItems.INDESTRUCTIBLE_PIPE.get());
                        output.accept(ModItems.PIPE_CONNECTOR.get());
                        output.accept(ModItems.VALVE.get());
                        output.accept(ModItems.END_PIPE.get());
                        output.accept(ModItems.WATER_STORAGE.get());
                        output.accept(ModItems.OFFICE_SPACE.get());
                        output.accept(ModItems.REGISTER_COUNTER.get());
                        output.accept(ModItems.SERVICE_SPAWNER.get());
                        output.accept(ModItems.MILITARY_TOOL.get());
                        output.accept(ModItems.WAR_PLANNER_WAND.get());
                        output.accept(ModItems.SERVICE_RIFLE.get());
                    })
                    .build());

    private ModCreativeTabs() {
    }

    public static void register(IEventBus modBus) {
        TABS.register(modBus);
    }
}
