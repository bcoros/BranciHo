package com.branciho.citiesinlife.registry;

import com.branciho.citiesinlife.CitiesInLife;
import com.branciho.citiesinlife.blockentity.FactoryOutputBlockEntity;
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

    private ModBlockEntities() {
    }

    public static void register(IEventBus modBus) {
        BLOCK_ENTITIES.register(modBus);
    }
}
