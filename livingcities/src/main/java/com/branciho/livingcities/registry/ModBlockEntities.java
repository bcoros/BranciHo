package com.branciho.livingcities.registry;

import com.branciho.livingcities.LivingCities;
import com.branciho.livingcities.blockentity.CityHallCoreBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class ModBlockEntities {

    private ModBlockEntities() {
    }

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, LivingCities.MOD_ID);

    public static final Supplier<BlockEntityType<CityHallCoreBlockEntity>> CITY_HALL_CORE =
            BLOCK_ENTITIES.register("city_hall_core", () ->
                    BlockEntityType.Builder.of(CityHallCoreBlockEntity::new, ModBlocks.CITY_HALL_CORE.get()).build(null));

    public static void register(IEventBus bus) {
        BLOCK_ENTITIES.register(bus);
    }
}
