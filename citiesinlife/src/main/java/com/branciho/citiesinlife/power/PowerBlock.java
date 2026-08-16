package com.branciho.citiesinlife.power;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/** Implemented by every block the power line tool can attach to. */
public interface PowerBlock {

    PowerRole powerRole();

    /** Units produced per simulation step. Zero for anything that is not a producer. */
    default int powerOutput(BlockGetter level, BlockPos pos, BlockState state) {
        return 0;
    }

    /**
     * How far a link from this block may reach, in blocks.
     *
     * <p>A link uses the shorter of its two ends' ranges, so a mast's long reach only applies when it
     * is talking to another mast.
     */
    int linkRange();

    /**
     * The position the network actually stores for this block.
     *
     * <p>Tall blocks are several block positions but one node, so clicking any part of a mast has to
     * resolve to the same place or a line would attach to the middle of one and the foot of another.
     */
    default BlockPos networkPos(BlockGetter level, BlockPos pos, BlockState state) {
        return pos;
    }
}
