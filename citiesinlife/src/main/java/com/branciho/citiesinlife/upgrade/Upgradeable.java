package com.branciho.citiesinlife.upgrade;

import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A machine that can be made better with the Upgrade Tool.
 *
 * <p>Declared on the <em>block</em> rather than on the block entity, which is the only choice that
 * works: the starter pump keeps its tier in a block state and has no block entity at all, while the
 * turbine and the sewage collector both have one. Asking the block means the tool never has to know
 * which of those it is holding.
 *
 * <p>Every machine decides its own ceiling and its own price. Nothing here is generic beyond the
 * gesture, because "one more level of pump" and "one more level of turbine" are worth different
 * amounts to a city and pricing them the same would make one of them the only sensible purchase.
 */
public interface Upgradeable {

    /** The highest tier this machine can reach. Tier 0 is what it is when placed. */
    int maxTier();

    int tierAt(BlockGetter level, BlockPos pos, BlockState state);

    /**
     * What it costs to buy the next level up from the given tier.
     *
     * <p>Takes the current tier rather than being one number, so upgrades get dearer as they go.
     */
    long upgradeCost(int fromTier);

    /**
     * Actually apply the upgrade. Called only after the money has been taken.
     *
     * @return false if it could not be applied after all, in which case the caller refunds
     */
    boolean upgrade(Level level, BlockPos pos, BlockState state);

    /** One line saying what this machine does at its current tier. Shown after every upgrade. */
    Component describe(BlockGetter level, BlockPos pos, BlockState state);
}
