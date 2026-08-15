package com.branciho.livingcities.client.render;

import com.branciho.livingcities.LivingCities;
import com.branciho.livingcities.entity.CitizenEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * Renders representative citizens.
 *
 * <p>Reuses the vanilla humanoid model rather than shipping a bespoke one: crowds are the point, and
 * a familiar silhouette reads as "a person" instantly. Variety comes from swapping the texture based
 * on a synced variant index, which costs nothing per frame.
 */
public class CitizenRenderer extends HumanoidMobRenderer<CitizenEntity, HumanoidModel<CitizenEntity>> {

    private static final ResourceLocation[] TEXTURES = {
            LivingCities.id("textures/entity/citizen/citizen_0.png"),
            LivingCities.id("textures/entity/citizen/citizen_1.png"),
            LivingCities.id("textures/entity/citizen/citizen_2.png"),
            LivingCities.id("textures/entity/citizen/citizen_3.png"),
    };

    public CitizenRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(CitizenEntity entity) {
        return TEXTURES[Math.floorMod(entity.variant(), TEXTURES.length)];
    }
}
