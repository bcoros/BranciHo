package com.branciho.citiesinlife.client;

import com.branciho.citiesinlife.CitiesInLife;
import com.branciho.citiesinlife.registry.ModFluids;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/**
 * How sewage is drawn: vanilla's water, tinted.
 *
 * <p>Pointing at {@code minecraft:block/water_still} and {@code water_flow} rather than shipping
 * two textures of our own is the whole trick, and it is the right one. Those textures are animated
 * — thirty-two frames of still and sixty-four of flow, with their own .mcmeta — and water is drawn
 * greyscale precisely so a biome can tint it. Reusing them means sewage ripples and runs exactly
 * like water, in brown, for four lines and no art.
 *
 * <p>The fog is a shade darker than the fluid so that being under it reads as "you are in the
 * sewer" rather than as a brown pane over the camera.
 */
@EventBusSubscriber(modid = CitiesInLife.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientFluids {

    private static final ResourceLocation STILL =
            ResourceLocation.withDefaultNamespace("block/water_still");
    private static final ResourceLocation FLOW =
            ResourceLocation.withDefaultNamespace("block/water_flow");
    private static final ResourceLocation OVERLAY =
            ResourceLocation.withDefaultNamespace("block/water_overlay");

    /** Opaque murky brown. The alpha byte matters: water's own tint is fully opaque too. */
    private static final int TINT = 0xFF6B5227;

    private ClientFluids() {
    }

    @SubscribeEvent
    public static void registerClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerFluidType(new IClientFluidTypeExtensions() {
            @Override
            public ResourceLocation getStillTexture() {
                return STILL;
            }

            @Override
            public ResourceLocation getFlowingTexture() {
                return FLOW;
            }

            @Override
            public ResourceLocation getOverlayTexture() {
                return OVERLAY;
            }

            @Override
            public int getTintColor() {
                return TINT;
            }
        }, ModFluids.SEWAGE_TYPE.get());
    }
}
