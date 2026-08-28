package com.branciho.citiesinlife.registry;

import com.branciho.citiesinlife.CitiesInLife;
import com.branciho.citiesinlife.blockentity.CityFlagBlockEntity;
import com.branciho.citiesinlife.blockentity.AlarmBlockEntity;
import com.branciho.citiesinlife.blockentity.BoilerBlockEntity;
import com.branciho.citiesinlife.blockentity.EndPipeBlockEntity;
import com.branciho.citiesinlife.blockentity.FactoryOutputBlockEntity;
import com.branciho.citiesinlife.blockentity.TouristAirplaneBlockEntity;
import com.branciho.citiesinlife.blockentity.TransportAirplaneBlockEntity;
import com.branciho.citiesinlife.blockentity.TurbineBlockEntity;
import com.branciho.citiesinlife.blockentity.ChimneyBlockEntity;
import com.branciho.citiesinlife.blockentity.OfficeSpaceBlockEntity;
import com.branciho.citiesinlife.blockentity.RegisterCounterBlockEntity;
import com.branciho.citiesinlife.blockentity.ServiceSpawnerBlockEntity;
import com.branciho.citiesinlife.blockentity.CoolingPortBlockEntity;
import com.branciho.citiesinlife.blockentity.ReactorLeverBlockEntity;
import com.branciho.citiesinlife.blockentity.SewageCollectorBlockEntity;
import com.branciho.citiesinlife.blockentity.SteamEmitterBlockEntity;
import com.branciho.citiesinlife.blockentity.UraniumStorageBlockEntity;
import com.branciho.citiesinlife.blockentity.WaterStorageBlockEntity;
import com.branciho.citiesinlife.blockentity.WindmillBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import com.branciho.citiesinlife.blockentity.MissileBlockEntity;

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
                            .of(TurbineBlockEntity::new, ModBlocks.TURBINE.get(),
                                    ModBlocks.NUCLEAR_TURBINE.get())
                            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CityFlagBlockEntity>>
            CITY_FLAG = BLOCK_ENTITIES.register("city_flag",
                    () -> BlockEntityType.Builder
                            .of(CityFlagBlockEntity::new, ModBlocks.CITY_FLAG.get())
                            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WaterStorageBlockEntity>>
            WATER_STORAGE = BLOCK_ENTITIES.register("water_storage",
                    () -> BlockEntityType.Builder
                            .of(WaterStorageBlockEntity::new, ModBlocks.WATER_STORAGE.get())
                            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SewageCollectorBlockEntity>>
            SEWAGE_COLLECTOR = BLOCK_ENTITIES.register("sewage_collector",
                    () -> BlockEntityType.Builder
                            .of(SewageCollectorBlockEntity::new, ModBlocks.SEWAGE_COLLECTOR.get())
                            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<UraniumStorageBlockEntity>>
            URANIUM_STORAGE = BLOCK_ENTITIES.register("uranium_storage",
                    () -> BlockEntityType.Builder
                            .of(UraniumStorageBlockEntity::new, ModBlocks.URANIUM_STORAGE.get())
                            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CoolingPortBlockEntity>>
            COOLING_PORT = BLOCK_ENTITIES.register("cooling_port",
                    () -> BlockEntityType.Builder
                            .of(CoolingPortBlockEntity::new,
                                    ModBlocks.INPUT_WATER_PORT.get(),
                                    ModBlocks.OUTPUT_COOLED_PORT.get(),
                                    ModBlocks.INPUT_COOLED_PORT.get(),
                                    ModBlocks.OUTPUT_HEATED_PORT.get())
                            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SteamEmitterBlockEntity>>
            STEAM_EMITTER = BLOCK_ENTITIES.register("steam_emitter",
                    () -> BlockEntityType.Builder
                            .of(SteamEmitterBlockEntity::new, ModBlocks.STEAM_EMITTER.get())
                            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ReactorLeverBlockEntity>>
            REACTOR_LEVER = BLOCK_ENTITIES.register("reactor_lever",
                    () -> BlockEntityType.Builder
                            .of(ReactorLeverBlockEntity::new,
                                    ModBlocks.COOLER_LEVER.get(), ModBlocks.HEAT_LEVER.get(),
                                    ModBlocks.PRESSURE_LEVER.get(), ModBlocks.TURBINE_POWER.get())
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

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<OfficeSpaceBlockEntity>>
            OFFICE_SPACE = BLOCK_ENTITIES.register("office_space",
                    () -> BlockEntityType.Builder
                            .of(OfficeSpaceBlockEntity::new, ModBlocks.OFFICE_SPACE.get())
                            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RegisterCounterBlockEntity>>
            REGISTER_COUNTER = BLOCK_ENTITIES.register("register_counter",
                    () -> BlockEntityType.Builder
                            .of(RegisterCounterBlockEntity::new, ModBlocks.REGISTER_COUNTER.get())
                            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AlarmBlockEntity>>
            ALARM = BLOCK_ENTITIES.register("alarm",
                    () -> BlockEntityType.Builder
                            .of(AlarmBlockEntity::new, ModBlocks.ALARM.get())
                            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EndPipeBlockEntity>>
            END_PIPE = BLOCK_ENTITIES.register("end_pipe",
                    () -> BlockEntityType.Builder
                            .of(EndPipeBlockEntity::new, ModBlocks.END_PIPE.get())
                            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ServiceSpawnerBlockEntity>>
            SERVICE_SPAWNER = BLOCK_ENTITIES.register("service_spawner",
                    () -> BlockEntityType.Builder
                            .of(ServiceSpawnerBlockEntity::new, ModBlocks.SERVICE_SPAWNER.get())
                            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TouristAirplaneBlockEntity>>
            TOURIST_AIRPLANE = BLOCK_ENTITIES.register("tourist_airplane",
                    () -> BlockEntityType.Builder
                            .of(TouristAirplaneBlockEntity::new, ModBlocks.TOURIST_AIRPLANE.get())
                            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TransportAirplaneBlockEntity>>
            TRANSPORT_AIRPLANE = BLOCK_ENTITIES.register("transport_airplane",
                    () -> BlockEntityType.Builder
                            .of(TransportAirplaneBlockEntity::new, ModBlocks.TRANSPORT_AIRPLANE.get())
                            .build(null));

    private ModBlockEntities() {
    }

    /**
     * One type for all three rockets.
     *
     * <p>They differ by texture and scale, both of which the renderer reads off the block, so
     * three block entity types would be three registrations to hold the same nothing.
     */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MissileBlockEntity>>
            MISSILE = BLOCK_ENTITIES.register("missile",
                    () -> BlockEntityType.Builder
                            .of(MissileBlockEntity::new,
                                    ModBlocks.BALLISTIC_MISSILE.get(),
                                    ModBlocks.NUCLEAR_MISSILE.get(),
                                    ModBlocks.INTERCEPTOR_MISSILE.get())
                            .build(null));

    public static void register(IEventBus modBus) {
        BLOCK_ENTITIES.register(modBus);
    }
}
