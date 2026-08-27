package com.branciho.citiesinlife.block;

import com.branciho.citiesinlife.upgrade.Upgradeable;
import com.branciho.citiesinlife.water.WaterRole;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
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
public class StarterPumpBlock extends AbstractPumpBlock implements Upgradeable {

    /**
     * How good this particular intake is. Two upgrades, so 0, 1 or 2.
     *
     * <p>A block state rather than a block entity, because this is the whole of what an upgraded
     * pump remembers and a block entity ticking away on every pump in the world to hold one small
     * number would be an unreasonable price for it.
     */
    public static final IntegerProperty TIER = IntegerProperty.create("tier", 0, 2);

    public static final int MAX_TIER = 2;

    /** Units a working intake lifts per simulation step at tier 0. */
    public static final int OUTPUT = 40;

    /** Each upgrade adds this much again. Tiers 0-2 give 40, 60, 80. */
    public static final int OUTPUT_PER_TIER = 20;

    /** What the first upgrade costs. The second is twice this. */
    private static final long UPGRADE_BASE_COST = 750L;

    public StarterPumpBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(TIER, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(TIER);
    }

    /** What an intake at this tier lifts, working. */
    public static int outputAt(int tier) {
        return OUTPUT + tier * OUTPUT_PER_TIER;
    }

    @Override
    protected Component statusOf(Level level, BlockPos pos) {
        int tier = level.getBlockState(pos).hasProperty(TIER)
                ? level.getBlockState(pos).getValue(TIER) : 0;
        return Component.translatable(touchesWater(level, pos)
                        ? "message.citiesinlife.pump_wet"
                        : "message.citiesinlife.pump_dry",
                outputAt(tier), tier + 1);
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
        if (!touchesWater(level, pos)) {
            return 0;
        }
        return outputAt(state.hasProperty(TIER) ? state.getValue(TIER) : 0);
    }

    // ------------------------------------------------------------- upgrading

    @Override
    public int maxTier() {
        return MAX_TIER;
    }

    @Override
    public int tierAt(BlockGetter level, BlockPos pos, BlockState state) {
        return state.hasProperty(TIER) ? state.getValue(TIER) : 0;
    }

    @Override
    public long upgradeCost(int fromTier) {
        return UPGRADE_BASE_COST * (fromTier + 1);
    }

    @Override
    public boolean upgrade(Level level, BlockPos pos, BlockState state) {
        int tier = tierAt(level, pos, state);
        if (tier >= MAX_TIER) {
            return false;
        }
        level.setBlock(pos, state.setValue(TIER, tier + 1), Block.UPDATE_ALL);
        return true;
    }

    @Override
    public Component describe(BlockGetter level, BlockPos pos, BlockState state) {
        int tier = tierAt(level, pos, state);
        return Component.translatable("message.citiesinlife.upgraded_pump",
                tier + 1, outputAt(tier));
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
