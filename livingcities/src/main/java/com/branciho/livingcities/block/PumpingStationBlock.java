package com.branciho.livingcities.block;

import com.branciho.livingcities.blockentity.PumpingStationBlockEntity;
import com.branciho.livingcities.config.LivingCitiesConfig;
import com.branciho.livingcities.utility.UtilityComponent;
import com.branciho.livingcities.utility.UtilityGrid;
import com.branciho.livingcities.utility.UtilityKind;
import com.branciho.livingcities.utility.UtilityNetwork;
import com.branciho.livingcities.utility.UtilityRole;
import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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
 * Where the water network meets the city, mirroring what a substation does for power.
 *
 * <p>Buildings are served by sitting inside its radius, not by having a pipe run to each one. Right
 * clicking reports supply and demand, which is the only diagnostic that matters when a district is
 * dry and the reason is not obvious.
 */
public class PumpingStationBlock extends Block implements EntityBlock, UtilityComponent {

    public static final MapCodec<PumpingStationBlock> CODEC = simpleCodec(PumpingStationBlock::new);

    public PumpingStationBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PumpingStationBlockEntity(pos, state);
    }

    @Override
    public UtilityKind utilityKind() {
        return UtilityKind.WATER;
    }

    @Override
    public UtilityRole utilityRole() {
        return UtilityRole.DISTRIBUTOR;
    }

    @Override
    public int coverageRadius() {
        return LivingCitiesConfig.SERVER.pumpingStationRadius.get();
    }

    /** Enough on its own for a small district; a city needs water towers behind it. */
    @Override
    public int throughput() {
        return LivingCitiesConfig.SERVER.pumpingStationThroughput.get();
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (level instanceof ServerLevel serverLevel) {
            UtilityNetwork network = UtilityGrid.get(serverLevel.getServer())
                    .networkCovering(UtilityKind.WATER, serverLevel, pos);
            if (network == null) {
                player.displayClientMessage(Component.translatable("message.livingcities.water_pending")
                        .withStyle(ChatFormatting.GRAY), false);
            } else {
                ChatFormatting colour = network.isOverloaded() ? ChatFormatting.RED : ChatFormatting.AQUA;
                player.displayClientMessage(Component.translatable("message.livingcities.water_status",
                        network.deliverable(), network.demand(),
                        Math.round(network.satisfaction() * 100.0F)).withStyle(colour), false);
            }
        }
        return InteractionResult.CONSUME;
    }
}
