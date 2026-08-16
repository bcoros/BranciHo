package com.branciho.livingcities.registry;

import com.branciho.livingcities.LivingCities;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Every block this mod adds. */
public final class ModBlocks {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(LivingCities.MOD_ID);

    /**
     * The anchor of a city.
     *
     * <p>Currently an inert decorative block: it registers, it places, and it does nothing else.
     * Giving it behaviour is the first real piece of work.
     */
    public static final DeferredBlock<Block> CITY_HALL_CORE = BLOCKS.registerSimpleBlock(
            "city_hall_core",
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(3.0F, 9.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.STONE));

    private ModBlocks() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
    }
}
