package com.branciho.citiesinlife.client;

import com.branciho.citiesinlife.net.ClientCityCache;
import com.branciho.citiesinlife.net.payload.StructureSyncPayload;
import com.branciho.citiesinlife.structure.StructureType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Draws the selection box and, in structure mode, every registration near the player.
 *
 * <p>The corner markers are the reason this reads as a selection rather than as a wireframe cube
 * sitting in the world: they show the two points the player actually placed, which is the thing they
 * are aiming with.
 */
public final class SelectionRenderer {

    /** Half-size of the little cubes drawn at the selection's corners. */
    private static final double CORNER_SIZE = 0.12D;

    /** Structures further away than this stop being drawn; outlines are illegible long before. */
    private static final double MAX_STRUCTURE_DISTANCE_SQR = 180.0D * 180.0D;

    private SelectionRenderer() {
    }

    public static void render(PoseStack poseStack, Vec3 camera) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }

        AABB selection = ClientSelection.bounds();
        boolean anythingToDraw = selection != null || StructureMode.active();
        if (!anythingToDraw) {
            return;
        }

        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer consumer = buffers.getBuffer(RenderType.lines());

        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);

        if (StructureMode.active()) {
            drawRegisteredStructures(poseStack, consumer, minecraft.player.position());
        }
        if (selection != null) {
            drawSelection(poseStack, consumer, selection);
        }

        poseStack.popPose();
        buffers.endBatch(RenderType.lines());
    }

    private static void drawSelection(PoseStack poseStack, VertexConsumer consumer, AABB bounds) {
        // Cyan while sizing, then the colour of the chosen type once both corners are down, so the
        // box itself tells you what you are about to create.
        boolean complete = ClientSelection.phase() == ClientSelection.Phase.COMPLETE;
        float[] rgb = complete
                ? unpack(ClientSelection.type().colour())
                : new float[]{0.35F, 0.90F, 1.00F};

        LevelRenderer.renderLineBox(poseStack, consumer, bounds, rgb[0], rgb[1], rgb[2], 0.9F);

        drawCorner(poseStack, consumer, ClientSelection.pointA(), 0.35F, 1.00F, 0.45F);
        if (complete) {
            drawCorner(poseStack, consumer, ClientSelection.pointB(), 0.45F, 0.70F, 1.00F);
        }
    }

    /** A small cube marking one of the two points the player placed. */
    private static void drawCorner(PoseStack poseStack, VertexConsumer consumer,
                                   @Nullable net.minecraft.core.BlockPos pos, float r, float g, float b) {
        if (pos == null) {
            return;
        }
        double x = pos.getX() + 0.5D;
        double y = pos.getY() + 0.5D;
        double z = pos.getZ() + 0.5D;
        LevelRenderer.renderLineBox(poseStack, consumer,
                new AABB(x - CORNER_SIZE, y - CORNER_SIZE, z - CORNER_SIZE,
                        x + CORNER_SIZE, y + CORNER_SIZE, z + CORNER_SIZE),
                r, g, b, 1.0F);
    }

    private static void drawRegisteredStructures(PoseStack poseStack, VertexConsumer consumer, Vec3 eye) {
        StructureSyncPayload.Entry targeted = StructureMode.lookingAt();

        for (StructureSyncPayload.Entry entry : ClientCityCache.structures()) {
            AABB box = StructureMode.boundsOf(entry);
            if (box.getCenter().distanceToSqr(eye) > MAX_STRUCTURE_DISTANCE_SQR) {
                continue;
            }
            boolean isTarget = targeted != null && targeted.id().equals(entry.id());
            if (isTarget) {
                // The one that Shift+left click would delete, drawn white so there is no doubt
                // about which registration is about to disappear.
                LevelRenderer.renderLineBox(poseStack, consumer, box, 1.0F, 1.0F, 1.0F, 1.0F);
                LevelRenderer.renderLineBox(poseStack, consumer, box.inflate(0.05D), 1.0F, 1.0F, 1.0F, 0.5F);
            } else {
                float[] rgb = unpack(
                        StructureType.byId(entry.typeId(), StructureType.RESIDENTIAL).colour());
                LevelRenderer.renderLineBox(poseStack, consumer, box, rgb[0], rgb[1], rgb[2], 0.75F);
            }
        }
    }

    private static float[] unpack(int rgb) {
        return new float[]{
                ((rgb >> 16) & 0xFF) / 255.0F,
                ((rgb >> 8) & 0xFF) / 255.0F,
                (rgb & 0xFF) / 255.0F};
    }
}
