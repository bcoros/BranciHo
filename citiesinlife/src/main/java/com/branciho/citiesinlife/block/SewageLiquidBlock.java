package com.branciho.citiesinlife.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;

import java.util.function.Supplier;

/**
 * The block form of sewage: vanilla's liquid block, plus consequences.
 *
 * <p>Everything about how it spreads, pools, falls and renders is inherited. The only thing added
 * is that wading through raw effluent makes you ill — nausea and hunger rather than damage, because
 * it should be unpleasant and worth avoiding rather than a trap that kills somebody who walked
 * across their own outfall while building it.
 */
public class SewageLiquidBlock extends LiquidBlock {

    /** How often somebody standing in it is made to regret it. Two seconds. */
    private static final int SICKEN_INTERVAL = 40;

    public SewageLiquidBlock(Supplier<? extends FlowingFluid> fluid, Properties properties) {
        super(fluid.get(), properties);
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        super.entityInside(state, level, pos, entity);
        if (level.isClientSide || !(entity instanceof LivingEntity living)) {
            return;
        }
        if (level.getGameTime() % SICKEN_INTERVAL != 0L) {
            return;
        }
        living.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 120, 0, false, false));
        living.addEffect(new MobEffectInstance(MobEffects.HUNGER, 120, 0, false, false));
    }
}
