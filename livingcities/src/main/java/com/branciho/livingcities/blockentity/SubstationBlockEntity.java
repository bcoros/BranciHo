package com.branciho.livingcities.blockentity;

import com.branciho.livingcities.power.PowerGrid;
import com.branciho.livingcities.power.SubstationIndex;
import com.branciho.livingcities.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Registers its substation with the grid index while its chunk is loaded.
 *
 * <p>Doing this from the block entity's lifecycle rather than from a saved list is what lets the index
 * rebuild itself after a restart without ever disagreeing with the blocks that actually exist.
 */
public class SubstationBlockEntity extends BlockEntity {

    public SubstationBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SUBSTATION.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel serverLevel) {
            SubstationIndex.get(serverLevel.getServer()).add(serverLevel.dimension(), worldPosition);
            PowerGrid.get(serverLevel.getServer()).markDirty(serverLevel.dimension());
        }
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel serverLevel) {
            SubstationIndex.get(serverLevel.getServer()).remove(serverLevel.dimension(), worldPosition);
            PowerGrid.get(serverLevel.getServer()).markDirty(serverLevel.dimension());
        }
        super.setRemoved();
    }
}
