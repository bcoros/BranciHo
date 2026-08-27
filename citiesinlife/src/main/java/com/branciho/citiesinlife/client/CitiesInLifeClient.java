package com.branciho.citiesinlife.client;

import com.branciho.citiesinlife.CitiesInLife;
import com.branciho.citiesinlife.client.render.CarModel;
import com.branciho.citiesinlife.client.render.CarRenderer;
import com.branciho.citiesinlife.client.render.CitizenModel;
import com.branciho.citiesinlife.client.render.CitizenRenderer;
import com.branciho.citiesinlife.client.render.ServiceModel;
import com.branciho.citiesinlife.client.render.ServiceRenderer;
import com.branciho.citiesinlife.client.render.TouristModel;
import com.branciho.citiesinlife.client.render.TouristRenderer;
import com.branciho.citiesinlife.client.render.TurbineModel;
import com.branciho.citiesinlife.client.render.WindmillModel;
import com.branciho.citiesinlife.client.render.WindmillRenderer;
import com.branciho.citiesinlife.client.render.TurbineRenderer;
import com.branciho.citiesinlife.client.screen.BoilerScreen;
import com.branciho.citiesinlife.client.screen.FactoryOutputScreen;
import com.branciho.citiesinlife.client.screen.RegisterCounterScreen;
import com.branciho.citiesinlife.registry.ModBlockEntities;
import com.branciho.citiesinlife.registry.ModEntities;
import com.branciho.citiesinlife.registry.ModMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
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
        event.register(KeyBindings.TOGGLE_CREATIVE_MONEY);
        event.register(KeyBindings.OPEN_ROAD_TOOL);
    }

    @SubscribeEvent
    public static void registerMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.FACTORY_OUTPUT.get(), FactoryOutputScreen::new);
        event.register(ModMenus.BOILER.get(), BoilerScreen::new);
        event.register(ModMenus.REGISTER_COUNTER.get(), RegisterCounterScreen::new);
    }

    @SubscribeEvent
    public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(TurbineModel.LAYER, TurbineModel::create);
        event.registerLayerDefinition(CarModel.LAYER, CarModel::create);
        event.registerLayerDefinition(WindmillModel.LAYER, WindmillModel::create);
        event.registerLayerDefinition(CitizenModel.LAYER, CitizenModel::createBodyLayer);
        event.registerLayerDefinition(ServiceModel.LAYER, ServiceModel::createBodyLayer);
        event.registerLayerDefinition(TouristModel.LAYER, TouristModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // The turbine is three blocks wide and its rotor turns, so none of it can come from a block
        // model. All of it is drawn here.
        event.registerBlockEntityRenderer(ModBlockEntities.TURBINE.get(), TurbineRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.WINDMILL.get(), WindmillRenderer::new);
        event.registerEntityRenderer(ModEntities.CITIZEN.get(), CitizenRenderer::new);
        event.registerEntityRenderer(ModEntities.SERVICE.get(), ServiceRenderer::new);
        event.registerEntityRenderer(ModEntities.CAR.get(), CarRenderer::new);
        event.registerEntityRenderer(ModEntities.TOURIST.get(), TouristRenderer::new);
    }

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        // Above the hotbar so the panel and the structure-mode banner sit over the world but under
        // any open screen.
        event.registerAbove(VanillaGuiLayers.HOTBAR, CitiesInLife.id("planner"), PlannerHud::render);
    }
}
