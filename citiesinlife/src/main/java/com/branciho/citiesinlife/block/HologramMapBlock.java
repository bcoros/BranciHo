package com.branciho.citiesinlife.block;

import com.branciho.citiesinlife.blockentity.HologramMapBlockEntity;
import com.branciho.citiesinlife.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import com.branciho.citiesinlife.net.ServerActions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A projection table: a plinth with a slowly turning globe of light standing over it.
 *
 * <p>Click it and it tells you who is standing on your ground — everyone inside the chunks your
 * city has claimed, and nobody else. That limit is the whole design. A map that showed every player
 * in the world would be a wallhack with a nice model on it; a map that shows your own territory is
 * a thing a city hall would plausibly have, and it answers the question anybody actually has, which
 * is whether they are alone in their own city.
 *
 * <p>Like everything else in the hall, it only works inside a registered city core, and only for
 * the city that owns it. The server re-checks both on every request; the panel is told which of the
 * two failed so it can say so rather than showing an empty list that looks broken.
 */
public class HologramMapBlock extends BaseEntityBlock {

    public static final MapCodec<HologramMapBlock> CODEC = simpleCodec(HologramMapBlock::new);

    /** The plinth. The projection above it is light and has no business stopping anybody. */
    private static final VoxelShape SHAPE = Block.box(1.0D, 0.0D, 1.0D, 15.0D, 12.0D, 15.0D);

    public HologramMapBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /** The globe is its own light source, which is most of why it reads as a projection. */
    public static int light() {
        return 10;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HologramMapBlockEntity(pos, state);
    }

    /**
     * Ticked only on the client, and only to turn the globe.
     *
     * <p>There is nothing for the server to do here: the table holds no state, and the one question
     * it can answer is asked by the panel over the network when somebody is actually looking.
     */
    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, ModBlockEntities.HOLOGRAM_MAP.get(),
                        HologramMapBlockEntity::clientTick)
                : null;
    }

    /**
     * Open the projection.
     *
     * <p>Asked for from the server rather than opened here, because a block's use handler runs
     * server-side and this class must never so much as name a screen: the first dedicated server to
     * load it would take the client class with it and fall over.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer viewer) {
            ServerActions.openHologram(viewer);
        }
        return InteractionResult.CONSUME;
    }
}
