package com.branciho.citiesinlife.registry;

import com.branciho.citiesinlife.CitiesInLife;
import com.branciho.citiesinlife.blockentity.BoilerBlockEntity;
import com.branciho.citiesinlife.blockentity.FactoryOutputBlockEntity;
import com.branciho.citiesinlife.blockentity.TurbineBlockEntity;
import com.branciho.citiesinlife.blockentity.ChimneyBlockEntity;
import com.branciho.citiesinlife.blockentity.WaterStorageBlockEntity;
import com.branciho.citiesinlife.blockentity.WindmillBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CitiesInLife.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FactoryOutputBlockEntity>>
            FACTORY_OUTPUT = BLOCK_ENTITIES.register("factory_output",
                    () -> BlockEntityType.Builder
                            .of(FactoryOutputBlockEntity::new, ModBlocks.FACTORY_OUTPUT.get())
                            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BoilerBlockEntity>>
            BOILER = BLOCK_ENTITIES.register("boiler",
                    () -> BlockEntityType.Builder
                            .of(BoilerBlockEntity::new, ModBlocks.BOILER.get())
                            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TurbineBlockEntity>>
            TURBINE = BLOCK_ENTITIES.register("turbine",
                    () -> BlockEntityType.Builder
                            .of(TurbineBlockEntity::new, ModBlocks.TURBINE.get())
                            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WaterStorageBlockEntity>>
            WATER_STORAGE = BLOCK_ENTITIES.register("water_storage",
                    () -> BlockEntityType.Builder
                            .of(WaterStorageBlockEntity::new, ModBlocks.WATER_STORAGE.get())
                            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WindmillBlockEntity>>
            WINDMILL = BLOCK_ENTITIES.register("windmill",
                    () -> BlockEntityType.Builder
                            .of(WindmillBlockEntity::new,
                                    ModBlocks.WINDMILL_WHITE.get(), ModBlocks.WINDMILL_BLACK.get(),
                                    ModBlocks.WINDMILL_BLUE.get(), ModBlocks.WINDMILL_GREEN.get())
                            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ChimneyBlockEntity>>
            CHIMNEY = BLOCK_ENTITIES.register("chimney",
                    () -> BlockEntityType.Builder
                            .of(ChimneyBlockEntity::new, ModBlocks.CHIMNEY.get())
                            .build(null));

    private ModBlockEntities() {
    }

    public static void register(IEventBus modBus) {
        BLOCK_ENTITIES.register(modBus);
    }
}
