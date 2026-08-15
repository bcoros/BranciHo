package com.branciho.livingcities.block;

import com.branciho.livingcities.config.LivingCitiesConfig;
import com.branciho.livingcities.utility.UtilityComponent;
import com.branciho.livingcities.utility.UtilityKind;
import com.branciho.livingcities.utility.UtilityRole;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;

/**
 * Raises how much a network can actually carry.
 *
 * <p>Generation alone does not light a city: a network delivers the lesser of what it produces and
 * what its transformers can carry. That is what turns transmission into something you plan rather
 * than a formality, and it gives a clear failure the UI can name - "your plant makes 500 kW and your
 * transformers carry 200".
 */
public class TransformerBlock extends Block implements UtilityComponent {

    public static final MapCodec<TransformerBlock> CODEC = simpleCodec(TransformerBlock::new);

    public TransformerBlock(Properties properties) {
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
        return UtilityRole.TRANSFORMER;
    }

    @Override
    public int throughput() {
        return LivingCitiesConfig.SERVER.transformerThroughputKw.get();
    }
}
