package com.branciho.citiesinlife.client;

import com.branciho.citiesinlife.net.payload.StructureSyncPayload;
import net.minecraft.world.phys.AABB;

/**
 * Structure mode: see the registrations that are otherwise invisible, and delete them.
 *
 * <p>A registered structure is a box in server data with no blocks of its own. Without a way to see
 * one, a player who demolishes a building is left with an invisible claim that blocks the ground
 * forever and reports "that overlaps something" with no way to find out what.
 *
 * <p>Deleting works by drawing a box, the same gesture that created the registration. Picking one
 * with the crosshair was tried first and was unreliable — you are aiming at something that has no
 * blocks, so there is nothing for the ray to hit except an imaginary volume.
 */
public final class StructureMode {

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

    public static AABB boundsOf(StructureSyncPayload.Entry entry) {
        return new AABB(
                entry.minX(), entry.minY(), entry.minZ(),
                entry.maxX() + 1.0D, entry.maxY() + 1.0D, entry.maxZ() + 1.0D);
    }
}
