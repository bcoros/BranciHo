package com.branciho.citiesinlife.blockentity;

import com.branciho.citiesinlife.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
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

    /** Particles per tick while venting. Enough to read from across a city, cheap enough to spam. */
    private static final int PLUME_DENSITY = 3;

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
        // sendParticles, not addParticle: this runs on the server, and addParticle would be a
        // no-op that looks exactly like the emitter being broken.
        serverLevel.sendParticles(ParticleTypes.CLOUD,
                pos.getX() + 0.5D, pos.getY() + 1.1D, pos.getZ() + 0.5D,
                PLUME_DENSITY, 0.18D, 0.05D, 0.18D, 0.04D);
        if (level.getGameTime() % 40L == 0L) {
            serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE,
                    pos.getX() + 0.5D, pos.getY() + 1.3D, pos.getZ() + 0.5D,
                    2, 0.12D, 0.0D, 0.12D, 0.02D);
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
