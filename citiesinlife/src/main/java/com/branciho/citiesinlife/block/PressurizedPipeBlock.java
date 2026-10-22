package com.branciho.citiesinlife.block;

import com.branciho.citiesinlife.nuclear.ReactorReadout;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The pressurised pipe: an ordinary pipe with a rating plate on it.
 *
 * <p>Mechanically it carries water exactly as its parent does — it inherits every connection rule,
 * every join state and the leak. What it adds is one thing the reactor looks for: the survey
 * insists on finding one of these immediately before the cooled input, and refuses by name if it is
 * missing. The owner asked for the pressure system not to be overcomplicated, and this is the whole
 * of it: one block, in one place, that has to be there.
 *
 * <p>Subclassing WaterPipeBlock rather than copying it means the day pipes learn something new,
 * this learns it too.
 */
public class PressurizedPipeBlock extends WaterPipeBlock {

    public PressurizedPipeBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            player.displayClientMessage(ReactorReadout.describe(level, pos), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
