package com.branciho.livingcities.power;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Implemented by any block that takes part in the electrical grid.
 *
 * <p>The grid walker asks blocks what they are instead of consulting a registry of known blocks, so
 * the network code never grows a switch statement as generation types are added.
 */
public interface PowerComponent {

    PowerRole powerRole();

    /**
     * Output in kilowatts right now, for generators.
     *
     * <p>Called on the server thread during a grid rebuild, not every tick, so it is allowed to look
     * at the world - daylight, weather, sky exposure - but should stay cheap.
     */
    default int generationKw(ServerLevel level, BlockPos pos, BlockState state) {
        return 0;
    }

    /** How far a pylon reaches to link with another pylon, in blocks. Ignored for other roles. */
    default int linkRange() {
        return 0;
    }

    /** How far a substation distributes power to buildings, in blocks. Ignored for other roles. */
    default int coverageRadius() {
        return 0;
    }
}
