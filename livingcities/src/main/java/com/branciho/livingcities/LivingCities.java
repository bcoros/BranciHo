package com.branciho.livingcities;

import com.branciho.livingcities.config.LivingCitiesConfig;
import com.branciho.livingcities.registry.ModBlockEntities;
import com.branciho.livingcities.registry.ModBlocks;
import com.branciho.livingcities.registry.ModCreativeTabs;
import com.branciho.livingcities.registry.ModDataComponents;
import com.branciho.livingcities.registry.ModEntities;
import com.branciho.livingcities.registry.ModItems;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

/**
 * Mod entry point.
 *
 * <p>Living Cities never ships prefab architecture. The player builds whatever they want out of
 * whatever blocks they want, and then tells this mod what the build <em>is</em>. Everything here
 * exists to support that: selection tooling, a scanner that measures arbitrary geometry, and an
 * aggregate simulation that runs on the server.
 *
 * <p>Nothing in this class (or anything it touches at load time) may reference client-only types.
 * Client wiring lives in {@code com.branciho.livingcities.client} and is registered from
 * {@code LivingCitiesClient}, which is guarded by {@code Dist.CLIENT}.
 */
@Mod(LivingCities.MOD_ID)
public final class LivingCities {

    public static final String MOD_ID = "livingcities";

    public static final Logger LOGGER = LogUtils.getLogger();

    public LivingCities(IEventBus modBus, ModContainer container) {
        ModBlocks.register(modBus);
        ModItems.register(modBus);
        ModBlockEntities.register(modBus);
        ModEntities.register(modBus);
        ModDataComponents.register(modBus);
        ModCreativeTabs.register(modBus);

        container.registerConfig(ModConfig.Type.SERVER, LivingCitiesConfig.SERVER_SPEC);

        LOGGER.info("Living Cities {} initialising", container.getModInfo().getVersion());
    }

    /** Convenience for building a {@link ResourceLocation} in this mod's namespace. */
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
