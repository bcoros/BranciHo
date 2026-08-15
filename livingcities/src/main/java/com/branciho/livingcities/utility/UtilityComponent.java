package com.branciho.livingcities.utility;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Implemented by any block that participates in a utility network.
 *
 * <p>The walker asks blocks what they are rather than consulting a list of known blocks, so adding a
 * generator or a pump later needs no change to the network code.
 */
public interface UtilityComponent {

    UtilityKind utilityKind();

    UtilityRole utilityRole();

    /**
     * Supply produced right now, in this utility's units.
     *
     * <p>Called during a network rebuild rather than every tick, so it may look at the world -
     * daylight, weather, adjacent water - but should stay cheap.
     */
    default int output(ServerLevel level, BlockPos pos, BlockState state) {
        return 0;
    }

    /** Throughput this block contributes, for transformers and pumping equipment. */
    default int throughput() {
        return 0;
    }

    /** How far a pylon reaches to link with another pylon, in blocks. */
    default int linkRange() {
        return 0;
    }

    /** How far a distributor serves buildings, in blocks. */
    default int coverageRadius() {
        return 0;
    }
}
