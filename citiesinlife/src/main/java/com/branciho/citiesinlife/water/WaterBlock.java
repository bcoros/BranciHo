package com.branciho.citiesinlife.water;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/** Implemented by every block that is part of the water system. */
public interface WaterBlock {

    WaterRole waterRole();

    /**
     * How far a hand-drawn link from this block may reach, in blocks.
     *
     * <p>A link uses the shorter of its two ends' reaches, so the relay pump's long throw only counts
     * when it is talking to another pump - which is what makes relays the thing you build a run out
     * of rather than an upgrade to everything.
     */
    default int linkRange() {
        return 16;
    }

    /** The position the network stores for this block, for anything occupying more than one place. */
    default BlockPos networkPos(BlockGetter level, BlockPos pos, BlockState state) {
        return pos;
    }

    /**
     * Whether water passes between this block and whatever is on the given side, with no tool.
     *
     * <p>Only conduits say yes, and a valve only says yes along its own axis and only while it is
     * open. Both sides have to agree before anything flows, so a pipe pointed at the closed end of a
     * valve is not a leak.
     */
    default boolean joinsAutomatically(BlockGetter level, BlockPos pos, BlockState state, Direction side) {
        return false;
    }

    /** Units this block puts into the network each simulation step. Zero for anything but a source. */
    default int waterOutput(BlockGetter level, BlockPos pos, BlockState state) {
        return 0;
    }
}
