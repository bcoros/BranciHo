package com.branciho.citiesinlife.client;

import com.branciho.citiesinlife.net.ClientCityCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * What standing in fallout looks and sounds like.
 *
 * <p>Entirely client side, driven by one number the server sends once a second. The server already
 * hands out the nausea and the poison; what it cannot do is explain them, and a player whose screen
 * has started swimming with no visible cause will reasonably conclude the mod is broken rather than
 * that they are standing in a nuclear crater's fallout.
 *
 * <p>Three tells, and they arrive in the order you would want them to. The clicking is audible
 * furthest out and is the one that says <em>leave</em>; the motes in the air confirm where you are;
 * the green cast over everything only really bites near the middle, by which point the health bar
 * is saying the same thing.
 */
public final class ClientRadiation {

    /** Counts down to the next click. Shorter the hotter it gets. */
    private static int nextClick;

    private ClientRadiation() {
    }

    /**
     * The dose, from the one place that holds it.
     *
     * <p>Kept in {@link ClientCityCache} with everything else the server has told this client,
     * rather than as a second copy here - two fields updated by two different packets is how a HUD
     * and a tick end up disagreeing about whether the player is standing in anything.
     */
    private static int strength() {
        return ClientCityCache.radiation();
    }

    public static void tick(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        int strength = strength();
        if (player == null || level == null || strength <= 0) {
            return;
        }
        double dose = strength / 100.0D;
        RandomSource random = level.random;

        // The counter. Between roughly three a second at the edge and twenty a second in the
        // middle, with the interval jittered - a geiger counter that ticked like a metronome
        // would read as a machine rather than as something reacting to the air.
        if (--nextClick <= 0) {
            nextClick = Math.max(1, (int) (7.0D - 6.0D * dose) + random.nextInt(3));
            level.playLocalSound(player.getX(), player.getY(), player.getZ(),
                    SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.AMBIENT,
                    0.25F + 0.35F * (float) dose, 1.7F + random.nextFloat() * 0.3F, false);
        }

        // Motes in the air around the player rather than at the crater, because the point is that
        // it is here, in the air you are breathing, and not something happening over there.
        int motes = 1 + (int) (dose * 6.0D);
        for (int i = 0; i < motes; i++) {
            level.addParticle(ParticleTypes.HAPPY_VILLAGER,
                    player.getX() + (random.nextDouble() - 0.5D) * 8.0D,
                    player.getY() + random.nextDouble() * 3.0D,
                    player.getZ() + (random.nextDouble() - 0.5D) * 8.0D,
                    0.0D, -0.02D, 0.0D);
        }
    }

    /**
     * The colour to wash the screen with, as packed ARGB, or zero for none.
     *
     * <p>Held well below opaque even at a full dose. This is the one effect that could make the
     * game unplayable rather than unpleasant, and being unable to see is what the blindness is for.
     */
    public static int tint() {
        int strength = strength();
        if (strength <= 0) {
            return 0;
        }
        int alpha = (int) (Math.min(1.0D, strength / 100.0D) * 90.0D);
        return (alpha << 24) | 0x2FBF3F;
    }
}
