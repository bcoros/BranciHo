package com.branciho.citiesinlife.registry;

import com.branciho.citiesinlife.CitiesInLife;
import com.branciho.citiesinlife.entity.CitizenEntity;
import com.branciho.citiesinlife.entity.ServiceEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The mod's one entity, and the attributes it needs to exist at all. */
@EventBusSubscriber(modid = CitiesInLife.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, CitiesInLife.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<CitizenEntity>> CITIZEN =
            ENTITIES.register("citizen", () -> EntityType.Builder
                    .<CitizenEntity>of(CitizenEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.95F)
                    .eyeHeight(1.74F)
                    .clientTrackingRange(10)
                    .build("citizen"));

    /**
     * Everybody in a uniform, of any service.
     *
     * <p>One type rather than five. The role is a byte on the entity, because a police officer and a
     * bin man differ only in which errand they are running - and five entity types would be five
     * registrations, five renderers and five places to fix the same walk.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<ServiceEntity>> SERVICE =
            ENTITIES.register("service", () -> EntityType.Builder
                    .<ServiceEntity>of(ServiceEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.95F)
                    .eyeHeight(1.74F)
                    .clientTrackingRange(10)
                    .build("service"));

    private ModEntities() {
    }

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
    }

    /**
     * Citizens are placed by the director, never by natural spawning, so they are MISC rather than
     * CREATURE: a category with a spawn cap would have the game quietly deciding how many people a
     * city may have, which is the player's decision and lives in the config.
     */
    @SubscribeEvent
    public static void createAttributes(EntityAttributeCreationEvent event) {
        event.put(CITIZEN.get(), CitizenEntity.createAttributes().build());
        event.put(SERVICE.get(), ServiceEntity.createAttributes().build());
    }
}
