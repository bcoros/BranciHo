package com.branciho.citiesinlife.client;

import com.branciho.citiesinlife.scan.StructureScanner;
import com.branciho.citiesinlife.structure.MeasureMode;
import com.branciho.citiesinlife.structure.StructureType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The selection the player is currently drawing.
 *
 * <p>Entirely client-side and entirely provisional. Nothing here is authoritative — when the player
 * commits, two corners and a type go to the server and the server decides what actually happens. This
 * exists so that drawing a box feels immediate rather than like filling in a form.
 *
 * <p>The three phases mirror how it feels to use: click once to drop an anchor, move to size the box
 * live, click again to freeze it.
 */
public final class ClientSelection {

    public enum Phase {
        /** Nothing selected. */
        IDLE,
        /** One corner placed; the other follows the crosshair. */
        ANCHORED,
        /** Both corners fixed, waiting for the player to choose a type and confirm. */
        COMPLETE
    }

    /** How far ahead a corner lands when the player is pointing at open sky. */
    private static final int AIR_REACH = 6;

    /** Selections bigger than this are not previewed; the server still measures them properly. */
    private static final int MAX_PREVIEW_VOLUME = 40_000;

    private static Phase phase = Phase.IDLE;
    private static @Nullable BlockPos pointA;
    private static @Nullable BlockPos pointB;
    private static StructureType type = StructureType.RESIDENTIAL;
    private static MeasureMode measureMode = MeasureMode.FLOORS;

    private static int previewFloors = -1;
    private static int previewCells = -1;
    private static @Nullable BlockPos lastPreviewedB;

    private ClientSelection() {
    }

    public static Phase phase() {
        return phase;
    }

    public static boolean active() {
        return phase != Phase.IDLE;
    }

    public static @Nullable BlockPos pointA() {
        return pointA;
    }

    public static @Nullable BlockPos pointB() {
        return pointB;
    }

    public static StructureType type() {
        return type;
    }

    public static void cycleType(int direction) {
        type = type.next(direction);
    }

    public static MeasureMode measureMode() {
        return measureMode;
    }

    /** Swap measurement mode and re-measure, so the panel updates the moment it is pressed. */
    public static void toggleMeasureMode() {
        measureMode = measureMode.other();
        lastPreviewedB = null;
        refreshPreview();
    }

    public static int previewFloors() {
        return previewFloors;
    }

    public static int previewCells() {
        return previewCells;
    }

    /** Advance the selection one step. Called on right-click while holding the wand. */
    public static void advance() {
        BlockPos target = targetedBlock();
        if (target == null) {
            return;
        }
        switch (phase) {
            case IDLE -> {
                pointA = target;
                pointB = target;
                phase = Phase.ANCHORED;
                clearPreview();
            }
            case ANCHORED -> {
                pointB = target;
                phase = Phase.COMPLETE;
                refreshPreview();
            }
            // A third click starts a fresh box rather than doing nothing, which is what a player
            // who has changed their mind about where the building is expects.
            case COMPLETE -> {
                pointA = target;
                pointB = target;
                phase = Phase.ANCHORED;
                clearPreview();
            }
        }
    }

    public static void cancel() {
        phase = Phase.IDLE;
        pointA = null;
        pointB = null;
        clearPreview();
    }

    /** While anchored, the loose corner tracks whatever the player is looking at. */
    public static void tick() {
        if (phase != Phase.ANCHORED) {
            return;
        }
        BlockPos target = targetedBlock();
        if (target != null && !target.equals(pointB)) {
            pointB = target;
            refreshPreview();
        }
    }

    public static @Nullable AABB bounds() {
        if (pointA == null || pointB == null) {
            return null;
        }
        return new AABB(
                Math.min(pointA.getX(), pointB.getX()),
                Math.min(pointA.getY(), pointB.getY()),
                Math.min(pointA.getZ(), pointB.getZ()),
                Math.max(pointA.getX(), pointB.getX()) + 1.0D,
                Math.max(pointA.getY(), pointB.getY()) + 1.0D,
                Math.max(pointA.getZ(), pointB.getZ()) + 1.0D);
    }

    public static int spanX() {
        return pointA == null || pointB == null ? 0 : Math.abs(pointA.getX() - pointB.getX()) + 1;
    }

    public static int spanY() {
        return pointA == null || pointB == null ? 0 : Math.abs(pointA.getY() - pointB.getY()) + 1;
    }

    public static int spanZ() {
        return pointA == null || pointB == null ? 0 : Math.abs(pointA.getZ() - pointB.getZ()) + 1;
    }

    /**
     * Measure the selection locally so the panel can show floors and area before committing.
     *
     * <p>The client has the same blocks loaded as the server, so it gets the same answer. It is a
     * preview only: the server measures again on commit and its number is the one that counts.
     */
    private static void refreshPreview() {
        if (pointA == null || pointB == null) {
            return;
        }
        if (pointB.equals(lastPreviewedB)) {
            return;
        }
        lastPreviewedB = pointB;

        BlockPos min = new BlockPos(
                Math.min(pointA.getX(), pointB.getX()),
                Math.min(pointA.getY(), pointB.getY()),
                Math.min(pointA.getZ(), pointB.getZ()));
        BlockPos max = new BlockPos(
                Math.max(pointA.getX(), pointB.getX()),
                Math.max(pointA.getY(), pointB.getY()),
                Math.max(pointA.getZ(), pointB.getZ()));

        long volume = (long) spanX() * spanY() * spanZ();
        Level level = Minecraft.getInstance().level;
        if (level == null || volume > MAX_PREVIEW_VOLUME || StructureScanner.validate(min, max) != null) {
            clearPreview();
            return;
        }

        StructureScanner.Measurement measured = StructureScanner.measure(level, min, max, measureMode);
        previewFloors = measured.floors().size();
        previewCells = measured.usableCells();
    }

    private static void clearPreview() {
        previewFloors = -1;
        previewCells = -1;
        lastPreviewedB = null;
    }

    /** What the player is pointing at, or a point in front of them if that is nothing. */
    private static @Nullable BlockPos targetedBlock() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return null;
        }
        HitResult hit = minecraft.hitResult;
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            return blockHit.getBlockPos();
        }
        Vec3 eye = minecraft.player.getEyePosition();
        Vec3 ahead = eye.add(minecraft.player.getLookAngle().scale(AIR_REACH));
        return BlockPos.containing(ahead);
    }

    /** Wipe state on disconnect so a new world does not inherit the last one's half-drawn box. */
    public static void reset() {
        cancel();
        type = StructureType.RESIDENTIAL;
        measureMode = MeasureMode.FLOORS;
    }
}
