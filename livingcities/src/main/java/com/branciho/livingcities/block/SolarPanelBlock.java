package com.branciho.livingcities.block;

import com.branciho.livingcities.config.LivingCitiesConfig;
import com.branciho.livingcities.utility.UtilityComponent;
import com.branciho.livingcities.utility.UtilityKind;
import com.branciho.livingcities.utility.UtilityRole;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Solar generation. Output depends on daylight, sky exposure and weather.
 *
 * <p>Cheap, clean and useless at night, which is the trade the player is meant to feel: an array big
 * enough to run a city by day leaves it dark at 3am unless something else covers the gap.
 */
public class SolarPanelBlock extends Block implements UtilityComponent {

    public static final MapCodec<SolarPanelBlock> CODEC = simpleCodec(SolarPanelBlock::new);

    private static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 3.0D, 16.0D);

    public SolarPanelBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public UtilityKind utilityKind() {
        return UtilityKind.POWER;
    }

    @Override
    public UtilityRole utilityRole() {
        return UtilityRole.PRODUCER;
    }

    @Override
    public int output(ServerLevel level, BlockPos pos, BlockState state) {
        // Buried or roofed panels produce nothing, which is the intuitive result and needs no
        // explaining in a tooltip.
        if (!level.canSeeSky(pos.above())) {
            return 0;
        }
        if (!level.isDay()) {
            return 0;
        }
        int output = LivingCitiesConfig.SERVER.solarPanelKw.get();
        if (level.isRaining() || level.isThundering()) {
            output = Math.max(1, output / 3);
        }
        return output;
    }
}
