package com.branciho.citiesinlife.registry;

import com.branciho.citiesinlife.CitiesInLife;
import com.branciho.citiesinlife.menu.BoilerMenu;
import com.branciho.citiesinlife.menu.FactoryOutputMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, CitiesInLife.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<FactoryOutputMenu>> FACTORY_OUTPUT =
            MENUS.register("factory_output",
                    () -> new MenuType<>(FactoryOutputMenu::new, FeatureFlags.DEFAULT_FLAGS));

    public static final DeferredHolder<MenuType<?>, MenuType<BoilerMenu>> BOILER =
            MENUS.register("boiler",
                    () -> new MenuType<>(BoilerMenu::new, FeatureFlags.DEFAULT_FLAGS));

    private ModMenus() {
    }

    public static void register(IEventBus modBus) {
        MENUS.register(modBus);
    }
}
