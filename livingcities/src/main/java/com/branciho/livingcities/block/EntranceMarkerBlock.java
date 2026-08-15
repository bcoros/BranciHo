package com.branciho.livingcities.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Designates a doorway that NPCs should enter and leave a building through.
 *
 * <p>Deliberately a plain block rather than a block entity: there can be many of these in a city and
 * they hold no per-tick state. Ownership and meaning are resolved by the building that contains them.
 */
public class EntranceMarkerBlock extends Block {

    public static final MapCodec<EntranceMarkerBlock> CODEC = simpleCodec(EntranceMarkerBlock::new);

    private static final VoxelShape SHAPE = Block.box(2.0D, 0.0D, 2.0D, 14.0D, 1.0D, 14.0D);

    public EntranceMarkerBlock(Properties properties) {
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
}
