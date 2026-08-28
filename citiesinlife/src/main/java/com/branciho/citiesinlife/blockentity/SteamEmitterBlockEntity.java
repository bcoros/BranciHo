package com.branciho.citiesinlife.blockentity;

import com.branciho.citiesinlife.config.CitiesInLifeConfig;
import com.branciho.citiesinlife.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The steam emitter's plume.
 *
 * <p>It looks like decoration and it is not. A plant with no working emitter has nowhere to put the
 * heat it is shedding, so its four cooling ports foul — heated side first — until they latch shut
 * one after another and the core walks past the meltdown line. The emitter is the reactor's
 * chimney, and it fails the same way a chimney does: quietly, until it does not.
 *
 * <p>Blocked counts as missing. A plume that cannot get out is a plume that is not getting out,
 * whatever the block above is made of.
 */
public class SteamEmitterBlockEntity extends BlockEntity {

    /** How long a "the plant is running" signal from the reactor stays good, in ticks. */
    private static final int EMIT_WINDOW = 220;

    /** Particles per level of the column while venting. */
    private static final int PLUME_DENSITY = 3;

    /**
     * How far up the plume climbs, in blocks.
     *
     * <p>A cooling tower's plume is the tallest thing on the site and is visible from the far side
     * of the map; three particles hovering at head height above the block were not that. The column
     * is drawn as a stack of small bursts rather than left to the particles' own drift, because a
     * cloud particle rises about a block and a half before it fades - the height has to be placed,
     * not waited for.
     */
    private static final int PLUME_HEIGHT = 34;

    /** Blocks between one puff of the column and the next. */
    private static final int PLUME_STEP = 2;

    private long emitUntil = -1L;

    public SteamEmitterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STEAM_EMITTER.get(), pos, state);
    }

    /** Told by the reactor, once per simulation step, that the plant is generating. */
    public void keepEmitting(long gameTime) {
        emitUntil = gameTime + EMIT_WINDOW;
        setChanged();
    }

    public boolean emitting(long gameTime) {
        return gameTime < emitUntil;
    }

    /** Whether the block above lets a plume out. A capped emitter does the plant no good at all. */
    public static boolean clear(Level level, BlockPos pos) {
        BlockState above = level.getBlockState(pos.above());
        return above.isAir() || !above.isSolidRender(level, pos.above());
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  SteamEmitterBlockEntity emitter) {
        if (!(level instanceof ServerLevel serverLevel) || !emitter.emitting(level.getGameTime())) {
            return;
        }
        if (!clear(level, pos)) {
            return;
        }
        // Scaled by the setting, and the height and the density come down together. Turning the
        // plume down to a quarter and leaving three particles on every remaining level would have
        // saved a quarter of the cost and looked like a stubby cloud rather than a smaller plume.
        int percent = CitiesInLifeConfig.steamPlumePercent();
        if (percent <= 0) {
            return;
        }
        int height = Math.max(2, PLUME_HEIGHT * percent / 100);
        int density = Math.max(1, PLUME_DENSITY * percent / 100);

        double x = pos.getX() + 0.5D;
        double z = pos.getZ() + 0.5D;
        // Sent per player with the force flag. The plain sendParticles drops anything more than 32
        // blocks from the viewer, which would cut a 34-block plume off at chest height for anybody
        // standing far enough back to see the whole plant.
        for (ServerPlayer player : serverLevel.players()) {
            for (int level = 1; level <= height; level += PLUME_STEP) {
                // Widening and thinning with height, so it billows out into a head instead of
                // staying a pipe of smoke all the way up.
                double climb = (double) level / height;
                double spread = 0.18D + climb * climb * 2.2D;
                serverLevel.sendParticles(player, ParticleTypes.CLOUD, true,
                        x, pos.getY() + level, z,
                        density, spread, 0.10D, spread, 0.015D);
            }
        }
        if (level.getGameTime() % 40L == 0L) {
            serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE,
                    x, pos.getY() + 1.3D, z, 2, 0.12D, 0.0D, 0.12D, 0.02D);
            level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.22F, 1.6F);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        emitUntil = tag.getLong("emitUntil");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong("emitUntil", emitUntil);
    }
}
