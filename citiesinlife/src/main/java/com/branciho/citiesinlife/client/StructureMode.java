package com.branciho.citiesinlife.client;

import com.branciho.citiesinlife.net.ClientCityCache;
import com.branciho.citiesinlife.net.payload.StructureSyncPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Structure mode: see the registrations that are otherwise invisible, and delete them.
 *
 * <p>A registered structure is a box in server data with no blocks of its own. Without a way to see
 * one, a player who demolishes a building is left with an invisible claim that blocks the ground
 * forever and reports "that overlaps something" with no way to find out what. This mode is the
 * answer to that, and it is why deleting is built in the same breath as seeing.
 *
 * <p>Deleting is sneak + right click, not the attack key. The attack key is contested by several
 * systems at once and the first attempt at this simply never fired; right click is the same path
 * that already places selection corners, so it is known to work.
 */
public final class StructureMode {

    /** How far the delete ray reaches. */
    private static final double PICK_RANGE = 96.0D;

    private static boolean active;

    private StructureMode() {
    }

    public static boolean active() {
        return active;
    }

    public static void toggle() {
        active = !active;
    }

    public static void deactivate() {
        active = false;
    }

    /**
     * The registered structure the player is looking at.
     *
     * <p>Picks the nearest box the view ray enters, so standing outside a district and pointing at
     * it selects the building in front rather than one behind it.
     */
    public static @Nullable StructureSyncPayload.Entry lookingAt() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return null;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(PICK_RANGE));

        StructureSyncPayload.Entry nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        for (StructureSyncPayload.Entry entry : ClientCityCache.structures()) {
            AABB box = boundsOf(entry);
            Optional<Vec3> hit = box.clip(eye, end);
            double distance;
            if (hit.isPresent()) {
                distance = eye.distanceToSqr(hit.get());
            } else if (box.contains(eye)) {
                // Standing inside a structure counts as looking at it; otherwise you could never
                // delete the registration for the building you are stood in.
                distance = 0.0D;
            } else {
                continue;
            }
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = entry;
            }
        }
        return nearest;
    }

    public static AABB boundsOf(StructureSyncPayload.Entry entry) {
        return new AABB(
                entry.minX(), entry.minY(), entry.minZ(),
                entry.maxX() + 1.0D, entry.maxY() + 1.0D, entry.maxZ() + 1.0D);
    }
}
