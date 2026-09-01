package com.branciho.citiesinlife.client.render;

import com.branciho.citiesinlife.CitiesInLife;
import com.branciho.citiesinlife.entity.CitizenEntity;
import com.branciho.citiesinlife.entity.TouristEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * Drawing a visitor.
 *
 * <p>Reuses the four citizen skins rather than shipping four more. A tourist is meant to look like
 * an ordinary person, and inventing a uniform for "being on holiday" would say the opposite.
 */
public class TouristRenderer extends HumanoidMobRenderer<TouristEntity, TouristModel> {

    private static final ResourceLocation[] SKINS = new ResourceLocation[CitizenEntity.SKINS];

    static {
        for (int i = 0; i < CitizenEntity.SKINS; i++) {
            SKINS[i] = CitiesInLife.id("textures/entity/citizen/citizen_" + i + ".png");
        }
    }

    public TouristRenderer(EntityRendererProvider.Context context) {
        super(context, new TouristModel(context.bakeLayer(TouristModel.LAYER)), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(TouristEntity entity) {
        return SKINS[Math.floorMod(entity.skin(), SKINS.length)];
    }
}
