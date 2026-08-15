package com.branciho.livingcities.block;

import com.branciho.livingcities.blockentity.SubstationBlockEntity;
import com.branciho.livingcities.config.LivingCitiesConfig;
import com.branciho.livingcities.power.PowerComponent;
import com.branciho.livingcities.power.PowerGrid;
import com.branciho.livingcities.power.PowerNetwork;
import com.branciho.livingcities.power.PowerRole;
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
 * The point where the grid meets the city.
 *
 * <p>Buildings are powered by being within a substation's radius, not by having a cable run to them.
 * The brief is explicit that nobody should be wiring every apartment in a tower individually, so the
 * player's job is to get power <em>to the district</em> and the substation handles the last step.
 *
 * <p>Right-clicking reports the grid's supply and demand, which is the only diagnostic that matters
 * when the lights are off and it is not obvious why.
 */
public class SubstationBlock extends Block implements EntityBlock, PowerComponent {

    public static final MapCodec<SubstationBlock> CODEC = simpleCodec(SubstationBlock::new);

    public SubstationBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SubstationBlockEntity(pos, state);
    }

    @Override
    public PowerRole powerRole() {
        return PowerRole.SUBSTATION;
    }

    @Override
    public int coverageRadius() {
        return LivingCitiesConfig.SERVER.substationRadius.get();
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (level instanceof ServerLevel serverLevel) {
            PowerNetwork network = PowerGrid.get(serverLevel.getServer()).networkCovering(serverLevel, pos);
            if (network == null) {
                player.displayClientMessage(Component.translatable("message.livingcities.grid_pending")
                        .withStyle(ChatFormatting.GRAY), false);
            } else {
                ChatFormatting colour = network.isOverloaded() ? ChatFormatting.RED : ChatFormatting.GREEN;
                player.displayClientMessage(Component.translatable("message.livingcities.grid_status",
                        network.generationKw(), network.demandKw(),
                        Math.round(network.satisfaction() * 100.0F)).withStyle(colour), false);
            }
        }
        return InteractionResult.CONSUME;
    }
}
