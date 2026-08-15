package com.branciho.livingcities.block;

import com.branciho.livingcities.utility.UtilityComponent;
import com.branciho.livingcities.utility.UtilityKind;
import com.branciho.livingcities.utility.UtilityRole;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Carries water between blocks it directly touches.
 *
 * <p>A plain block, not a block entity: a city can contain thousands, and a block entity each would
 * be thousands of objects storing nothing. Pipes are meant to be buried and forgotten.
 */
public class WaterPipeBlock extends Block implements UtilityComponent {

    public static final MapCodec<WaterPipeBlock> CODEC = simpleCodec(WaterPipeBlock::new);

    private static final VoxelShape SHAPE = Block.box(5.0D, 0.0D, 5.0D, 11.0D, 6.0D, 11.0D);

    public WaterPipeBlock(Properties properties) {
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
        return UtilityKind.WATER;
    }

    @Override
    public UtilityRole utilityRole() {
        return UtilityRole.CONDUCTOR;
    }
}
