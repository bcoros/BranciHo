package com.branciho.citiesinlife.block;

import com.branciho.citiesinlife.blockentity.TransportAirplaneBlockEntity;
import com.branciho.citiesinlife.net.ServerActions;
import com.branciho.citiesinlife.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The transport airport: link two of them, and it is a flight between them.
 *
 * <p>One gesture does everything, and it is a plain right click with an empty hand. That is
 * deliberate and it took a rewrite to get right: the obvious design was sneak + right click to
 * link, but {@code ServerPlayerGameMode.useItemOn} skips block interaction entirely when the player
 * is sneaking <em>and</em> holding anything in either hand - so the gesture would have failed in
 * exactly the situation it is always attempted in, holding the second airport you are about to
 * place. An empty hand always reaches {@link #useWithoutItem}.
 *
 * <p>So: click one that is not linked, then click another, and they are paired. Click one that is
 * already linked, and you fly. Break either and the other quietly forgets it, because a link to a
 * block that no longer exists must not be a permanent silent failure.
 */
public class TransportAirplaneBlock extends AirfieldBlock {

    public static final MapCodec<TransportAirplaneBlock> CODEC =
            simpleCodec(TransportAirplaneBlock::new);

    public TransportAirplaneBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TransportAirplaneBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return createTickerHelper(type, ModBlockEntities.TRANSPORT_AIRPLANE.get(),
                TransportAirplaneBlockEntity::serverTick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            ServerActions.useAirplane(serverPlayer, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
