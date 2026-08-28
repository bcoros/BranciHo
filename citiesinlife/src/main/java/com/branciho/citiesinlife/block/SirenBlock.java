package com.branciho.citiesinlife.block;

import com.branciho.citiesinlife.sound.MachineSounds;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * An air-raid siren: the only warning a city gets.
 *
 * <p>Without one, a missile arrives. That is the whole of it — the first you know is the crater,
 * and there is no version of that which is a game rather than a punishment. A siren turns an
 * incoming warhead into a minute of knowing, which is enough time to get out of the way, watch it
 * come in, or find out whether your interceptors work.
 *
 * <p>It answers to territory rather than to a wire. Anything flying at ground your city has claimed
 * sets off every siren that city owns, wherever they are standing, because the thing being defended
 * is the land and not the pole.
 *
 * <p>No block entity. Whether it is wailing is a block state the missile director sets, which costs
 * one state change at the start of an attack and one at the end rather than a ticking machine on
 * every pole in the city.
 */
public class SirenBlock extends Block {

    public static final MapCodec<SirenBlock> CODEC = simpleCodec(SirenBlock::new);

    public static final BooleanProperty WAILING = BooleanProperty.create("wailing");

    /** A post with a horn on top. */
    private static final VoxelShape SHAPE = Block.box(4.0D, 0.0D, 4.0D, 12.0D, 16.0D, 12.0D);

    public SirenBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(WAILING, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(WAILING);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext context) {
        return SHAPE;
    }

    public static boolean wailing(BlockState state) {
        return state.hasProperty(WAILING) && state.getValue(WAILING);
    }

    /**
     * The wail.
     *
     * <p>Louder than anything else the mod plays and still on the machine-volume dial, because a
     * siren that cannot be turned down is a siren that gets the mod uninstalled. It rides
     * {@code animateTick} like every other machine here, which means you hear it from about thirty
     * blocks — a pole in the middle of a district covers the district.
     */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        super.animateTick(state, level, pos, random);
        if (wailing(state)) {
            MachineSounds.airRaid(level, pos, random);
        }
    }
}
