package com.branciho.livingcities.registry;

import com.branciho.livingcities.LivingCities;
import com.branciho.livingcities.blockentity.CityHallCoreBlockEntity;
import com.branciho.livingcities.blockentity.CoalGeneratorBlockEntity;
import com.branciho.livingcities.blockentity.PumpingStationBlockEntity;
import com.branciho.livingcities.blockentity.SubstationBlockEntity;
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

    public static final Supplier<BlockEntityType<SubstationBlockEntity>> SUBSTATION =
            BLOCK_ENTITIES.register("substation", () ->
                    BlockEntityType.Builder.of(SubstationBlockEntity::new, ModBlocks.SUBSTATION.get()).build(null));

    public static final Supplier<BlockEntityType<CoalGeneratorBlockEntity>> COAL_GENERATOR =
            BLOCK_ENTITIES.register("coal_generator", () ->
                    BlockEntityType.Builder.of(CoalGeneratorBlockEntity::new, ModBlocks.COAL_GENERATOR.get()).build(null));

    public static final Supplier<BlockEntityType<PumpingStationBlockEntity>> PUMPING_STATION =
            BLOCK_ENTITIES.register("pumping_station", () ->
                    BlockEntityType.Builder.of(PumpingStationBlockEntity::new, ModBlocks.PUMPING_STATION.get()).build(null));

    public static void register(IEventBus bus) {
        BLOCK_ENTITIES.register(bus);
    }
}
