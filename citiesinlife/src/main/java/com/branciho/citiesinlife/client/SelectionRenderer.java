package com.branciho.citiesinlife.client;

import com.branciho.citiesinlife.block.PowerMastBlock;
import com.branciho.citiesinlife.net.ClientCityCache;
import com.branciho.citiesinlife.net.payload.StructureSyncPayload;
import com.branciho.citiesinlife.structure.StructureType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Everything this mod draws into the world: the selection box, registered structures, and the power
 * lines strung between poles.
 *
 * <p>The power lines matter most. There is no block in the air between two masts — the connection is
 * pure server data — so without drawing them a player would link two poles and see absolutely
 * nothing happen.
 */
public final class SelectionRenderer {

    /** Half-size of the little cubes drawn at the selection's corners. */
    private static final double CORNER_SIZE = 0.12D;

    private static final double MAX_STRUCTURE_DISTANCE_SQR = 180.0D * 180.0D;
    private static final double MAX_LINE_DISTANCE_SQR = 260.0D * 260.0D;

    /** Segments per wire. Enough for the sag to read as a curve rather than a kink. */
    private static final int WIRE_SEGMENTS = 12;

    /** How far the two wires sit either side of the pole centre. */
    private static final double WIRE_SPACING = 0.18D;

    /** How far a wire dips at mid-span, as a fraction of its length. */
    private static final double SAG_FACTOR = 0.06D;

    private SelectionRenderer() {
    }

    public static void render(PoseStack poseStack, Vec3 camera) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }

        AABB selection = ClientSelection.bounds();
        boolean anything = selection != null || StructureMode.active()
                || !ClientCityCache.powerLines().isEmpty();
        if (!anything) {
            return;
        }

        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer consumer = buffers.getBuffer(RenderType.lines());

        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);

        drawPowerLines(poseStack, consumer, minecraft.level, minecraft.player.position());
        if (StructureMode.active()) {
            drawRegisteredStructures(poseStack, consumer, minecraft.player.position());
        }
        if (selection != null) {
            drawSelection(poseStack, consumer, selection);
        }

        poseStack.popPose();
        buffers.endBatch(RenderType.lines());
    }

    // -------------------------------------------------------------- selection

    private static void drawSelection(PoseStack poseStack, VertexConsumer consumer, AABB bounds) {
        boolean complete = ClientSelection.phase() == ClientSelection.Phase.COMPLETE;

        // Red in structure mode, because there the box deletes rather than creates. Same gesture,
        // opposite consequence, so it must not look the same.
        float[] rgb;
        if (StructureMode.active()) {
            rgb = new float[]{1.00F, 0.30F, 0.30F};
        } else if (complete) {
            rgb = unpack(ClientSelection.type().colour());
        } else {
            rgb = new float[]{0.35F, 0.90F, 1.00F};
        }

        LevelRenderer.renderLineBox(poseStack, consumer, bounds, rgb[0], rgb[1], rgb[2], 0.9F);

        drawCorner(poseStack, consumer, ClientSelection.pointA(), 0.35F, 1.00F, 0.45F);
        if (complete) {
            drawCorner(poseStack, consumer, ClientSelection.pointB(), 0.45F, 0.70F, 1.00F);
        }
    }

    private static void drawCorner(PoseStack poseStack, VertexConsumer consumer,
                                   @Nullable BlockPos pos, float r, float g, float b) {
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
        for (StructureSyncPayload.Entry entry : ClientCityCache.structures()) {
            AABB box = StructureMode.boundsOf(entry);
            if (box.getCenter().distanceToSqr(eye) > MAX_STRUCTURE_DISTANCE_SQR) {
                continue;
            }
            float[] rgb = unpack(StructureType.byId(entry.typeId(), StructureType.RESIDENTIAL).colour());
            LevelRenderer.renderLineBox(poseStack, consumer, box, rgb[0], rgb[1], rgb[2], 0.75F);
        }
    }

    // ------------------------------------------------------------ power lines

    private static void drawPowerLines(PoseStack poseStack, VertexConsumer consumer,
                                       Level level, Vec3 eye) {
        for (long[] line : ClientCityCache.powerLines()) {
            Vec3 from = attachPoint(level, line[0]);
            Vec3 to = attachPoint(level, line[1]);
            if (from.distanceToSqr(eye) > MAX_LINE_DISTANCE_SQR
                    && to.distanceToSqr(eye) > MAX_LINE_DISTANCE_SQR) {
                continue;
            }

            // Two wires, offset either side of the span so it reads as a real line rather than a
            // single laser between poles.
            double dx = to.x - from.x;
            double dz = to.z - from.z;
            double horizontal = Math.sqrt(dx * dx + dz * dz);
            double offsetX = horizontal < 1.0E-4D ? WIRE_SPACING : (-dz / horizontal) * WIRE_SPACING;
            double offsetZ = horizontal < 1.0E-4D ? 0.0D : (dx / horizontal) * WIRE_SPACING;

            drawWire(poseStack, consumer, from.add(offsetX, 0, offsetZ), to.add(offsetX, 0, offsetZ));
            drawWire(poseStack, consumer, from.subtract(offsetX, 0, offsetZ), to.subtract(offsetX, 0, offsetZ));
        }
    }

    /** A single sagging wire, drawn as a short chain of segments. */
    private static void drawWire(PoseStack poseStack, VertexConsumer consumer, Vec3 from, Vec3 to) {
        double sag = from.distanceTo(to) * SAG_FACTOR;
        Vec3 previous = from;
        for (int i = 1; i <= WIRE_SEGMENTS; i++) {
            double t = i / (double) WIRE_SEGMENTS;
            double x = from.x + (to.x - from.x) * t;
            double z = from.z + (to.z - from.z) * t;
            // A parabola is close enough to a catenary at these spans and far cheaper to reason about.
            double y = from.y + (to.y - from.y) * t - sag * 4.0D * t * (1.0D - t);
            Vec3 next = new Vec3(x, y, z);
            segment(poseStack, consumer, previous, next);
            previous = next;
        }
    }

    private static void segment(PoseStack poseStack, VertexConsumer consumer, Vec3 from, Vec3 to) {
        PoseStack.Pose pose = poseStack.last();
        float nx = (float) (to.x - from.x);
        float ny = (float) (to.y - from.y);
        float nz = (float) (to.z - from.z);
        float length = Math.max(1.0E-4F, (float) Math.sqrt(nx * nx + ny * ny + nz * nz));
        nx /= length;
        ny /= length;
        nz /= length;

        consumer.addVertex(pose, (float) from.x, (float) from.y, (float) from.z)
                .setColor(0.10F, 0.10F, 0.12F, 1.0F)
                .setNormal(pose, nx, ny, nz);
        consumer.addVertex(pose, (float) to.x, (float) to.y, (float) to.z)
                .setColor(0.10F, 0.10F, 0.12F, 1.0F)
                .setNormal(pose, nx, ny, nz);
    }

    /**
     * Where a wire meets a block.
     *
     * <p>Masts are three blocks tall but stored as their foot, so a line has to be lifted to the
     * crossarm or every wire in the world would run along the ground.
     */
    private static Vec3 attachPoint(Level level, long packed) {
        BlockPos pos = BlockPos.of(packed);
        if (level.getBlockState(pos).getBlock() instanceof PowerMastBlock) {
            BlockPos top = PowerMastBlock.attachPoint(pos);
            return new Vec3(top.getX() + 0.5D, top.getY() + 0.75D, top.getZ() + 0.5D);
        }
        return new Vec3(pos.getX() + 0.5D, pos.getY() + 0.9D, pos.getZ() + 0.5D);
    }

    private static float[] unpack(int rgb) {
        return new float[]{
                ((rgb >> 16) & 0xFF) / 255.0F,
                ((rgb >> 8) & 0xFF) / 255.0F,
                (rgb & 0xFF) / 255.0F};
    }
}
