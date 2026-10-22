package com.branciho.citiesinlife.client;

import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * The half-finished power line: one end clicked, waiting for the other.
 *
 * <p>Client-side and provisional, exactly like the planner's selection. The server is told only the
 * two positions and re-checks the block types, the range and whether the line already exists.
 */
public final class ClientPowerTool {

    private static @Nullable BlockPos pending;

    private ClientPowerTool() {
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
