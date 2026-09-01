package com.branciho.citiesinlife.client.render;

import com.branciho.citiesinlife.CitiesInLife;
import com.branciho.citiesinlife.block.SirenBlock;
import com.branciho.citiesinlife.blockentity.SirenBlockEntity;
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

/** Draws the siren mast and turns its horns. */
public class SirenRenderer implements BlockEntityRenderer<SirenBlockEntity> {

    private static final ResourceLocation TEXTURE =
            CitiesInLife.id("textures/block/siren_mast.png");

    private final ModelPart root;
    private final ModelPart head;
    private final ModelPart lamp;

    public SirenRenderer(BlockEntityRendererProvider.Context context) {
        this.root = context.bakeLayer(SirenModel.LAYER);
        this.head = root.getChild(SirenModel.HEAD);
        this.lamp = root.getChild(SirenModel.LAMP);
    }

    @Override
    public void render(SirenBlockEntity siren, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        boolean wailing = SirenBlock.wailing(siren.getBlockState());

        poseStack.pushPose();
        poseStack.translate(0.5D, 0.0D, 0.5D);
        // Model geometry is authored with Y downwards; this puts it the right way up.
        poseStack.scale(1.0F, -1.0F, -1.0F);

        head.yRot = (float) Math.toRadians(siren.spin(partialTick));

        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        // The lamp is drawn separately so it can be lit on its own. Hiding it for the first pass
        // rather than accepting the overdraw, because two coincident faces at this distance
        // z-fight into a flicker that reads as a rendering bug rather than as a beacon.
        lamp.visible = false;
        root.render(poseStack, consumer, packedLight, packedOverlay);
        lamp.visible = true;
        lamp.render(poseStack, consumer,
                wailing ? LightTexture.FULL_BRIGHT : packedLight, packedOverlay);

        poseStack.popPose();
    }

    /**
     * A siren is a landmark and is meant to be seen from the district it covers.
     *
     * <p>Not as far as a wind farm — this is street furniture, not a hillside — but well past the
     * default, which would have the mast pop into existence as you walked up to it.
     */
    @Override
    public int getViewDistance() {
        return 128;
    }

    /** The mast is three and a half blocks tall and the horns overhang their own block. */
    @Override
    public AABB getRenderBoundingBox(SirenBlockEntity siren) {
        BlockPos pos = siren.getBlockPos();
        return new AABB(pos).inflate(1.0D, 0.0D, 1.0D).expandTowards(0.0D, 4.0D, 0.0D);
    }

    @Override
    public boolean shouldRenderOffScreen(SirenBlockEntity siren) {
        return true;
    }
}
