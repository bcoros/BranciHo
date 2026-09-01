package com.branciho.citiesinlife.block;

import com.branciho.citiesinlife.water.WaterBlock;
import com.branciho.citiesinlife.water.WaterGrid;
import com.branciho.citiesinlife.water.WaterRole;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A coupling: a fat block of pipe that joins on all six faces at once.
 *
 * <p>Pipes taper to their arms, which looks right in a straight run and wrong where four of them
 * meet or where a run has to butt up against the flat face of a tank. This is the fitting for those
 * places. It carries water exactly as a pipe does and exists purely so a junction can be built
 * without it looking like an accident.
 */
public class PipeConnectorBlock extends Block implements WaterBlock {

    public PipeConnectorBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            // A conduit is a legal end of a hand-drawn link, so breaking one has to take its links
            // with it. Without this the link outlives the block, cannot be cut - the cut gesture
            // needs a block to click and the server rejects a position that is no longer a water
            // block - and silently adopts whatever gets placed there next.
            WaterGrid.get(serverLevel.getServer()).removeNode(level.dimension(), pos);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public WaterRole waterRole() {
        return WaterRole.CONDUIT;
    }

    @Override
    public boolean joinsAutomatically(BlockGetter level, BlockPos pos, BlockState state, Direction side) {
        return true;
    }
}
