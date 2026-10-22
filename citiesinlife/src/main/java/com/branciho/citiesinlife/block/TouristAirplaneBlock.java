package com.branciho.citiesinlife.block;

import com.branciho.citiesinlife.blockentity.TouristAirplaneBlockEntity;
import com.branciho.citiesinlife.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The tourist airport: put one down and visitors start turning up around it.
 *
 * <p>Nothing to configure and nothing to link. It is the simple half of the pair on purpose - a
 * building that makes the city look visited, and the reward for having a city worth visiting.
 */
public class TouristAirplaneBlock extends AirfieldBlock {

    public static final MapCodec<TouristAirplaneBlock> CODEC = simpleCodec(TouristAirplaneBlock::new);

    public TouristAirplaneBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TouristAirplaneBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return createTickerHelper(type, ModBlockEntities.TOURIST_AIRPLANE.get(),
                TouristAirplaneBlockEntity::serverTick);
    }
}
