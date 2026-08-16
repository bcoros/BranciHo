package com.branciho.livingcities;

import com.branciho.livingcities.registry.ModBlocks;
import com.branciho.livingcities.registry.ModCreativeTabs;
import com.branciho.livingcities.registry.ModItems;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Mod entry point.
 *
 * <p>This is a deliberate clean slate. The mod currently registers one block and nothing else — no
 * city system, no simulation, no scanner. That is the starting point, not an accident.
 *
 * <p>The design it is being rebuilt towards is in {@code docs/DESIGN-BRIEF.md}, and the constraints
 * that must not be traded away are in {@code AGENTS.md} at the repository root. Read both before
 * adding anything.
 *
 * <p>Nothing in this class, or anything it touches at load time, may reference client-only types.
 * A dedicated server loads this class and crashes if a {@code net.minecraft.client} type appears on
 * the path. Client wiring belongs in a {@code client} package guarded by {@code Dist.CLIENT}.
 */
@Mod(LivingCities.MOD_ID)
public final class LivingCities {

    public static final String MOD_ID = "livingcities";

    public static final Logger LOGGER = LogUtils.getLogger();

    public LivingCities(IEventBus modBus, ModContainer container) {
        ModBlocks.register(modBus);
        ModItems.register(modBus);
        ModCreativeTabs.register(modBus);

        LOGGER.info("Living Cities {} initialising", container.getModInfo().getVersion());
    }

    /** Convenience for building a {@link ResourceLocation} in this mod's namespace. */
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
