package com.branciho.citiesinlife.block;

import com.branciho.citiesinlife.blockentity.CoolingPortBlockEntity;
import com.branciho.citiesinlife.nuclear.CoolingPort;
import com.branciho.citiesinlife.nuclear.ReactorReadout;
import com.branciho.citiesinlife.water.WaterBlock;
import com.branciho.citiesinlife.water.WaterGrid;
import com.branciho.citiesinlife.water.WaterRole;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * One of the four ports on the reactor's cooling loop.
 *
 * <p>One class, four registrations. They differ only in where they belong in the circuit and what
 * the survey expects on the other side of them; four near-identical files differing by a noun is
 * how those four quietly drift apart.
 *
 * <p>A port has to behave two ways at once, which is why it needs its own {@link WaterRole}. Lay a
 * pipe against it and it conducts like pipework, because {@code joinsAutomatically} says yes on
 * every face. Point the Pipe Connect Tool at it and it links like a machine — which a conduit
 * cannot do, since the tool refuses conduit-to-conduit outright.
 */
public class CoolingPortBlock extends BaseEntityBlock implements WaterBlock {

    public static final MapCodec<CoolingPortBlock> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    propertiesCodec(),
                    CoolingPort.CODEC.fieldOf("port").forGetter(CoolingPortBlock::port))
                    .apply(instance, CoolingPortBlock::new));

    /** Far enough to cross a reactor hall, since the one hand-drawn link spans the whole loop. */
    private static final int LINK_RANGE = 24;

    private final CoolingPort port;

    public CoolingPortBlock(Properties properties, CoolingPort port) {
        super(properties);
        this.port = port;
    }

    public CoolingPort port() {
        return port;
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
        return new CoolingPortBlockEntity(pos, state);
    }

    /**
     * Ask the port how the plant it belongs to is doing.
     *
     * <p>Every reactor block answers this, not just the monitor. A player who has built half a
     * reactor and cannot work out why it is dead should be able to punch any part of it and be
     * told, rather than having to build the monitor first to find out what is missing.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            player.displayClientMessage(ReactorReadout.describe(level, pos), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
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
        return WaterRole.PORT;
    }

    @Override
    public int linkRange() {
        return LINK_RANGE;
    }

    /**
     * Always yes, on every face, whatever the fouling.
     *
     * <p>A clogged port still carries water, it just carries less — and that has to stay true here,
     * because this is asked with a {@link BlockGetter} during placement when no block entity is
     * reliably reachable. A port whose answer depended on its fouling would rearrange the
     * neighbouring pipework every time it clogged.
     */
    @Override
    public boolean joinsAutomatically(BlockGetter level, BlockPos pos, BlockState state,
                                      Direction side) {
        return true;
    }
}
