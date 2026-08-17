package com.branciho.citiesinlife.item;

import com.branciho.citiesinlife.block.WaterPipeBlock;
import com.branciho.citiesinlife.blockentity.TurbineBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * A wrench, for the two things in this mod that break rather than stop.
 *
 * <p>A turbine fouled by a boiler with no chimney, and a pipe that has split. Both are states you
 * arrive at by running a plant badly rather than by building it wrong, so both want a repair rather
 * than a demolition — being told to break and replace a fifteen-block machine because it needed
 * cleaning would be a poor lesson.
 */
public class RepairToolItem extends Item {

    public RepairToolItem(Properties properties) {
        super(properties);
    }

    /**
     * Repair on the way in, before the block sees the click.
     *
     * <p>Using {@code onItemUseFirst} rather than {@code useOn} keeps the wrench from working a
     * valve or opening a boiler on the way past: the server runs a block's own right-click handler
     * before the item's, so anything that waits for {@code useOn} has already set something off.
     */
    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        if (level.isClientSide || player == null) {
            return InteractionResult.SUCCESS;
        }

        BlockState state = level.getBlockState(pos);

        if (level.getBlockEntity(pos) instanceof TurbineBlockEntity turbine) {
            if (turbine.repair()) {
                say(level, pos, player, "message.citiesinlife.wrench_turbine", true);
            } else {
                say(level, pos, player, "message.citiesinlife.wrench_turbine_clean", false);
            }
            return InteractionResult.SUCCESS;
        }

        if (state.getBlock() instanceof WaterPipeBlock && state.hasProperty(WaterPipeBlock.LEAKING)) {
            if (state.getValue(WaterPipeBlock.LEAKING)) {
                level.setBlock(pos, state.setValue(WaterPipeBlock.LEAKING, false), Block.UPDATE_ALL);
                say(level, pos, player, "message.citiesinlife.wrench_pipe", true);
            } else {
                say(level, pos, player, "message.citiesinlife.wrench_pipe_clean", false);
            }
            return InteractionResult.SUCCESS;
        }

        say(level, pos, player, "message.citiesinlife.wrench_nothing", false);
        return InteractionResult.SUCCESS;
    }

    private static void say(Level level, BlockPos pos, Player player, String key, boolean worked) {
        player.displayClientMessage(Component.translatable(key), true);
        if (worked) {
            level.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.5F, 1.4F);
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.citiesinlife.wrench_1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.citiesinlife.wrench_2").withStyle(ChatFormatting.DARK_GRAY));
    }
}
