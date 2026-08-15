package com.branciho.livingcities.block;

import com.branciho.livingcities.power.PowerComponent;
import com.branciho.livingcities.power.PowerRole;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Short-range wiring. Carries power between blocks it directly touches.
 *
 * <p>A plain block rather than a block entity on purpose: a city can contain thousands of these, and
 * a block entity each would be thousands of objects for something that stores nothing. The grid walker
 * reads them straight from the world instead.
 */
public class PowerCableBlock extends Block implements PowerComponent {

    public static final MapCodec<PowerCableBlock> CODEC = simpleCodec(PowerCableBlock::new);

    private static final VoxelShape SHAPE = Block.box(6.0D, 0.0D, 6.0D, 10.0D, 4.0D, 10.0D);

    public PowerCableBlock(Properties properties) {
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
    public PowerRole powerRole() {
        return PowerRole.CONDUCTOR;
    }
}
