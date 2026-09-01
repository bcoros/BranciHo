package com.branciho.citiesinlife;

import com.branciho.citiesinlife.config.CitiesInLifeClientConfig;
import com.branciho.citiesinlife.config.CitiesInLifeConfig;
import com.branciho.citiesinlife.registry.ModBlockEntities;
import com.branciho.citiesinlife.registry.ModBlocks;
import com.branciho.citiesinlife.registry.ModCreativeTabs;
import com.branciho.citiesinlife.registry.ModEntities;
import com.branciho.citiesinlife.registry.ModFluids;
import com.branciho.citiesinlife.registry.ModItems;
import com.branciho.citiesinlife.registry.ModMenus;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import com.branciho.citiesinlife.client.ClientConfigScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

/**
 * Cities In Life.
 *
 * <p>You build a structure by hand, draw a box around it with the Planner Wand, and say what it is.
 * The mod measures what is actually there and simulates it — population, jobs, money, territory.
 *
 * <p>It ships no prefab buildings and never will. Every other decision in here is downstream of that
 * one: the scanner asks blocks what they <em>do</em> rather than what they are, so a build made of
 * blocks nobody tested still measures correctly.
 *
 * <p>Nothing this class touches at load time may reference client-only types — a dedicated server
 * loads it. Client wiring lives under {@code client} behind a {@code Dist.CLIENT} guard.
 */
@Mod(CitiesInLife.MOD_ID)
public final class CitiesInLife {

    public static final String MOD_ID = "citiesinlife";

    public static final Logger LOGGER = LogUtils.getLogger();

    public CitiesInLife(IEventBus modBus, ModContainer container) {
        ModBlocks.register(modBus);
        ModItems.register(modBus);
        ModBlockEntities.register(modBus);
        ModMenus.register(modBus);
        ModFluids.register(modBus);
        ModEntities.register(modBus);
        ModCreativeTabs.register(modBus);

        // Server-side, because it decides what gets spawned rather than what gets drawn:
        // turning citizens down is a fact about the world, not about one player's view of it.
        container.registerConfig(ModConfig.Type.SERVER, CitiesInLifeConfig.SPEC);

        // Client-side, for the opposite reason: how loud a turbine is decides nothing about the
        // world, and a player on somebody else's server should not have to ask the host to turn
        // it down.
        container.registerConfig(ModConfig.Type.CLIENT, CitiesInLifeClientConfig.SPEC);

        // Put a Config button beside the mod in the Mods list. Behind a dist check and in its own
        // class, because the screen it points at is a client type: touching it from common code
        // would take a dedicated server down on startup.
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientConfigScreen.register(container);
        }

        LOGGER.info("Cities In Life {} initialising", container.getModInfo().getVersion());
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
