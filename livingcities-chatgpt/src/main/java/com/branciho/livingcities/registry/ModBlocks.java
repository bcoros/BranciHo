package com.branciho.livingcities.registry;

import com.branciho.livingcities.LivingCities;
import com.branciho.livingcities.block.CityHallCoreBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    private ModBlocks() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(LivingCities.MOD_ID);

    public static final DeferredBlock<CityHallCoreBlock> CITY_HALL_CORE = BLOCKS.registerBlock(
            "city_hall_core",
            CityHallCoreBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(4.0F, 1200.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops());

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
    }
}
