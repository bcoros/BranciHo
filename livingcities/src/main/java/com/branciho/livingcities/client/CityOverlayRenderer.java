package com.branciho.livingcities.client;

import com.branciho.livingcities.building.ZoneUse;
import com.branciho.livingcities.net.payload.CityOverlayPayload;
import com.branciho.livingcities.utility.UtilityKind;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Draws city territory and registered building outlines.
 *
 * <p>A registration is server-side data with no blocks of its own, which made it completely invisible:
 * you could stand inside one, be blocked from building there, and have no way to see why. Everything
 * here exists so that state has a shape on screen.
 *
 * <p>Territory is drawn only along the <em>boundary</em> of the claim. Outlining every chunk turns a
 * city into a grid of noise; outlining only the edge reads as a border.
 */
public final class CityOverlayRenderer {

    /** Vertical half-height of the territory wall drawn around the player's eye level. */
    private static final double BORDER_HEIGHT = 3.0D;

    /** Buildings further away than this are skipped; outlines stop being legible long before. */
    private static final double MAX_BUILDING_DISTANCE_SQR = 160.0D * 160.0D;

    /** Coverage rings are large, so they stay visible further out than building outlines. */
    private static final double MAX_COVERAGE_DISTANCE_SQR = 220.0D * 220.0D;

    private CityOverlayRenderer() {
    }

    public static void render(PoseStack poseStack, Vec3 camera) {
        CityOverlayPayload overlay = ClientOverlayCache.overlay();
        if (overlay == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }

        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer consumer = buffers.getBuffer(RenderType.lines());

        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);

        drawTerritory(poseStack, consumer, overlay, player.getY());
        drawCoverage(poseStack, consumer, overlay, player.position());
        drawBuildings(poseStack, consumer, overlay, player.position());

        poseStack.popPose();
        buffers.endBatch(RenderType.lines());
    }

    private static void drawTerritory(PoseStack poseStack, VertexConsumer consumer,
                                      CityOverlayPayload overlay, double playerY) {
        if (overlay.claimedChunks().isEmpty()) {
            return;
        }
        final double lowY = playerY - BORDER_HEIGHT;
        final double highY = playerY + BORDER_HEIGHT;

        for (long key : overlay.claimedChunks()) {
            ChunkPos chunk = new ChunkPos(key);
            double minX = chunk.getMinBlockX();
            double minZ = chunk.getMinBlockZ();
            double maxX = minX + 16.0D;
            double maxZ = minZ + 16.0D;

            // Only sides facing unclaimed ground: interior edges would just be grid clutter.
            if (!claims(overlay, chunk.x, chunk.z - 1)) {
                wall(poseStack, consumer, minX, lowY, minZ, maxX, highY, minZ);
            }
            if (!claims(overlay, chunk.x, chunk.z + 1)) {
                wall(poseStack, consumer, minX, lowY, maxZ, maxX, highY, maxZ);
            }
            if (!claims(overlay, chunk.x - 1, chunk.z)) {
                wall(poseStack, consumer, minX, lowY, minZ, minX, highY, maxZ);
            }
            if (!claims(overlay, chunk.x + 1, chunk.z)) {
                wall(poseStack, consumer, maxX, lowY, minZ, maxX, highY, maxZ);
            }
        }
    }

    private static boolean claims(CityOverlayPayload overlay, int x, int z) {
        return ClientOverlayCache.claims(new ChunkPos(x, z).toLong());
    }

    /** A flat vertical panel along one chunk edge, drawn as a degenerate box. */
    private static void wall(PoseStack poseStack, VertexConsumer consumer,
                             double x1, double y1, double z1, double x2, double y2, double z2) {
        LevelRenderer.renderLineBox(poseStack, consumer,
                new AABB(x1, y1, z1, x2, y2, z2),
                0.30F, 0.85F, 1.00F, 0.55F);
    }

    /**
     * Ring out each distributor's service radius on the ground.
     *
     * <p>Drawn as a ring at the distributor's own height rather than a sphere: the coverage test is a
     * plain distance check, and a circle on the ground is what a player can actually plan against.
     * An overloaded network rings red, which turns "the lights are off" into "this one is the problem".
     */
    private static void drawCoverage(PoseStack poseStack, VertexConsumer consumer,
                                     CityOverlayPayload overlay, Vec3 eye) {
        for (CityOverlayPayload.Coverage entry : overlay.coverage()) {
            double cx = entry.x() + 0.5D;
            double cy = entry.y() + 0.5D;
            double cz = entry.z() + 0.5D;
            if (eye.distanceToSqr(cx, cy, cz) > MAX_COVERAGE_DISTANCE_SQR) {
                continue;
            }
            boolean water = UtilityKind.WATER.id().equals(entry.kindId());
            float red = entry.overloaded() ? 1.0F : (water ? 0.30F : 1.00F);
            float green = entry.overloaded() ? 0.25F : (water ? 0.75F : 0.85F);
            float blue = entry.overloaded() ? 0.25F : (water ? 1.00F : 0.20F);
            ring(poseStack, consumer, cx, cy, cz, entry.radius(), red, green, blue);
        }
    }

    /** A ring approximated by short segments; enough for the eye, cheap enough to redraw every frame. */
    private static void ring(PoseStack poseStack, VertexConsumer consumer,
                             double cx, double cy, double cz, int radius, float r, float g, float b) {
        final int segments = 64;
        for (int i = 0; i < segments; i++) {
            double a0 = (Math.PI * 2 * i) / segments;
            double a1 = (Math.PI * 2 * (i + 1)) / segments;
            double x0 = cx + Math.cos(a0) * radius;
            double z0 = cz + Math.sin(a0) * radius;
            double x1 = cx + Math.cos(a1) * radius;
            double z1 = cz + Math.sin(a1) * radius;
            // renderLineBox on a degenerate box gives a straight segment without a bespoke line helper.
            LevelRenderer.renderLineBox(poseStack, consumer,
                    new AABB(Math.min(x0, x1), cy, Math.min(z0, z1), Math.max(x0, x1), cy, Math.max(z0, z1)),
                    r, g, b, 0.6F);
        }
    }

    private static void drawBuildings(PoseStack poseStack, VertexConsumer consumer,
                                      CityOverlayPayload overlay, Vec3 eye) {
        for (CityOverlayPayload.BuildingBox box : overlay.buildings()) {
            AABB bounds = new AABB(
                    box.minX(), box.minY(), box.minZ(),
                    box.maxX() + 1.0D, box.maxY() + 1.0D, box.maxZ() + 1.0D);
            if (bounds.getCenter().distanceToSqr(eye) > MAX_BUILDING_DISTANCE_SQR) {
                continue;
            }
            float[] colour = colourFor(box);
            LevelRenderer.renderLineBox(poseStack, consumer, bounds, colour[0], colour[1], colour[2], 0.85F);
        }
    }

    /**
     * Colour by what the building is for, so a glance at a district tells you its make-up.
     * A building whose measurements are stale is drawn amber regardless of use, because that is the
     * state the player most needs prompting about.
     */
    private static float[] colourFor(CityOverlayPayload.BuildingBox box) {
        // Utility failure outranks everything else: a building with no power is not doing its job,
        // whatever it is zoned as.
        if (!box.powered() || !box.watered()) {
            return new float[]{1.00F, 0.30F, 0.30F};
        }
        if (box.needsRescan()) {
            return new float[]{1.00F, 0.72F, 0.15F};
        }
        if (box.mixedUse()) {
            return new float[]{0.85F, 0.55F, 1.00F};
        }
        ZoneUse use = ZoneUse.byId(box.zoneId(), ZoneUse.UNUSED);
        return switch (use) {
            case RESIDENTIAL -> new float[]{0.40F, 0.90F, 0.45F};
            case COMMERCIAL -> new float[]{0.35F, 0.65F, 1.00F};
            case OFFICE -> new float[]{0.30F, 0.85F, 0.90F};
            case INDUSTRIAL, WAREHOUSE -> new float[]{1.00F, 0.85F, 0.35F};
            case PARK -> new float[]{0.55F, 1.00F, 0.60F};
            case GOVERNMENT, PUBLIC_SERVICE -> new float[]{1.00F, 0.45F, 0.45F};
            // Unassigned is deliberately drab: it should look like something waiting to be dealt with.
            default -> new float[]{0.70F, 0.70F, 0.70F};
        };
    }

    /** The building whose box contains this position, for "what am I standing in" hints. */
    public static @Nullable CityOverlayPayload.BuildingBox boxAt(double x, double y, double z) {
        CityOverlayPayload overlay = ClientOverlayCache.overlay();
        if (overlay == null) {
            return null;
        }
        for (CityOverlayPayload.BuildingBox box : overlay.buildings()) {
            if (x >= box.minX() && x <= box.maxX() + 1
                    && y >= box.minY() && y <= box.maxY() + 1
                    && z >= box.minZ() && z <= box.maxZ() + 1) {
                return box;
            }
        }
        return null;
    }
}
