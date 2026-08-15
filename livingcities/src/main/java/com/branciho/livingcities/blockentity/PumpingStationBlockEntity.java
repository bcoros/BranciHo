package com.branciho.livingcities.blockentity;

import com.branciho.livingcities.registry.ModBlockEntities;
import com.branciho.livingcities.utility.DistributorIndex;
import com.branciho.livingcities.utility.UtilityGrid;
import com.branciho.livingcities.utility.UtilityKind;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Registers its pumping station with the water index while its chunk is loaded. */
public class PumpingStationBlockEntity extends BlockEntity {

    public PumpingStationBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PUMPING_STATION.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel serverLevel) {
            DistributorIndex.get(serverLevel.getServer())
                    .add(UtilityKind.WATER, serverLevel.dimension(), worldPosition);
            UtilityGrid.get(serverLevel.getServer()).markDirty(UtilityKind.WATER, serverLevel.dimension());
        }
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel serverLevel) {
            DistributorIndex.get(serverLevel.getServer())
                    .remove(UtilityKind.WATER, serverLevel.dimension(), worldPosition);
            UtilityGrid.get(serverLevel.getServer()).markDirty(UtilityKind.WATER, serverLevel.dimension());
        }
        super.setRemoved();
    }
}
