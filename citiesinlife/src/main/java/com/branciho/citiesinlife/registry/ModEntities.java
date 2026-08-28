package com.branciho.citiesinlife.registry;

import com.branciho.citiesinlife.CitiesInLife;
import com.branciho.citiesinlife.entity.CarEntity;
import com.branciho.citiesinlife.entity.CitizenEntity;
import com.branciho.citiesinlife.entity.ServiceEntity;
import com.branciho.citiesinlife.entity.TouristEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import com.branciho.citiesinlife.entity.MissileEntity;

/** The mod's entities, and the attributes the living ones need to exist at all. */
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

    /**
     * A citizen's car.
     *
     * <p>MISC and no attributes at all, because it is a plain Entity rather than a Mob - see
     * {@link CarEntity} for why. Putting it through {@code EntityAttributeCreationEvent} would be a
     * hard crash on startup, since that only accepts living things.
     *
     * <p>The hitbox is square in plan and the car is not: entity boxes cannot be rectangular, so
     * this is the width of the longer side. It is never collided with anyway - the car is neither
     * pickable nor pushable.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<CarEntity>> CAR =
            ENTITIES.register("car", () -> EntityType.Builder
                    .<CarEntity>of(CarEntity::new, MobCategory.MISC)
                    .sized(3.5F, 2.1F)
                    .clientTrackingRange(12)
                    .build("car"));

    /**
     * A visitor.
     *
     * <p>Its own type rather than a citizen with a flag, because CitizenDirector counts citizens
     * against the city's cap and a tourist must not take a resident's place.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<TouristEntity>> TOURIST =
            ENTITIES.register("tourist", () -> EntityType.Builder
                    .<TouristEntity>of(TouristEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.95F)
                    .eyeHeight(1.74F)
                    .clientTrackingRange(10)
                    .build("tourist"));

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
    /**
     * A missile in the air.
     *
     * <p>A plain Entity like the car, so it must NOT appear in createAttributes below — that event
     * accepts living things only and a plain entity there is a hard startup crash.
     *
     * <p>The tracking range is the largest in the mod on purpose. A warhead crossing the sky is
     * meant to be seen from a long way off by people who have nothing to do with the war.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<MissileEntity>> MISSILE =
            ENTITIES.register("missile", () -> EntityType.Builder
                    .<MissileEntity>of(MissileEntity::new, MobCategory.MISC)
                    .sized(2.0F, 10.0F)
                    .clientTrackingRange(32)
                    .updateInterval(1)
                    .build("missile"));

    @SubscribeEvent
    public static void createAttributes(EntityAttributeCreationEvent event) {
        event.put(CITIZEN.get(), CitizenEntity.createAttributes().build());
        event.put(SERVICE.get(), ServiceEntity.createAttributes().build());
        // No line for CAR: it is a plain Entity, and this event accepts living things only.
        event.put(TOURIST.get(), TouristEntity.createAttributes().build());
    }
}
