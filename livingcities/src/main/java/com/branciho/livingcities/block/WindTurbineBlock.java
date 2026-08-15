package com.branciho.livingcities.block;

import com.branciho.livingcities.config.LivingCitiesConfig;
import com.branciho.livingcities.utility.UtilityComponent;
import com.branciho.livingcities.utility.UtilityKind;
import com.branciho.livingcities.utility.UtilityRole;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Wind generation. Output rises with height and requires open sky.
 *
 * <p>Rewards putting turbines where a real one goes - on a ridge, clear of the city - rather than in
 * a basement. Unlike solar it keeps working at night, which is what makes it worth the altitude.
 *
 * <p>This is a single block rather than the multiblock the original brief sketched. A tower, nacelle,
 * hub and blades assembled by hand would be a better toy, but it is a whole feature on its own; the
 * simulation only needs to know how much power arrives. The multiblock can replace this later without
 * anything else changing.
 */
public class WindTurbineBlock extends Block implements UtilityComponent {

    public static final MapCodec<WindTurbineBlock> CODEC = simpleCodec(WindTurbineBlock::new);

    /** Below this the air is too sheltered to be worth anything. */
    private static final int MIN_USEFUL_Y = 72;

    /** Output stops climbing here, so there is no reason to build a turbine at the height limit. */
    private static final int PEAK_Y = 160;

    public WindTurbineBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
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
        if (!level.canSeeSky(pos.above())) {
            return 0;
        }
        int y = pos.getY();
        if (y < MIN_USEFUL_Y) {
            return 0;
        }
        float altitude = Math.min(1.0F, (y - MIN_USEFUL_Y) / (float) (PEAK_Y - MIN_USEFUL_Y));
        int peak = LivingCitiesConfig.SERVER.windTurbineKw.get();
        // A third of peak at the minimum useful height, full output at the top of the band.
        int output = Math.round(peak * (0.33F + 0.67F * altitude));
        if (level.isThundering()) {
            output = Math.round(output * 1.25F);
        }
        return output;
    }
}
