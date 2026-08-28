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
import net.minecraft.world.phys.AABB;

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

    /**
     * All in block-model units, read off the pole's own model so the two cannot drift apart.
     *
     * <p>The first version had the cloth start at x=6 while the pole occupies 7 to 9, so the flag
     * hung <em>through</em> its own pole, and at twelve by seven and a half it was smaller than the
     * block it flew from. It now starts clear of the far face and is twenty-four wide: a block and
     * a half of cloth on a pole two blocks tall, which is the size a flag has to be to read as a
     * landmark rather than as a sign.
     */
    private static final float POLE_FACE = 9.0F;
    private static final float CLOTH_GAP = 0.2F;
    private static final float CLOTH_WIDTH = 24.0F;
    private static final float CLOTH_HEIGHT = 14.0F;

    /** Where the top of the cloth sits on the pole, in the model's own upward-positive space. */
    private static final float CLOTH_TOP = 29.0F;

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
        float x0 = POLE_FACE + CLOTH_GAP;
        float x1 = x0 + CLOTH_WIDTH;
        // The pose stack's y runs downward from the top of the block after the flip above, so a
        // height in the model's own space becomes 16 minus itself. Getting this the wrong way round
        // is how a flag ends up under the ground rather than on the pole.
        float y0 = 16.0F - CLOTH_TOP;
        float y1 = 16.0F - (CLOTH_TOP - CLOTH_HEIGHT);
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

    /**
     * Big enough to hold the pole and the cloth.
     *
     * <p>Both reach outside the block: the pole is two blocks tall and the cloth is a block and a
     * half of it hanging off one side. Left at the default one-block box, the whole thing vanishes
     * the moment the block itself leaves the screen - which for something meant to be seen from
     * across the map is exactly backwards.
     *
     * <p>On the renderer rather than on the block entity, which is where NeoForge puts it: the
     * extension is on {@code BlockEntityRenderer}, and the same method written on the block entity
     * overrides nothing at all and is never called.
     */
    @Override
    public AABB getRenderBoundingBox(CityFlagBlockEntity flag) {
        return new AABB(flag.getBlockPos()).inflate(3.0D);
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
