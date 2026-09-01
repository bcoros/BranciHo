package com.branciho.citiesinlife.client.render;

import com.branciho.citiesinlife.CitiesInLife;
import com.branciho.citiesinlife.blockentity.HologramMapBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;

/** Draws the projection table and turns its globe. */
public class HologramRenderer implements BlockEntityRenderer<HologramMapBlockEntity> {

    private static final ResourceLocation TEXTURE =
            CitiesInLife.id("textures/block/hologram_map.png");

    private final ModelPart root;
    private final ModelPart base;
    private final ModelPart globe;

    public HologramRenderer(BlockEntityRendererProvider.Context context) {
        this.root = context.bakeLayer(HologramModel.LAYER);
        this.base = root.getChild(HologramModel.BASE);
        this.globe = root.getChild(HologramModel.GLOBE);
    }

    @Override
    public void render(HologramMapBlockEntity table, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        poseStack.pushPose();
        poseStack.translate(0.5D, 0.0D, 0.5D);
        // Model geometry is authored with Y downwards; this puts it the right way up.
        poseStack.scale(1.0F, -1.0F, -1.0F);

        // The plinth is a solid object and is lit like one.
        globe.visible = false;
        base.render(poseStack, buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)),
                packedLight, packedOverlay);
        globe.visible = true;

        // The globe is light: full brightness, translucent, and turning. The bob is what stops it
        // reading as a solid ornament bolted to the top of the plinth.
        poseStack.pushPose();
        poseStack.translate(0.0D, -table.rise(), 0.0D);
        globe.yRot = (float) Math.toRadians(table.spin(partialTick));
        globe.xRot = (float) Math.toRadians(12.0D);
        VertexConsumer glow = buffer.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE));
        globe.render(poseStack, glow, LightTexture.FULL_BRIGHT, packedOverlay);
        poseStack.popPose();

        poseStack.popPose();
    }

    /** The globe floats above the block it stands on. */
    @Override
    public AABB getRenderBoundingBox(HologramMapBlockEntity table) {
        BlockPos pos = table.getBlockPos();
        return new AABB(pos).expandTowards(0.0D, 2.0D, 0.0D);
    }
}
