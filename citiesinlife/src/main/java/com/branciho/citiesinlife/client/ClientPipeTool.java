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

    private ClientPipeTool() {
    }

    public static @Nullable BlockPos pending() {
        return pending;
    }

    public static void setPending(BlockPos pos) {
        pending = pos;
    }

    public static void clear() {
        pending = null;
    }
}
