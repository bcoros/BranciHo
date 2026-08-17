package com.branciho.citiesinlife.block;

import com.branciho.citiesinlife.water.WaterRole;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

/**
 * The starter pump: where water gets into the system.
 *
 * <p>It has to be able to reach water — sitting in a lake, or on the bank with the river below or
 * beside it. That is the whole of its cost. Water is not a resource you buy, it is a place you have
 * to build near, which is the decision this block exists to force.
 *
 * <p>One per pumping station. Two starter pumps on the same run of links is refused when the link is
 * drawn rather than quietly ignored later.
 */
public class StarterPumpBlock extends AbstractPumpBlock {

    /** Units a working intake lifts per simulation step. */
    public static final int OUTPUT = 40;

    public StarterPumpBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected Component statusOf(Level level, BlockPos pos) {
        return Component.translatable(touchesWater(level, pos)
                ? "message.citiesinlife.pump_wet"
                : "message.citiesinlife.pump_dry");
    }

    @Override
    public WaterRole waterRole() {
        return WaterRole.SOURCE;
    }

    @Override
    public int linkRange() {
        return 24;
    }

    @Override
    public int waterOutput(BlockGetter level, BlockPos pos, BlockState state) {
        return touchesWater(level, pos) ? OUTPUT : 0;
    }

    /**
     * Whether there is water to draw from.
     *
     * <p>Checked generously: the pump's own space, the block under it, and the four beside it. A
     * pump placed into a lake displaces the water it was standing in, so insisting on water at its
     * exact position would break the most obvious way to place one.
     */
    public static boolean touchesWater(BlockGetter level, BlockPos pos) {
        if (isWater(level, pos) || isWater(level, pos.below())) {
            return true;
        }
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (isWater(level, pos.relative(direction))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isWater(BlockGetter level, BlockPos pos) {
        return level.getFluidState(pos).is(Fluids.WATER)
                || level.getFluidState(pos).is(Fluids.FLOWING_WATER);
    }
}
