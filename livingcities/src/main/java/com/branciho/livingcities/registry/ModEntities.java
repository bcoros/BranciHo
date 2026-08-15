package com.branciho.livingcities.registry;

import com.branciho.livingcities.LivingCities;
import com.branciho.livingcities.entity.CitizenEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

@EventBusSubscriber(modid = LivingCities.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class ModEntities {

    private ModEntities() {
    }

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, LivingCities.MOD_ID);

    public static final Supplier<EntityType<CitizenEntity>> CITIZEN = ENTITY_TYPES.register("citizen", () ->
            EntityType.Builder.of(CitizenEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F)
                    .eyeHeight(1.62F)
                    .clientTrackingRange(8)
                    .updateInterval(3)
                    .build("citizen"));

    public static void register(IEventBus bus) {
        ENTITY_TYPES.register(bus);
    }

    @SubscribeEvent
    public static void onCreateAttributes(EntityAttributeCreationEvent event) {
        event.put(CITIZEN.get(), CitizenEntity.createAttributes().build());
    }
}
