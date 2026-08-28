package com.branciho.citiesinlife.client.render;

import com.branciho.citiesinlife.CitiesInLife;
import com.branciho.citiesinlife.entity.MissileEntity;
import com.branciho.citiesinlife.missile.MissileKind;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Draws the rocket in the air, leaning into its own arc.
 *
 * <p>Same mesh as the one on the pad, pitched over. The pitch is the thing worth getting right: a
 * missile that flies a curve while pointing at the horizon reads as a model being dragged along a
 * line, and the whole reason the flight takes forty seconds is so that people look at it.
 */
public class MissileEntityRenderer extends EntityRenderer<MissileEntity> {

    private static final ResourceLocation[] TEXTURES = textures();

    private static ResourceLocation[] textures() {
        MissileKind[] kinds = MissileKind.values();
        ResourceLocation[] paths = new ResourceLocation[kinds.length];
        for (int i = 0; i < kinds.length; i++) {
            paths[i] = CitiesInLife.id("textures/entity/missile/" + kinds[i].id() + ".png");
        }
        return paths;
    }

    private final ModelPart root;

    public MissileEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.root = context.bakeLayer(MissileModel.LAYER);
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(MissileEntity missile, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        super.render(missile, entityYaw, partialTick, poseStack, buffer, packedLight);
        MissileKind kind = missile.kind();

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-entityYaw));
        // Nose along the flight path. The entity's pitch is measured from the horizontal and the
        // rocket is authored standing up, so ninety degrees of it is simply "upright".
        float pitch = Mth.lerp(partialTick, missile.xRotO, missile.getXRot());
        poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F - pitch));
        poseStack.scale(kind.scale(), -kind.scale(), -kind.scale());

        root.render(poseStack,
                buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURES[kind.ordinal()])),
                packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(MissileEntity missile) {
        return TEXTURES[missile.kind().ordinal()];
    }
}
