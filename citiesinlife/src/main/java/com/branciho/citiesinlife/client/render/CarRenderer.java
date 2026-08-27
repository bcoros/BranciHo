package com.branciho.citiesinlife.client.render;

import com.branciho.citiesinlife.CitiesInLife;
import com.branciho.citiesinlife.entity.CarEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws the car and turns its wheels.
 *
 * <p>A plain {@link EntityRenderer} rather than a MobRenderer, the way vanilla draws a boat. A
 * MobRenderer applies its own translate for a standing creature's feet, and a car is not standing
 * on anything.
 *
 * <p>The wheels are turned by distance covered rather than by time, so a car stopped at a junction
 * has stopped wheels. A wheel of radius three model units rolls its circumference in one revolution,
 * which is where the constant below comes from.
 */
public class CarRenderer extends EntityRenderer<CarEntity> {

    private static final ResourceLocation TEXTURE = CitiesInLife.id("textures/entity/car/car.png");

    /** Radians of wheel rotation per block travelled: one turn per 2*pi*r, with r = 3/16 of a block. */
    private static final float ROLL_PER_BLOCK = 1.0F / (3.0F / 16.0F);

    private final ModelPart root;
    private final ModelPart[] wheels;

    public CarRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.root = context.bakeLayer(CarModel.LAYER);
        this.wheels = new ModelPart[]{
                root.getChild(CarModel.WHEEL_FRONT_LEFT),
                root.getChild(CarModel.WHEEL_FRONT_RIGHT),
                root.getChild(CarModel.WHEEL_REAR_LEFT),
                root.getChild(CarModel.WHEEL_REAR_RIGHT)};
        this.shadowRadius = 1.0F;
    }

    @Override
    public void render(CarEntity car, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        super.render(car, entityYaw, partialTick, poseStack, buffer, packedLight);

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-entityYaw));
        // Geometry is authored with Y downwards; this puts it the right way up. No half-block
        // translate here - that is a block-entity correction and would slide the car sideways.
        poseStack.scale(1.0F, -1.0F, -1.0F);

        float roll = car.travelled(partialTick) * ROLL_PER_BLOCK;
        for (ModelPart wheel : wheels) {
            wheel.xRot = roll;
        }

        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        root.render(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(CarEntity entity) {
        return TEXTURE;
    }
}
