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
 * <p>The housing and its two dark lamps are an ordinary block model. Only the moving half and the
 * one lit lamp are drawn here, because a five-position throw baked as five models would be five
 * poses it jumps between — and the quarter-second it takes to get there is the whole of what makes
 * a lever feel like a lever.
 *
 * <p>Everything here is positioned in <b>block-model units</b>, the same 0..16 space the housing's
 * JSON uses, so the two cannot drift apart. That is not fussiness: the first version derived its
 * facing from {@link Direction#toYRot()}, which calls north 180°, while the blockstate calls north
 * 0° — so the arm was rendered on the opposite side of the block from its own housing, floating in
 * mid-air a block away. The rotation below is now the blockstate's own number, and the two agree by
 * construction rather than by coincidence.
 */
public class LeverRenderer implements BlockEntityRenderer<ReactorLeverBlockEntity> {

    private static final ResourceLocation ARM = CitiesInLife.id("textures/block/lever_arm.png");
    private static final ResourceLocation LAMP_GREEN =
            CitiesInLife.id("textures/block/lever_lamp_green.png");
    private static final ResourceLocation LAMP_RED =
            CitiesInLife.id("textures/block/lever_lamp_red.png");

    /** Straight up at position 0, straight down at 4: the full sweep across the face of the plate. */
    private static final float ANGLE_OFF = -52.0F;
    private static final float ANGLE_ON = 52.0F;

    // ---- block-model coordinates, read straight off reactor_lever.json --------------------
    /** The plate's front face. The arm pivots just proud of it. */
    private static final float PLATE_FACE = 11.0F;

    /** Where the two lamps sit in the recess: green above, red below. */
    private static final float LAMP_GREEN_Y = 10.0F;
    private static final float LAMP_RED_Y = 6.5F;

    /** Clear of the dark lamp's face at 11.0, so the lit one sits over it without z-fighting. */
    private static final float LAMP_Z = 10.6F;

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

        float progress = lever.swingProgress(now, partialTick);
        float angle = Mth.lerp(smooth(progress), angleFor(lever.from()), angleFor(position));

        // ---- the arm --------------------------------------------------------------------
        poseStack.pushPose();
        orient(poseStack, state);
        // Out to the face of the plate, then swing about that line. Geometry is authored with Y
        // downwards, as everything hand-built in this mod is, so the flip above makes -Y up.
        poseStack.translate(0.0D, 0.0D, -(PLATE_FACE - 8.0F) / 16.0D);
        poseStack.mulPose(Axis.XP.rotationDegrees(angle));
        VertexConsumer arm = buffer.getBuffer(RenderType.entityCutoutNoCull(ARM));
        pivot.render(poseStack, arm, packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();

        // ---- whichever lamp is lit -------------------------------------------------------
        boolean on = position > 0;
        poseStack.pushPose();
        orient(poseStack, state);
        poseStack.translate(0.0D,
                -((on ? LAMP_GREEN_Y : LAMP_RED_Y) - 8.0F) / 16.0D,
                -(LAMP_Z - 8.0F) / 16.0D);
        VertexConsumer glass = buffer.getBuffer(
                RenderType.entityCutoutNoCull(on ? LAMP_GREEN : LAMP_RED));
        // Full bright rather than the ambient value: an indicator lamp that goes dim in a dark
        // reactor hall is not an indicator lamp.
        lamp.render(poseStack, glass, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }

    /**
     * Put the pose stack where the block model is.
     *
     * <p>The rotation is the blockstate's own y value, not anything derived from the Direction
     * enum, because those two disagree by 180° and the arm belongs to the housing.
     */
    private static void orient(PoseStack poseStack, BlockState state) {
        poseStack.translate(0.5D, 0.5D, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(-blockstateRotation(state)));
        poseStack.scale(1.0F, -1.0F, -1.0F);
    }

    /** Exactly the y values reactor_lever.json's blockstate uses, in the same order. */
    private static float blockstateRotation(BlockState state) {
        return switch (state.getValue(ReactorLeverBlock.FACING)) {
            case EAST -> 90.0F;
            case SOUTH -> 180.0F;
            case WEST -> 270.0F;
            default -> 0.0F;
        };
    }

    private static float angleFor(int position) {
        return Mth.lerp(position / (float) ReactorLeverBlock.MAX_POSITION, ANGLE_OFF, ANGLE_ON);
    }

    /** Ease out, so the arm arrives against its stop rather than stopping dead in mid-air. */
    private static float smooth(float t) {
        return 1.0F - (1.0F - t) * (1.0F - t);
    }
}
