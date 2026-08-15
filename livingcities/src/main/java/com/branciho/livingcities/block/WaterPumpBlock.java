package com.branciho.livingcities.block;

import com.branciho.livingcities.config.LivingCitiesConfig;
import com.branciho.livingcities.utility.UtilityComponent;
import com.branciho.livingcities.utility.UtilityKind;
import com.branciho.livingcities.utility.UtilityRole;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

/**
 * Draws water out of an actual lake or river.
 *
 * <p>Output scales with how much water it can reach, so a pump dropped on dry land produces nothing
 * and one sunk into a river produces a lot. That is the whole design: water has to come from somewhere
 * the player can point at, rather than appearing because a block was placed.
 *
 * <p>Deliberately checks real fluid state instead of a biome or a block id, so modded water and
 * waterlogged blocks work without a compatibility list.
 */
public class WaterPumpBlock extends Block implements UtilityComponent {

    public static final MapCodec<WaterPumpBlock> CODEC = simpleCodec(WaterPumpBlock::new);

    /** How far around the pump counts as its intake. Kept small so pumps must sit at the water. */
    private static final int INTAKE_RADIUS = 3;

    /** Sources needed for full output. Beyond this a bigger lake does not pump faster. */
    private static final int SOURCES_FOR_FULL_OUTPUT = 24;

    public WaterPumpBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public UtilityKind utilityKind() {
        return UtilityKind.WATER;
    }

    @Override
    public UtilityRole utilityRole() {
        return UtilityRole.PRODUCER;
    }

    @Override
    public int output(ServerLevel level, BlockPos pos, BlockState state) {
        int sources = countWaterAround(level, pos);
        if (sources <= 0) {
            return 0;
        }
        float scale = Math.min(1.0F, sources / (float) SOURCES_FOR_FULL_OUTPUT);
        return Math.round(LivingCitiesConfig.SERVER.waterPumpOutput.get() * scale);
    }

    /**
     * Count still water sources the pump can reach.
     *
     * <p>Bounded to a 7x7x7 box and only run during a network rebuild, not per tick, so the cost is
     * paid when plumbing changes rather than continuously.
     */
    private static int countWaterAround(ServerLevel level, BlockPos pos) {
        int found = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -INTAKE_RADIUS; dx <= INTAKE_RADIUS; dx++) {
            for (int dy = -INTAKE_RADIUS; dy <= INTAKE_RADIUS; dy++) {
                for (int dz = -INTAKE_RADIUS; dz <= INTAKE_RADIUS; dz++) {
                    cursor.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    if (!level.isLoaded(cursor)) {
                        continue;
                    }
                    // Source blocks only: flowing water is runoff, and counting it would let a player
                    // feed a city from a single bucket poured down a hillside.
                    if (level.getFluidState(cursor).getType() == Fluids.WATER
                            && level.getFluidState(cursor).isSource()) {
                        found++;
                    }
                }
            }
        }
        return found;
    }

    /** Unused placement helper kept explicit so the intake direction is obvious to a reader. */
    public static boolean hasWaterAdjacent(ServerLevel level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (level.getFluidState(pos.relative(direction)).isSource()) {
                return true;
            }
        }
        return false;
    }
}
