package com.branciho.citiesinlife.block;

import com.branciho.citiesinlife.blockentity.ChimneyBlockEntity;
import com.branciho.citiesinlife.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A brick flue for a power plant's smoke.
 *
 * <p>It has no logic of its own. A boiler finds it by looking inside the Turbine Power Plant it was
 * registered as part of, the same way it finds the turbine, and sends its emissions out of the top of
 * whichever chimney is actually open to the air.
 *
 * <p>Open to the air is the one rule: nothing solid directly on top. Blocks may sit all around it —
 * you are meant to be able to build the stack into a wall or a roof — but a capped chimney is not a
 * chimney.
 */
public class ChimneyBlock extends Block implements EntityBlock {

    /** Slightly inset, so a run of them reads as a stack rather than as a column of full cubes. */
    private static final VoxelShape SHAPE = Block.box(2.0D, 0.0D, 2.0D, 14.0D, 16.0D, 14.0D);

    public ChimneyBlock(Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ChimneyBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                           BlockEntityType<T> type) {
        if (level.isClientSide || type != ModBlockEntities.CHIMNEY.get()) {
            return null;
        }
        @SuppressWarnings("unchecked")
        BlockEntityTicker<T> ticker =
                (BlockEntityTicker<T>) (BlockEntityTicker<ChimneyBlockEntity>) ChimneyBlockEntity::serverTick;
        return ticker;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /**
     * Wake up chimneys that were built before this block had any insides.
     *
     * <p>Block entities are restored from what the chunk saved. A chimney placed in an older version
     * saved nothing, so on loading it stayed a plain block with no ticker and never smoked — the new
     * house-chimney feature simply did not exist on any chimney anybody already owned. Asking for the
     * block entity is what creates and registers it, and a random tick is a free place to ask.
     *
     * <p>Costs nothing on a chimney that already has one: the second call returns the same object.
     */
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        level.getBlockEntity(pos);
    }

    /**
     * Whether smoke can actually leave this chimney.
     *
     * <p>Another chimney overhead does not count as open — the smoke would just be arriving at the
     * one above, which is checked in its own right. That way a three-block stack behaves the way it
     * looks: only the top of it is the outlet.
     */
    public static boolean isOpen(LevelReader level, BlockPos pos) {
        return !level.getBlockState(pos.above()).blocksMotion();
    }
}
