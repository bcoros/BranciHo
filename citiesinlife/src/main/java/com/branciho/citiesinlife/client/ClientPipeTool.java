package com.branciho.citiesinlife.client;

import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * The half-finished pipe link: one end clicked, waiting for the other.
 *
 * <p>Kept apart from the power tool's pending click on purpose. Sharing one slot between the two
 * tools would mean starting a power line, switching tool, and finishing it as a water link.
 */
public final class ClientPipeTool {

    private static @Nullable BlockPos pending;

    /**
     * The half-finished outlet link, kept apart from the pipe link above.
     *
     * <p>They are made with different gestures — right click for pipes, sneak and left click for an
     * end pipe's outlet — so sharing one slot would let a player start one and finish the other.
     */
    private static @Nullable BlockPos pendingOutlet;

    private ClientPipeTool() {
    }

    public static @Nullable BlockPos pending() {
        return pending;
    }

    public static void setPending(BlockPos pos) {
        pending = pos;
    }

    public static @Nullable BlockPos pendingOutlet() {
        return pendingOutlet;
    }

    public static void setPendingOutlet(BlockPos pos) {
        pendingOutlet = pos;
    }

    public static void clear() {
        pending = null;
        pendingOutlet = null;
    }
}
