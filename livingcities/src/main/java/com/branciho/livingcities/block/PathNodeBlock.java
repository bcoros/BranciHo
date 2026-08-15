package com.branciho.livingcities.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A vertex in the city's pedestrian graph.
 *
 * <p>NPCs route over this graph rather than asking vanilla pathfinding to understand a whole city.
 * The block has no collision so it can be placed in the middle of a sidewalk without tripping players.
 */
public class PathNodeBlock extends Block {

    public static final MapCodec<PathNodeBlock> CODEC = simpleCodec(PathNodeBlock::new);

    private static final VoxelShape SHAPE = Block.box(6.0D, 0.0D, 6.0D, 10.0D, 2.0D, 10.0D);

    public PathNodeBlock(Properties properties) {
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
