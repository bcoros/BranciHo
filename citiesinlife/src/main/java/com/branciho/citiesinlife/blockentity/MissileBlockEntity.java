package com.branciho.citiesinlife.blockentity;

import com.branciho.citiesinlife.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Nothing, deliberately.
 *
 * <p>A missile has no state worth keeping: which kind it is comes from the block, which way it
 * points comes from the block state, and whether it has launched is answered by whether the block
 * is still there. This exists only because a block entity renderer needs a block entity to hang
 * off, and ten blocks of rocket cannot come from a baked model — a block model may not reach
 * beyond thirty-two units above its own position, and this one is a hundred and sixty-two.
 *
 * <p>It does not tick on either side. There is nothing to tick.
 */
public class MissileBlockEntity extends BlockEntity {

    public MissileBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MISSILE.get(), pos, state);
    }
}
