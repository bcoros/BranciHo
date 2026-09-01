package com.branciho.citiesinlife.client;

import com.branciho.citiesinlife.power.MastBlock;
import com.branciho.citiesinlife.block.TurbineBlock;
import com.branciho.citiesinlife.net.ClientCityCache;
import com.branciho.citiesinlife.net.payload.StructureSyncPayload;
import com.branciho.citiesinlife.road.RoadTile;
import com.branciho.citiesinlife.structure.StructureType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

    /**
     * How far a health bar is legible from.
     *
     * <p>Much shorter than the outlines. An outline at a hundred and eighty blocks is a useful hint
     * that something is registered over there; two lines of text at that range are a smear, and a
     * hundred of them are a smear that costs a draw call each.
     */
    private static final double MAX_LABEL_DISTANCE_SQR = 64.0D * 64.0D;

    /** How far above the top of the box the label floats. */
    private static final double LABEL_LIFT = 0.7D;

    /** Vanilla's nameplate scale: one text pixel to this many blocks. */
    private static final float LABEL_SCALE = 0.025F;

    private static final float BAR_WIDTH = 60.0F;
    private static final float BAR_HEIGHT = 5.0F;
    private static final double MAX_LINE_DISTANCE_SQR = 260.0D * 260.0D;

    /** Segments per wire. Enough for the sag to read as a curve rather than a kink. */
    private static final int WIRE_SEGMENTS = 12;

    /** How far the two wires sit either side of the pole centre. */
    private static final double WIRE_SPACING = 0.18D;

    /** How far a wire dips at mid-span, as a fraction of its length. */
    private static final double SAG_FACTOR = 0.06D;

    /** How far pavement is drawn, and how much of it at once. */
    private static final double MAX_PATH_DISTANCE_SQR = 64.0D * 64.0D;
    private static final int MAX_PATHS_DRAWN = 2048;

    /** How much road is drawn at once. */
    private static final int MAX_ROADS_DRAWN = 2048;

    /** How far a direction tick sticks out from the middle of its tile. */
    private static final double TICK_INNER = 0.15D;
    private static final double TICK_OUTER = 0.42D;

    /** How far above the marked block its outline floats, so it does not fight with the surface. */
    private static final double PATH_LIFT = 1.015D;

    private SelectionRenderer() {
    }

    public static void render(PoseStack poseStack, Vec3 camera) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }

        // Pipe links have no model at all, so the only time they are drawn is while the tool that
        // made them is in hand. That is the whole compromise: invisible in play, checkable on demand.
        boolean showingPipes = ClientEvents.holdingPipeTool(minecraft.player)
                && !ClientCityCache.waterLines().isEmpty();

        // Pavement is scenery once it is drawn, so it is only shown when the player is actually
        // thinking about it: holding the tool that makes it, or in structure mode looking at what
        // they have already laid out.
        boolean showingPaths = (StructureMode.active() || ClientEvents.holdingPathTool(minecraft.player))
                && ClientCityCache.paths().length > 0;

        // Road is shown on the same terms as pavement, and for the same reason: once drawn it is
        // scenery, and the only time it matters is while you are thinking about it.
        boolean showingRoads = (StructureMode.active() || ClientEvents.holdingRoadTool(minecraft.player))
                && ClientCityCache.roadTiles().length > 0;

        AABB selection = ClientSelection.bounds();
        boolean anything = selection != null || StructureMode.active()
                || !ClientCityCache.powerLines().isEmpty() || showingPipes || showingPaths
                || showingRoads;
        if (!anything) {
            return;
        }

        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer consumer = buffers.getBuffer(RenderType.lines());

        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);

        drawPowerLines(poseStack, consumer, minecraft.level, minecraft.player.position());
        if (showingPipes) {
            drawPipeLinks(poseStack, consumer, minecraft.player.position());
        }
        if (showingPaths) {
            drawPaths(poseStack, consumer, minecraft.player.position());
        }
        if (showingRoads) {
            drawRoads(poseStack, consumer, minecraft.player.position());
        }
        if (StructureMode.active()) {
            drawRegisteredStructures(poseStack, consumer, minecraft.player.position());
        }
        if (selection != null) {
            drawSelection(poseStack, consumer, selection);
        }

        poseStack.popPose();
        buffers.endBatch(RenderType.lines());

        // After the line batch, and inside its own translate: text and quads are different render
        // types, and drawing them in the middle of the lines would flush that batch per building.
        if (StructureMode.active()) {
            poseStack.pushPose();
            poseStack.translate(-camera.x, -camera.y, -camera.z);
            drawStructureHealth(poseStack, buffers, minecraft, minecraft.player.position());
            poseStack.popPose();
        }
    }

    // -------------------------------------------------------------- selection

    private static void drawSelection(PoseStack poseStack, VertexConsumer consumer, AABB bounds) {
        boolean complete = ClientSelection.phase() == ClientSelection.Phase.COMPLETE;

        // Red in structure mode, because there the box deletes rather than creates. Same gesture,
        // opposite consequence, so it must not look the same.
        Minecraft minecraft = Minecraft.getInstance();
        boolean pathTool = minecraft.player != null && ClientEvents.holdingPathTool(minecraft.player);
        boolean roadTool = minecraft.player != null && ClientEvents.holdingRoadTool(minecraft.player);
        boolean warWand = minecraft.player != null && ClientEvents.holdingWarWand(minecraft.player);

        float[] rgb;
        if (warWand) {
            // Red, and not the structure-mode red: this box takes rather than deletes, and the two
            // are close enough in consequence that they should not be identical on screen.
            rgb = new float[]{1.00F, 0.42F, 0.18F};
        } else if (pathTool) {
            // The same amber the marked ground is drawn in, so it is obvious what the box will become.
            rgb = new float[]{1.00F, 0.82F, 0.32F};
        } else if (roadTool) {
            // Blue, distinct from pavement's amber: the two tools draw the same box over the same
            // ground and the only way to tell which is armed is the colour.
            rgb = new float[]{0.45F, 0.62F, 0.95F};
        } else if (StructureMode.active()) {
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

    /**
     * The marked ground, drawn as a flat outline on top of each block.
     *
     * <p>Flat rather than a full cube, because pavement is a surface and a stack of wireframe cubes
     * over a street reads as scaffolding rather than as a road.
     */
    private static void drawPaths(PoseStack poseStack, VertexConsumer consumer, Vec3 eye) {
        int drawn = 0;
        for (long packed : ClientCityCache.paths()) {
            if (drawn >= MAX_PATHS_DRAWN) {
                return;
            }
            double x = BlockPos.getX(packed);
            double y = BlockPos.getY(packed);
            double z = BlockPos.getZ(packed);
            if (eye.distanceToSqr(x + 0.5D, y + 0.5D, z + 0.5D) > MAX_PATH_DISTANCE_SQR) {
                continue;
            }
            drawn++;
            LevelRenderer.renderLineBox(poseStack, consumer,
                    new AABB(x + 0.02D, y + PATH_LIFT, z + 0.02D,
                            x + 0.98D, y + PATH_LIFT, z + 0.98D),
                    1.00F, 0.82F, 0.32F, 0.85F);
        }
    }

    /**
     * The road, drawn flat like pavement, with a tick on each side traffic may leave by.
     *
     * <p>Those ticks are the whole point of drawing it at all. A one-way street and a two-way street
     * cover exactly the same blocks, so without them the overlay could not tell the player the one
     * thing about a road they cannot see from standing on it.
     */
    private static void drawRoads(PoseStack poseStack, VertexConsumer consumer, Vec3 eye) {
        long[] tiles = ClientCityCache.roadTiles();
        int[] flags = ClientCityCache.roadFlags();
        int count = Math.min(tiles.length, flags.length);
        int drawn = 0;
        for (int i = 0; i < count; i++) {
            if (drawn >= MAX_ROADS_DRAWN) {
                return;
            }
            long packed = tiles[i];
            double x = BlockPos.getX(packed);
            double y = BlockPos.getY(packed);
            double z = BlockPos.getZ(packed);
            if (eye.distanceToSqr(x + 0.5D, y + 0.5D, z + 0.5D) > MAX_PATH_DISTANCE_SQR) {
                continue;
            }
            drawn++;

            int tile = flags[i];
            float r;
            float g;
            float b;
            if (RoadTile.is(tile, RoadTile.PARKING)) {
                r = 0.35F;
                g = 0.85F;
                b = 0.50F;
            } else if (RoadTile.is(tile, RoadTile.INTERSECTION)) {
                r = 0.95F;
                g = 0.95F;
                b = 0.95F;
            } else if (RoadTile.is(tile, RoadTile.HIGHWAY)) {
                r = 1.00F;
                g = 0.62F;
                b = 0.20F;
            } else {
                r = 0.45F;
                g = 0.62F;
                b = 0.95F;
            }

            LevelRenderer.renderLineBox(poseStack, consumer,
                    new AABB(x + 0.02D, y + PATH_LIFT, z + 0.02D,
                            x + 0.98D, y + PATH_LIFT, z + 0.98D),
                    r, g, b, 0.85F);

            Vec3 centre = new Vec3(x + 0.5D, y + PATH_LIFT, z + 0.5D);
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                if (!RoadTile.allows(tile, direction)) {
                    continue;
                }
                double dx = direction.getStepX();
                double dz = direction.getStepZ();
                segment(poseStack, consumer,
                        centre.add(dx * TICK_INNER, 0.0D, dz * TICK_INNER),
                        centre.add(dx * TICK_OUTER, 0.0D, dz * TICK_OUTER),
                        r, g, b);
            }
        }
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

    /**
     * The health bars, and the names over them.
     *
     * <p>Drawn in a pass of their own rather than beside the outlines, because text and quads are
     * different render types and interleaving them with the line batch would flush it once per
     * building. Only in structure mode, which is the whole rule: a city with a hundred buildings
     * would be unreadable with a hundred labels floating over it all the time, and the moment you
     * want to know how much punishment something has taken is the moment you are already looking at
     * the registrations.
     */
    private static void drawStructureHealth(PoseStack poseStack, MultiBufferSource.BufferSource buffers,
                                            Minecraft minecraft, Vec3 eye) {
        Font font = minecraft.font;
        Quaternionf facing = minecraft.getEntityRenderDispatcher().cameraOrientation();
        for (StructureSyncPayload.Entry entry : ClientCityCache.structures()) {
            AABB box = StructureMode.boundsOf(entry);
            Vec3 centre = box.getCenter();
            if (centre.distanceToSqr(eye) > MAX_LABEL_DISTANCE_SQR) {
                continue;
            }

            poseStack.pushPose();
            poseStack.translate(centre.x, box.maxY + LABEL_LIFT, centre.z);
            poseStack.mulPose(facing);
            // Nameplate scale, negated on Y because text is drawn downwards in screen space.
            poseStack.scale(-LABEL_SCALE, -LABEL_SCALE, LABEL_SCALE);

            int health = Math.max(0, entry.health());
            int max = Math.max(1, entry.maxHealth());
            float share = Mth.clamp(health / (float) max, 0.0F, 1.0F);

            healthBar(poseStack, buffers, share);

            Component name = Component.literal(entry.name());
            Component numbers = Component.translatable("hud.citiesinlife.structure_health",
                    health, max);
            font.drawInBatch(name, -font.width(name) / 2.0F, -BAR_HEIGHT - 22.0F,
                    0xFFFFFFFF, false, poseStack.last().pose(), buffers,
                    Font.DisplayMode.SEE_THROUGH, 0x40000000, LightTexture.FULL_BRIGHT);
            font.drawInBatch(numbers, -font.width(numbers) / 2.0F, -BAR_HEIGHT - 11.0F,
                    barColour(share), false, poseStack.last().pose(), buffers,
                    Font.DisplayMode.SEE_THROUGH, 0x40000000, LightTexture.FULL_BRIGHT);

            poseStack.popPose();
        }
        buffers.endBatch();
    }

    /** A trough and the part of it that is left, in the same billboard space as the text. */
    private static void healthBar(PoseStack poseStack, MultiBufferSource buffers, float share) {
        VertexConsumer quads = buffers.getBuffer(RenderType.debugQuads());
        float half = BAR_WIDTH / 2.0F;
        // The trough first, dark and full width, so a bar with almost nothing left is still
        // visible as a bar rather than as a stray pixel.
        quad(poseStack, quads, -half - 1.0F, -BAR_HEIGHT - 1.0F, half + 1.0F, 1.0F,
                0.0F, 0.0F, 0.0F, 0.55F);
        if (share <= 0.0F) {
            return;
        }
        float[] rgb = unpack(barColour(share));
        quad(poseStack, quads, -half, -BAR_HEIGHT, -half + BAR_WIDTH * share, 0.0F,
                rgb[0], rgb[1], rgb[2], 0.95F);
    }

    private static void quad(PoseStack poseStack, VertexConsumer quads,
                             float x0, float y0, float x1, float y1,
                             float r, float g, float b, float a) {
        Matrix4f pose = poseStack.last().pose();
        quads.addVertex(pose, x0, y1, 0.0F).setColor(r, g, b, a);
        quads.addVertex(pose, x1, y1, 0.0F).setColor(r, g, b, a);
        quads.addVertex(pose, x1, y0, 0.0F).setColor(r, g, b, a);
        quads.addVertex(pose, x0, y0, 0.0F).setColor(r, g, b, a);
    }

    /** Green down to amber down to red, so the state reads before the numbers do. */
    private static int barColour(float share) {
        if (share > 0.6F) {
            return 0xFF66E576;
        }
        return share > 0.3F ? 0xFFFFD859 : 0xFFFF6B6B;
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

    /**
     * The pipe links, drawn dead straight.
     *
     * <p>No sag, unlike a power line. These are not cables strung between poles - they stand for
     * plumbing that runs under the ground, and drawing them as catenaries would say the wrong thing
     * about what they are.
     */
    private static void drawPipeLinks(PoseStack poseStack, VertexConsumer consumer, Vec3 eye) {
        for (long[] line : ClientCityCache.waterLines()) {
            Vec3 from = pipePoint(line[0]);
            Vec3 to = pipePoint(line[1]);
            if (from.distanceToSqr(eye) > MAX_LINE_DISTANCE_SQR
                    && to.distanceToSqr(eye) > MAX_LINE_DISTANCE_SQR) {
                continue;
            }
            segment(poseStack, consumer, from, to, 0.30F, 0.70F, 1.00F);
        }
    }

    private static Vec3 pipePoint(long packed) {
        BlockPos pos = BlockPos.of(packed);
        return new Vec3(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
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

    /** A power wire's segment: near-black, the colour of a cable against the sky. */
    private static void segment(PoseStack poseStack, VertexConsumer consumer, Vec3 from, Vec3 to) {
        segment(poseStack, consumer, from, to, 0.10F, 0.10F, 0.12F);
    }

    private static void segment(PoseStack poseStack, VertexConsumer consumer, Vec3 from, Vec3 to,
                                float red, float green, float blue) {
        PoseStack.Pose pose = poseStack.last();
        float nx = (float) (to.x - from.x);
        float ny = (float) (to.y - from.y);
        float nz = (float) (to.z - from.z);
        float length = Math.max(1.0E-4F, (float) Math.sqrt(nx * nx + ny * ny + nz * nz));
        nx /= length;
        ny /= length;
        nz /= length;

        consumer.addVertex(pose, (float) from.x, (float) from.y, (float) from.z)
                .setColor(red, green, blue, 1.0F)
                .setNormal(pose, nx, ny, nz);
        consumer.addVertex(pose, (float) to.x, (float) to.y, (float) to.z)
                .setColor(red, green, blue, 1.0F)
                .setNormal(pose, nx, ny, nz);
    }

    /**
     * Where a wire meets a block.
     *
     * <p>Masts are three blocks tall but stored as their foot, so a line has to be lifted to the
     * crossarm or every wire in the world would run along the ground. The turbine has the same
     * problem for the same reason: it is one block position wearing a machine two blocks tall.
     */
    private static Vec3 attachPoint(Level level, long packed) {
        BlockPos pos = BlockPos.of(packed);
        if (level.getBlockState(pos).getBlock() instanceof MastBlock mast) {
            // Asked of the block rather than named, so the seven-block pylon and the three-block
            // wooden mast each get their own top without this method knowing either exists.
            return new Vec3(pos.getX() + 0.5D, pos.getY() + mast.mastHeight() - 0.25D,
                    pos.getZ() + 0.5D);
        }
        if (level.getBlockState(pos).getBlock() instanceof TurbineBlock) {
            return new Vec3(pos.getX() + 0.5D, pos.getY() + 1.4D, pos.getZ() + 0.5D);
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
