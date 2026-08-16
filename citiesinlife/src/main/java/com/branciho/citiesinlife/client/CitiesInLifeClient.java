package com.branciho.citiesinlife.client;

import com.branciho.citiesinlife.CitiesInLife;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * Client-side setup.
 *
 * <p>Everything under this package is guarded by {@code Dist.CLIENT} and reached only from here, so a
 * dedicated server never loads a single client class.
 */
@EventBusSubscriber(modid = CitiesInLife.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CitiesInLifeClient {

    private CitiesInLifeClient() {
    }

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(KeyBindings.OPEN_CITY);
        event.register(KeyBindings.TOGGLE_STRUCTURE_MODE);
        event.register(KeyBindings.TYPE_PREVIOUS);
        event.register(KeyBindings.TYPE_NEXT);
        event.register(KeyBindings.TOGGLE_MEASURE_MODE);
    }

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        // Above the hotbar so the panel and the structure-mode banner sit over the world but under
        // any open screen.
        event.registerAbove(VanillaGuiLayers.HOTBAR, CitiesInLife.id("planner"), PlannerHud::render);
    }
}
