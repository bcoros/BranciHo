package com.branciho.citiesinlife.sound;

import com.branciho.citiesinlife.config.CitiesInLifeClientConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

/**
 * What the mod's machines sound like.
 *
 * <p>Every voice in here is a vanilla sound, chosen and re-pitched. That is a constraint rather
 * than a preference — the mod ships no audio of its own — but it turns out to be most of the way
 * to the right answer anyway: a beacon's hum dropped an octave <em>is</em> a generator, and a
 * player already knows what these sounds mean before they have heard the machine.
 *
 * <p>Played locally rather than sent from the server. A machine's ambience is not an event anybody
 * else needs to know about, and doing it client side is what makes the volume setting possible at
 * all: a sound the server broadcast would be the same loudness for everybody, and the person who
 * wanted the reactor hall quieter would still be stuck with it.
 *
 * <p>Nearly all of it is driven from {@code Block#animateTick}, which the client already calls on
 * random blocks near the player a few hundred times a tick. Riding that costs nothing and gives
 * proximity for free: a machine you cannot walk near is a machine you cannot hear.
 */
public final class MachineSounds {

    /** Under this the sound is not worth mixing at all. */
    private static final float SILENT = 0.02F;

    private MachineSounds() {
    }

    /** The player's volume setting applied to a sound's designed loudness. */
    private static float scaled(float base) {
        return base * CitiesInLifeClientConfig.machineVolume();
    }

    public static void at(Level level, BlockPos pos, SoundEvent sound, float volume, float pitch) {
        at(level, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, sound, volume, pitch);
    }

    /**
     * One sound, at a point, at the player's chosen volume.
     *
     * <p>On {@link SoundSource#BLOCKS} deliberately. These are machines, and somebody who has
     * already turned the Blocks slider down in vanilla's own options has said something about what
     * they want to hear that this mod should not be exempt from.
     */
    public static void at(Level level, double x, double y, double z, SoundEvent sound,
                          float volume, float pitch) {
        float loudness = scaled(volume);
        if (loudness <= SILENT) {
            return;
        }
        level.playLocalSound(x, y, z, sound, SoundSource.BLOCKS, loudness,
                Mth.clamp(pitch, 0.5F, 2.0F), false);
    }

    // ------------------------------------------------------------- machinery

    /**
     * A turbine turning.
     *
     * <p>{@code load} is how hard it is being driven, 0 to 1, and it moves both the volume and the
     * pitch because that is what changing the speed of a real one does. A reactor at a quarter
     * throttle should sound like a machine idling, not like the same machine played quietly.
     */
    public static void turbine(Level level, BlockPos pos, RandomSource random, float load,
                               float size) {
        if (load <= 0.02F) {
            return;
        }
        float drive = Mth.clamp(load, 0.0F, 1.0F);
        at(level, pos, SoundEvents.BEACON_AMBIENT,
                size * (0.25F + 0.55F * drive),
                0.55F + 0.45F * drive + random.nextFloat() * 0.04F);
        // A second, harder layer that only arrives once it is really working, so opening the dial
        // adds a sound rather than only turning one up.
        if (drive > 0.55F && random.nextInt(3) == 0) {
            at(level, pos, SoundEvents.MINECART_RIDING,
                    size * 0.22F * drive, 0.6F + 0.3F * drive);
        }
    }

    /** A firebox with coal in it. */
    public static void boiler(Level level, BlockPos pos, RandomSource random) {
        at(level, pos, SoundEvents.FURNACE_FIRE_CRACKLE, 0.6F, 0.8F + random.nextFloat() * 0.2F);
        if (random.nextInt(4) == 0) {
            at(level, pos, SoundEvents.FIRE_AMBIENT, 0.35F, 0.6F + random.nextFloat() * 0.2F);
        }
    }

    /** Water being moved, which is most of what a pump is. */
    public static void pump(Level level, BlockPos pos, RandomSource random) {
        at(level, pos, SoundEvents.WATER_AMBIENT, 0.55F, 0.7F + random.nextFloat() * 0.25F);
        if (random.nextInt(3) == 0) {
            at(level, pos, SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, 0.3F,
                    0.55F + random.nextFloat() * 0.2F);
        }
    }

    /**
     * A big rotor going past.
     *
     * <p>A dragon's wingbeat, slowed down. Nothing else in the game is the right shape for
     * fifteen blocks of blade sweeping through the air, and slowed far enough it stops reading as
     * a dragon and starts reading as weather.
     */
    public static void windmill(Level level, BlockPos pos, RandomSource random) {
        at(level, pos, SoundEvents.ENDER_DRAGON_FLAP, 0.35F, 0.5F + random.nextFloat() * 0.1F);
    }

    /** The buzz off a line under load. Asked for as slight, and kept slight. */
    public static void mast(Level level, BlockPos pos, RandomSource random, float size) {
        at(level, pos, SoundEvents.BEE_LOOP, size * 0.14F, 0.5F + random.nextFloat() * 0.06F);
    }

    /**
     * A rod column.
     *
     * <p>Two completely different sounds, and the difference is the point. A healthy core is a
     * faint buzz you have to be next to it to notice. A core past its limits is loud, fast and
     * clicking, and it is meant to be audible from outside the building — by the time you can hear
     * this you should already be moving.
     */
    public static void rod(Level level, BlockPos pos, RandomSource random, boolean critical) {
        if (!critical) {
            at(level, pos, SoundEvents.BEE_LOOP, 0.1F, 0.55F + random.nextFloat() * 0.08F);
            return;
        }
        at(level, pos, SoundEvents.BEE_LOOP, 0.85F, 1.35F + random.nextFloat() * 0.25F);
        at(level, pos, SoundEvents.CONDUIT_AMBIENT, 0.7F, 0.6F + random.nextFloat() * 0.2F);
        // The clicking underneath it, which is the part that says radiation rather than noise.
        for (int i = 0; i < 3; i++) {
            at(level, pos, SoundEvents.STONE_BUTTON_CLICK_ON, 0.4F,
                    1.6F + random.nextFloat() * 0.4F);
        }
    }

    /**
     * A seal letting go a little.
     *
     * <p>The lid on a rod column has never had anything to say for itself. It should: it is the
     * top of a pressure vessel, and a vessel over its limits vents. Steam out of the seams is the
     * one tell for an overpressured core that is visible from outside the reactor hall.
     */
    public static void venting(Level level, BlockPos pos, RandomSource random) {
        at(level, pos, SoundEvents.LAVA_EXTINGUISH, 0.5F, 1.1F + random.nextFloat() * 0.4F);
        for (int i = 0; i < 4; i++) {
            level.addParticle(ParticleTypes.CLOUD,
                    pos.getX() + 0.5D + (random.nextDouble() - 0.5D) * 0.9D,
                    pos.getY() + 0.55D,
                    pos.getZ() + 0.5D + (random.nextDouble() - 0.5D) * 0.9D,
                    (random.nextDouble() - 0.5D) * 0.02D,
                    0.06D + random.nextDouble() * 0.05D,
                    (random.nextDouble() - 0.5D) * 0.02D);
        }
    }

    // --------------------------------------------------------------- workplaces

    /** Somebody at a keyboard. One key at a time; a room full of desks does the rest. */
    public static void typing(Level level, BlockPos pos, RandomSource random) {
        int keys = 2 + random.nextInt(4);
        for (int i = 0; i < keys; i++) {
            at(level, pos, SoundEvents.WOODEN_BUTTON_CLICK_ON, 0.22F,
                    1.6F + random.nextFloat() * 0.35F);
        }
    }

    /** A till: a scan, and now and then the drawer. */
    public static void till(Level level, BlockPos pos, RandomSource random) {
        at(level, pos, SoundEvents.NOTE_BLOCK_BELL.value(), 0.22F,
                1.5F + random.nextFloat() * 0.3F);
        if (random.nextInt(4) == 0) {
            at(level, pos, SoundEvents.WOODEN_DOOR_OPEN, 0.2F, 1.7F);
        }
    }

    // ---------------------------------------------------------------- traffic

    /**
     * An engine.
     *
     * <p>A minecart's rumble, pitched to the speed it is doing, which is close enough to a small
     * petrol engine that nobody will ask what it actually is. Played from the car's own tick
     * rather than from {@code animateTick} for the obvious reason: it moves.
     */
    public static void engine(Level level, double x, double y, double z, RandomSource random,
                              float speed) {
        at(level, x, y, z, SoundEvents.MINECART_RIDING,
                0.28F + 0.3F * Mth.clamp(speed, 0.0F, 1.0F),
                0.7F + 0.5F * Mth.clamp(speed, 0.0F, 1.0F) + random.nextFloat() * 0.05F);
    }
}
