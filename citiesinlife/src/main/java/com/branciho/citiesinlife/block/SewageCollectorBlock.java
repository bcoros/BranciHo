package com.branciho.citiesinlife.block;

import com.branciho.citiesinlife.blockentity.SewageCollectorBlockEntity;
import com.branciho.citiesinlife.city.CityData;
import com.branciho.citiesinlife.upgrade.Upgradeable;
import com.branciho.citiesinlife.water.WaterBlock;
import com.branciho.citiesinlife.water.WaterGrid;
import com.branciho.citiesinlife.water.WaterRole;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The sewage collector: where a city's used water goes.
 *
 * <p>Stand it inside your borders and plumb it into a pipe connector with the Pipe Connect Tool,
 * exactly as you would a water tank - the gesture is the same because it is the same network. Then
 * run pipes <em>out</em> of the city and finish with an end pipe. That end pipe is the outfall, and
 * what comes out of it is brown.
 *
 * <p>The outfall has to be outside every city's borders. Not as an arbitrary rule: sewage discharged
 * inside a city is the city's problem, and letting it count would mean the whole utility could be
 * satisfied by a pipe two blocks long. Dumping it somewhere is the point.
 *
 * <p>How much it can shift is its capacity, the way a power station has an output and a pump has a
 * throughput, and three upgrades take it from 40 to 130.
 */
public class SewageCollectorBlock extends BaseEntityBlock implements WaterBlock, Upgradeable {

    public static final MapCodec<SewageCollectorBlock> CODEC = simpleCodec(SewageCollectorBlock::new);

    /** What the first upgrade costs. Each one after is dearer by the same again. */
    private static final long UPGRADE_BASE_COST = 900L;

    public SewageCollectorBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SewageCollectorBlockEntity(pos, state);
    }

    /**
     * Right click to ask whether it has anywhere to send anything.
     *
     * <p>The "standing outside any city" case is answered here rather than by the collector itself,
     * and it has to be: a collector on unclaimed ground is never visited by the city simulation at
     * all, so its own idea of how it is doing was last updated never. It would have reported "no
     * outfall reachable" and sent the player off to check pipework that was fine.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (level instanceof ServerLevel serverLevel
                && CityData.get(serverLevel.getServer()).cityAtChunk(serverLevel.dimension(),
                        ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4)) == null) {
            player.displayClientMessage(
                    Component.translatable("message.citiesinlife.sewage_no_city"), true);
            return InteractionResult.CONSUME;
        }
        if (level.getBlockEntity(pos) instanceof SewageCollectorBlockEntity collector) {
            player.displayClientMessage(collector.status(), true);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState,
                            boolean moved) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            WaterGrid.get(serverLevel.getServer()).removeNode(level.dimension(), pos);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public WaterRole waterRole() {
        return WaterRole.SEWAGE;
    }

    @Override
    public int linkRange() {
        return 24;
    }

    // ------------------------------------------------------------- upgrading

    @Override
    public int maxTier() {
        return SewageCollectorBlockEntity.MAX_TIER;
    }

    @Override
    public int tierAt(BlockGetter level, BlockPos pos, BlockState state) {
        return level.getBlockEntity(pos) instanceof SewageCollectorBlockEntity collector
                ? collector.tier() : 0;
    }

    @Override
    public long upgradeCost(int fromTier) {
        return UPGRADE_BASE_COST * (fromTier + 1);
    }

    @Override
    public boolean upgrade(Level level, BlockPos pos, BlockState state) {
        return level.getBlockEntity(pos) instanceof SewageCollectorBlockEntity collector
                && collector.upgrade();
    }

    @Override
    public Component describe(BlockGetter level, BlockPos pos, BlockState state) {
        int capacity = level.getBlockEntity(pos) instanceof SewageCollectorBlockEntity collector
                ? collector.capacity() : SewageCollectorBlockEntity.BASE_CAPACITY;
        return Component.translatable("message.citiesinlife.upgraded_sewage",
                tierAt(level, pos, state) + 1, capacity);
    }
}
