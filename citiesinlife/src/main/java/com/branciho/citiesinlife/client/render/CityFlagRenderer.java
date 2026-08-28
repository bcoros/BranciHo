package com.branciho.citiesinlife.client.render;

import com.branciho.citiesinlife.block.CityFlagBlock;
import com.branciho.citiesinlife.blockentity.CityFlagBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

/**
 * The cloth on the pole.
 *
 * <p>One quad with the city's design uploaded as an eight by five texture, drawn with the no-cull
 * render type so it is visible from both sides — a flag you can only see from in front would be
 * worse than no flag, and mirroring on the back is what a real one does anyway.
 *
 * <p>Deliberately not animated. A waving flag needs either a rigged model or per-vertex maths every
 * frame for every pole on screen, and a city flag is a landmark you read from a distance rather
 * than something you stand and admire.
 *
 * <p>Everything is positioned in <b>block-model units</b>, the same 0..16 space the pole's own JSON
 * uses, and the facing comes from the blockstate's own y values rather than from {@code
 * Direction#toYRot()} — the two disagree by 180 degrees, which once put a reactor lever's arm on
 * the opposite side of its own housing.
 */
public class CityFlagRenderer implements BlockEntityRenderer<CityFlagBlockEntity> {

    private static final float POLE_TOP = 15.0F;
    private static final float CLOTH_WIDTH = 12.0F;
    private static final float CLOTH_HEIGHT = 7.5F;
    private static final float POLE_EDGE = 6.0F;

    public CityFlagRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(CityFlagBlockEntity flag, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int light, int overlay) {
        ResourceLocation texture = FlagTextures.of(flag.flag());
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(texture));

        poseStack.pushPose();
        poseStack.translate(0.5D, 0.5D, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(-facing(flag)));
        // Into block-model space, flipped the way every block model is - the same convention the
        // reactor lever uses, so the numbers above are the numbers you would put in the JSON.
        poseStack.scale(1.0F / 16.0F, -1.0F / 16.0F, -1.0F / 16.0F);
        poseStack.translate(-8.0D, -8.0D, -8.0D);

        PoseStack.Pose pose = poseStack.last();
        float x0 = POLE_EDGE;
        float x1 = POLE_EDGE + CLOTH_WIDTH;
        float y0 = 16.0F - POLE_TOP;
        float y1 = y0 + CLOTH_HEIGHT;
        float z = 8.0F;

        vertex(consumer, pose, x0, y1, z, 0.0F, 1.0F, light, overlay);
        vertex(consumer, pose, x1, y1, z, 1.0F, 1.0F, light, overlay);
        vertex(consumer, pose, x1, y0, z, 1.0F, 0.0F, light, overlay);
        vertex(consumer, pose, x0, y0, z, 0.0F, 0.0F, light, overlay);

        poseStack.popPose();
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y,
                               float z, float u, float v, int light, int overlay) {
        consumer.addVertex(pose, x, y, z)
                .setColor(1.0F, 1.0F, 1.0F, 1.0F)
                .setUv(u, v)
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(pose, 0.0F, 0.0F, 1.0F);
    }

    private static float facing(CityFlagBlockEntity flag) {
        Direction direction = flag.getBlockState().hasProperty(CityFlagBlock.FACING)
                ? flag.getBlockState().getValue(CityFlagBlock.FACING)
                : Direction.NORTH;
        return switch (direction) {
            case EAST -> 90.0F;
            case SOUTH -> 180.0F;
            case WEST -> 270.0F;
            default -> 0.0F;
        };
    }
}
