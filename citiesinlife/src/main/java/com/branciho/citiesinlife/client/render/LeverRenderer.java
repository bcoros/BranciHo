package com.branciho.citiesinlife.client.render;

import com.branciho.citiesinlife.CitiesInLife;
import com.branciho.citiesinlife.block.ReactorLeverBlock;
import com.branciho.citiesinlife.blockentity.ReactorLeverBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Draws the lever's arm, and swings it.
 *
 * <p>The housing and its two lamps are an ordinary block model. Only the moving half is drawn here,
 * because a five-position throw as five baked models would be five poses it jumps between — and the
 * thing that makes a lever feel like a lever is the quarter-second it takes to get there.
 *
 * <p>The lit lamp is drawn on top at full brightness rather than baked into the texture, so one
 * texture serves every position: green while the control is doing something, red while it is off,
 * exactly as in the reference.
 */
public class LeverRenderer implements BlockEntityRenderer<ReactorLeverBlockEntity> {

    private static final ResourceLocation ARM = CitiesInLife.id("textures/block/lever_arm.png");
    /** Which lamp is glowing. Two textures rather than one tinted quad: no raw vertex work. */
    private static final ResourceLocation LAMP_GREEN =
            CitiesInLife.id("textures/block/lever_lamp_green.png");
    private static final ResourceLocation LAMP_RED =
            CitiesInLife.id("textures/block/lever_lamp_red.png");

    /** Straight up at position 0, straight down at 4: the full sweep across the face of the plate. */
    private static final float ANGLE_OFF = -52.0F;
    private static final float ANGLE_ON = 52.0F;

    private final ModelPart pivot;
    private final ModelPart lamp;

    public LeverRenderer(BlockEntityRendererProvider.Context context) {
        ModelPart root = context.bakeLayer(LeverArmModel.LAYER);
        this.pivot = root.getChild(LeverArmModel.PIVOT);
        this.lamp = root.getChild(LeverArmModel.LAMP);
    }

    @Override
    public void render(ReactorLeverBlockEntity lever, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        BlockState state = lever.getBlockState();
        if (!state.hasProperty(ReactorLeverBlock.POSITION)) {
            return;
        }
        int position = state.getValue(ReactorLeverBlock.POSITION);
        long now = lever.getLevel() == null ? 0L : lever.getLevel().getGameTime();

        // Interpolate from where the arm was to where it now is. A throw that has finished, or a
        // lever loaded from disk, sits still at its destination.
        float progress = lever.swingProgress(now, partialTick);
        float from = angleFor(lever.from());
        float to = angleFor(position);
        float angle = Mth.lerp(smooth(progress), from, to);

        poseStack.pushPose();
        poseStack.translate(0.5D, 0.5D, 0.5D);
        // Turn to face the wall it is bolted to, then stand the geometry up: it is authored with Y
        // downwards, the same as every other hand-built model in this mod.
        Direction facing = state.getValue(ReactorLeverBlock.FACING);
        poseStack.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        poseStack.scale(1.0F, -1.0F, -1.0F);
        // Out to the face of the plate, so the arms travel in front of the lamps rather than
        // through them.
        poseStack.translate(0.0D, 0.0D, -0.30D);
        poseStack.mulPose(Axis.XP.rotationDegrees(angle));

        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(ARM));
        pivot.render(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();

        drawLamp(state, poseStack, buffer, position > 0);
    }

    /**
     * The live lamp, drawn at full brightness over whichever half of the housing is lit.
     *
     * <p>Full bright rather than the ambient light value, because an indicator lamp that goes dim
     * in a dark reactor hall is not an indicator lamp.
     */
    private void drawLamp(BlockState state, PoseStack poseStack, MultiBufferSource buffer,
                          boolean on) {
        poseStack.pushPose();
        poseStack.translate(0.5D, 0.5D, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(
                -state.getValue(ReactorLeverBlock.FACING).toYRot()));
        poseStack.scale(1.0F, -1.0F, -1.0F);
        // Green sits above red on the plate, so the lit one is a small vertical offset apart.
        poseStack.translate(0.0D, on ? -0.10D : 0.10D, 0.0D);

        VertexConsumer consumer = buffer.getBuffer(
                RenderType.entityCutoutNoCull(on ? LAMP_GREEN : LAMP_RED));
        lamp.render(poseStack, consumer, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }

    private static float angleFor(int position) {
        return Mth.lerp(position / (float) ReactorLeverBlock.MAX_POSITION, ANGLE_OFF, ANGLE_ON);
    }

    /** Ease out, so the arm arrives against its stop rather than stopping dead in mid-air. */
    private static float smooth(float t) {
        return 1.0F - (1.0F - t) * (1.0F - t);
    }
}
