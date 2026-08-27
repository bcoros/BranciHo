package com.branciho.citiesinlife.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * A fuel rod: uranium goes in here, and heat comes out.
 *
 * <p>Four columns drive one nuclear turbine, and each column has to be at least eight blocks tall
 * before it does anything at all. Build them taller and they produce more - the height of a reactor
 * hall is the dial you turn to make it a bigger reactor, which is why the rods are placed a block at
 * a time rather than dropped in as a finished machine.
 *
 * <p>The green bar down the side is the fuel level, and it is a block state rather than a block
 * entity on purpose. A sixteen-block column is sixteen blocks; six columns is ninety-six. Ninety-six
 * ticking block entities to display one number that the reactor already knows would be an absurd
 * price for a meter. The reactor owns the fuel figure and pushes it out to the states it lights.
 */
public class FuelRodBlock extends ReactorRodBlock {

    public static final MapCodec<FuelRodBlock> CODEC = simpleCodec(FuelRodBlock::new);

    /** How much of the green meter is lit: 0 is empty, 4 is a full rod. */
    public static final IntegerProperty FILL = IntegerProperty.create("fill", 0, 4);

    public static final int MAX_FILL = 4;

    public FuelRodBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FILL, 0));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FILL);
    }
}
