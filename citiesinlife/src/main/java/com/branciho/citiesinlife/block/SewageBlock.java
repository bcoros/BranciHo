package com.branciho.citiesinlife.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;

/**
 * What comes out of the outfall.
 *
 * <p>A block rather than a fluid, and that is a deliberate limitation rather than a shortcut. A real
 * fluid would spread, and a spreading fluid discharged by a machine that runs unattended is a
 * world-eating accident waiting for the first player who points an outfall at their own base. This
 * sits exactly where it is put, in the one cell in front of the end pipe, and vanishes when the
 * sewer stops.
 *
 * <p>You can walk through it. You should not want to: standing in it makes you ill.
 */
public class SewageBlock extends Block {

    /** Ankle deep, so it reads as a puddle rather than as a brown glass cube. */
    private static final VoxelShape PUDDLE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 4.0D, 16.0D);

    /** How often somebody standing in it is made to regret it. Two seconds. */
    private static final int SICKEN_INTERVAL = 40;

    public SewageBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext context) {
        return PUDDLE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                           CollisionContext context) {
        return Shapes.empty();
    }

    /**
     * Wading through raw sewage does you no good.
     *
     * <p>Nausea and hunger rather than damage. It should be unpleasant and worth avoiding, not a
     * trap that kills somebody who walked across their own outfall while building it.
     */
    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
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
