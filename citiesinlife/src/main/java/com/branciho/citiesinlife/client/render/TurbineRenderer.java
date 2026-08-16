package com.branciho.citiesinlife.client.render;

import com.branciho.citiesinlife.CitiesInLife;
import com.branciho.citiesinlife.block.TurbineBlock;
import com.branciho.citiesinlife.blockentity.TurbineBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Draws the turbine and turns its rotor.
 *
 * <p>The block itself renders a small steel pad; everything with any size to it is drawn from here,
 * because a machine three blocks wide with a moving part is beyond what a block model can express.
 */
public class TurbineRenderer implements BlockEntityRenderer<TurbineBlockEntity> {

    private static final ResourceLocation TEXTURE = CitiesInLife.id("textures/block/turbine.png");

    private final ModelPart root;
    private final ModelPart rotor;

    public TurbineRenderer(BlockEntityRendererProvider.Context context) {
        this.root = context.bakeLayer(TurbineModel.LAYER);
        this.rotor = root.getChild(TurbineModel.ROTOR);
    }

    @Override
    public void render(TurbineBlockEntity turbine, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        BlockState state = turbine.getBlockState();
        float yaw = state.hasProperty(TurbineBlock.FACING)
                ? state.getValue(TurbineBlock.FACING).toYRot()
                : 0.0F;

        poseStack.pushPose();
        // Middle of the block's bottom face, turned to match the way it was placed.
        poseStack.translate(0.5D, 0.0D, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(-yaw));
        // Model geometry is authored with Y downwards; this puts it the right way up.
        poseStack.scale(1.0F, -1.0F, -1.0F);

        rotor.xRot = (float) Math.toRadians(turbine.spin(partialTick));

        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        root.render(poseStack, consumer, packedLight, packedOverlay);
        poseStack.popPose();
    }

    /**
     * Keep drawing it from further away than a normal block entity.
     *
     * <p>It is the size of a small building and it is the thing a player walks over to look at, so
     * having it pop out of existence at sixteen blocks would be absurd.
     */
    @Override
    public int getViewDistance() {
        return 96;
    }

    /**
     * Draw it even when its own block position is off screen.
     *
     * <p>The machine is two blocks taller and one block wider than the position it is anchored to, so
     * culling on that single block makes the whole turbine blink out whenever you stand close enough
     * to look at it — which is exactly when you want to see it.
     */
    @Override
    public boolean shouldRenderOffScreen(TurbineBlockEntity turbine) {
        return true;
    }
}
