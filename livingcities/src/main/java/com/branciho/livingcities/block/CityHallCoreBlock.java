package com.branciho.livingcities.block;

import com.branciho.livingcities.blockentity.CityHallCoreBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The block that turns a player-built structure into a city seat.
 *
 * <p>The block itself carries no simulation state; it only holds the id of the city it belongs to.
 * All authority lives in the server-side city registry, so a player breaking or moving this block
 * cannot invent or steal a city.
 */
public class CityHallCoreBlock extends Block implements EntityBlock {

    public static final MapCodec<CityHallCoreBlock> CODEC = simpleCodec(CityHallCoreBlock::new);

    public CityHallCoreBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CityHallCoreBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (level.isClientSide()) {
            // The server decides what this interaction means and pushes the right screen back.
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof CityHallCoreBlockEntity core) {
            core.onInteract(player);
        }
        return InteractionResult.CONSUME;
    }
}
