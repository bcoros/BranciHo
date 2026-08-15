package com.branciho.livingcities.client;

import com.branciho.livingcities.LivingCities;
import com.branciho.livingcities.client.render.CitizenRenderer;
import com.branciho.livingcities.registry.ModEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

/**
 * Client-only mod-bus wiring.
 *
 * <p>Guarded by {@code Dist.CLIENT} so none of it is loaded on a dedicated server.
 */
@EventBusSubscriber(modid = LivingCities.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class LivingCitiesClient {

    private LivingCitiesClient() {
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(KeyBindings.OPEN_MANAGEMENT);
        event.register(KeyBindings.TOGGLE_OVERLAY);
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.CITIZEN.get(), CitizenRenderer::new);
    }
}
