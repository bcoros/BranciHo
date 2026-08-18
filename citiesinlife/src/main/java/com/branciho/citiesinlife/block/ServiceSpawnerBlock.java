package com.branciho.citiesinlife.block;

import com.branciho.citiesinlife.blockentity.ServiceSpawnerBlockEntity;
import com.branciho.citiesinlife.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Where a service's people come from.
 *
 * <p>One block for six jobs. It does not need telling which service it runs: it looks at the
 * registered building it is standing inside and becomes that. Put one in a police station and
 * officers come out of it; move the same block into a hospital and it staffs the hospital instead.
 * There is deliberately no way to set it wrong, because the alternative — six near-identical blocks,
 * or a menu of six options — is six chances to put the wrong one down and no clue that you have.
 *
 * <p>Right-clicking cycles how many people it may have on the street at once, and says what it
 * thinks it is. Standing in nothing in particular, it says that too rather than doing nothing
 * silently.
 */
public class ServiceSpawnerBlock extends BaseEntityBlock {

    public static final MapCodec<ServiceSpawnerBlock> CODEC = simpleCodec(ServiceSpawnerBlock::new);

    public ServiceSpawnerBlock(Properties properties) {
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
        return new ServiceSpawnerBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return createTickerHelper(type, ModBlockEntities.SERVICE_SPAWNER.get(),
                ServiceSpawnerBlockEntity::serverTick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof ServiceSpawnerBlockEntity spawner) {
            spawner.cycleLevel();
            player.displayClientMessage(spawner.report(), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof ServiceSpawnerBlockEntity spawner) {
            // The people it sent out belong to it. Leaving them behind would mean a station could be
            // knocked down and its officers would keep walking the beat for a city that no longer
            // employs them.
            spawner.sendEverybodyHome();
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
