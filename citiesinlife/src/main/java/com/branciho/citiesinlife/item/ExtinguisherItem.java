package com.branciho.citiesinlife.item;

import com.branciho.citiesinlife.blockentity.TurbineBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

/**
 * A fire extinguisher, for a turbine that has been left seized long enough to catch.
 *
 * <p>It puts the fire out and nothing else. The soot that started it is still in there, so a doused
 * turbine that is not then cleaned with the wrench will simply catch again — which is the right
 * lesson, because the fire was never the actual problem.
 *
 * <p>It also puts out ordinary fire, since the alternative is standing next to a burning power
 * station holding the one tool that obviously ought to work and being told it does not.
 */
public class ExtinguisherItem extends Item {

    /** How far around the clicked block it smothers ordinary flames. */
    private static final int SPLASH = 2;

    public ExtinguisherItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        if (level.isClientSide || player == null) {
            return InteractionResult.SUCCESS;
        }

        boolean didSomething = false;
        if (level.getBlockEntity(pos) instanceof TurbineBlockEntity turbine && turbine.douse()) {
            player.displayClientMessage(
                    Component.translatable("message.citiesinlife.doused_turbine"), true);
            didSomething = true;
        }

        // Sweep the ordinary flames around it too.
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = -SPLASH; x <= SPLASH; x++) {
            for (int y = -SPLASH; y <= SPLASH; y++) {
                for (int z = -SPLASH; z <= SPLASH; z++) {
                    cursor.set(pos.getX() + x, pos.getY() + y, pos.getZ() + z);
                    if (level.getBlockState(cursor).getBlock() instanceof BaseFireBlock) {
                        level.setBlockAndUpdate(cursor, Blocks.AIR.defaultBlockState());
                        didSomething = true;
                    }
                }
            }
        }

        if (!didSomething) {
            player.displayClientMessage(
                    Component.translatable("message.citiesinlife.nothing_burning"), true);
            return InteractionResult.SUCCESS;
        }

        level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.0F, 1.0F);
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.CLOUD,
                    pos.getX() + 0.5D, pos.getY() + 1.2D, pos.getZ() + 0.5D,
                    24, 0.6D, 0.5D, 0.6D, 0.02D);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.citiesinlife.extinguisher_1")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.citiesinlife.extinguisher_2")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
