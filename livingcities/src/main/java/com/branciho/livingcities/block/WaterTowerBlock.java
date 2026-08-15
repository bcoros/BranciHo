package com.branciho.livingcities.block;

import com.branciho.livingcities.config.LivingCitiesConfig;
import com.branciho.livingcities.utility.UtilityComponent;
import com.branciho.livingcities.utility.UtilityKind;
import com.branciho.livingcities.utility.UtilityRole;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;

/**
 * Storage and pressure for the water network: the plumbing equivalent of a transformer.
 *
 * <p>A network delivers the lesser of what it pumps and what it can push, so a city that adds houses
 * faster than towers starts running dry at the far end even though the pumps are keeping up. Towers
 * are also the obvious thing to put on a hill, which is where real ones go.
 */
public class WaterTowerBlock extends Block implements UtilityComponent {

    public static final MapCodec<WaterTowerBlock> CODEC = simpleCodec(WaterTowerBlock::new);

    public WaterTowerBlock(Properties properties) {
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
        return UtilityRole.TRANSFORMER;
    }

    @Override
    public int throughput() {
        return LivingCitiesConfig.SERVER.waterTowerThroughput.get();
    }
}
