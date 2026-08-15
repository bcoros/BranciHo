package com.branciho.livingcities.registry;

import com.branciho.livingcities.LivingCities;
import com.branciho.livingcities.item.SelectionData;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class ModDataComponents {

    private ModDataComponents() {
    }

    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, LivingCities.MOD_ID);

    /** Two-corner selection carried by the City Planner Tool. */
    public static final Supplier<DataComponentType<SelectionData>> SELECTION = DATA_COMPONENTS.register(
            "selection",
            () -> DataComponentType.<SelectionData>builder()
                    .persistent(SelectionData.CODEC)
                    .networkSynchronized(SelectionData.STREAM_CODEC)
                    .build());

    public static void register(IEventBus bus) {
        DATA_COMPONENTS.register(bus);
    }
}
