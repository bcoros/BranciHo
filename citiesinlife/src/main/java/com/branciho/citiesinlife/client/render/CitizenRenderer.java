package com.branciho.citiesinlife.client.render;

import com.branciho.citiesinlife.CitiesInLife;
import com.branciho.citiesinlife.entity.CitizenEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * Drawing a citizen.
 *
 * <p>Four faces, picked at random when somebody is spawned, so a street is not fifteen copies of the
 * same person. It costs four small textures and it is the difference between a city and a chorus
 * line.
 */
public class CitizenRenderer extends HumanoidMobRenderer<CitizenEntity, CitizenModel> {

    private static final ResourceLocation[] SKINS = new ResourceLocation[CitizenEntity.SKINS];

    static {
        for (int i = 0; i < CitizenEntity.SKINS; i++) {
            SKINS[i] = CitiesInLife.id("textures/entity/citizen/citizen_" + i + ".png");
        }
    }

    /** How far a seated citizen drops, so the bent legs land on a chair rather than on stilts. */
    private static final float SEAT_DROP = 0.27F;

    public CitizenRenderer(EntityRendererProvider.Context context) {
        super(context, new CitizenModel(context.bakeLayer(CitizenModel.LAYER)), 0.5F);
    }

    /**
     * Lower a seated citizen onto the chair.
     *
     * <p>Bending the legs alone leaves somebody sitting on thin air at standing height. This runs in
     * the flipped space the model is drawn in, where a positive Y moves the body down.
     */
    @Override
    protected void scale(CitizenEntity entity, PoseStack poseStack, float partialTick) {
        super.scale(entity, poseStack, partialTick);
        if (entity.seated()) {
            poseStack.translate(0.0F, SEAT_DROP, 0.0F);
        }
    }

    @Override
    public ResourceLocation getTextureLocation(CitizenEntity entity) {
        return SKINS[Math.floorMod(entity.skin(), SKINS.length)];
    }
}
