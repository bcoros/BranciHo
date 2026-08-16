package com.branciho.livingcities.registry;

import com.branciho.livingcities.LivingCities;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The mod's own creative tab.
 *
 * <p>Worth keeping even while there is only one item in it: an empty or missing tab is the fastest
 * way to not notice that registration silently failed.
 */
public final class ModCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, LivingCities.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + LivingCities.MOD_ID))
                    .icon(() -> new ItemStack(ModItems.CITY_HALL_CORE.get()))
                    .displayItems((parameters, output) -> output.accept(ModItems.CITY_HALL_CORE.get()))
                    .build());

    private ModCreativeTabs() {
    }

    public static void register(IEventBus modBus) {
        TABS.register(modBus);
    }
}
