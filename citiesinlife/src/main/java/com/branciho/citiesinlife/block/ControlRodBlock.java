package com.branciho.citiesinlife.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * A control rod: the thing standing between a reactor and a crater.
 *
 * <p>Two for the whole reactor, however many turbines it drives. Fuel scales with the turbines and
 * control does not, which is deliberate: a second turbine is a second set of four fuel columns, but
 * the core it heats is still one core and one core needs one pair of hands on it.
 *
 * <p>They carry no fuel and produce nothing; what they do is decide how much of the fuel rods' heat
 * actually reaches the core, which is what makes the cooler and heat levers mean anything. A reactor
 * with fuel and no control rods is not a reactor that runs badly - it is one that will not start,
 * and being refused is far kinder than being allowed.
 *
 * <p>{@code INSERTION} is how far down they are driven, 0 fully withdrawn to 4 fully in, and it is
 * driven by the levers rather than by hand. It exists as a block state so the column visibly moves
 * when the reactor is brought under control, which is the one piece of feedback that makes the
 * inside of a reactor hall worth looking at.
 */
public class ControlRodBlock extends ReactorRodBlock {

    public static final MapCodec<ControlRodBlock> CODEC = simpleCodec(ControlRodBlock::new);

    public static final IntegerProperty INSERTION = IntegerProperty.create("insertion", 0, 4);

    public static final int MAX_INSERTION = 4;

    public ControlRodBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(INSERTION, MAX_INSERTION));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(INSERTION);
    }
}
