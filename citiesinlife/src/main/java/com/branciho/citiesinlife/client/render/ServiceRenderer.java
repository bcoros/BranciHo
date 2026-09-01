package com.branciho.citiesinlife.client.render;

import com.branciho.citiesinlife.CitiesInLife;
import com.branciho.citiesinlife.entity.ServiceEntity;
import com.branciho.citiesinlife.service.ServiceType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

import java.util.EnumMap;
import java.util.Map;

/**
 * Drawing somebody in a uniform.
 *
 * <p>The uniform is the whole of it. A player should be able to look down a street and know without
 * clicking anything that the person walking towards the plant is a firefighter and the one standing
 * over a body is a police officer — so each service gets a colour you can read at distance rather
 * than a badge you have to be next to.
 */
public class ServiceRenderer extends HumanoidMobRenderer<ServiceEntity, ServiceModel> {

    private static final Map<ServiceType, ResourceLocation> UNIFORMS = new EnumMap<>(ServiceType.class);

    /** What somebody in a service with no uniform of its own wears. Nothing should ever use it. */
    private static final ResourceLocation FALLBACK =
            CitiesInLife.id("textures/entity/service/police.png");

    static {
        UNIFORMS.put(ServiceType.POLICE, CitiesInLife.id("textures/entity/service/police.png"));
        UNIFORMS.put(ServiceType.FIRE, CitiesInLife.id("textures/entity/service/firefighter.png"));
        UNIFORMS.put(ServiceType.HOSPITAL, CitiesInLife.id("textures/entity/service/doctor.png"));
        UNIFORMS.put(ServiceType.GARBAGE, CitiesInLife.id("textures/entity/service/binman.png"));
        UNIFORMS.put(ServiceType.MILITARY, CitiesInLife.id("textures/entity/service/soldier.png"));
        UNIFORMS.put(ServiceType.CLERK, CitiesInLife.id("textures/entity/service/clerk.png"));
        UNIFORMS.put(ServiceType.BODYGUARD,
                CitiesInLife.id("textures/entity/service/bodyguard.png"));
    }

    public ServiceRenderer(EntityRendererProvider.Context context) {
        super(context, new ServiceModel(context.bakeLayer(ServiceModel.LAYER)), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(ServiceEntity entity) {
        return UNIFORMS.getOrDefault(entity.role(), FALLBACK);
    }
}
