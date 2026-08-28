package com.branciho.citiesinlife.nuclear;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * What is left in the air after a core goes.
 *
 * <p>The explosions are over in thirteen seconds. This is the part that is not: everything within
 * half a kilometre of the crater is in the fallout, and being outside the blast radius is not the
 * same as being safe. It fades — slowly at first, then quickly, and after five minutes the site is
 * just a hole in the ground.
 *
 * <p>Deliberately in memory rather than saved. Fallout is a consequence you live through in the
 * minutes after a meltdown, not a permanent property of a chunk, and a restart clearing it is the
 * right amount of forgiving for something the player has already paid for once.
 *
 * <p>Two ranges, for one honest reason. Players are checked at the full radius because
 * {@code level.players()} is a short list and walking it costs nothing. Other mobs are only checked
 * near the crater, because sweeping every entity in a thousand-block cube every second would cost
 * more than the entire meltdown did.
 */
public final class Radiation {

    /** How far the fallout reaches. Far enough that running was the right call. */
    public static final double RADIUS = 520.0D;

    /** How far from the crater ordinary mobs are checked. */
    private static final double MOB_RADIUS = 96.0D;

    /** Five minutes. Long enough to matter, short enough that the land comes back. */
    private static final int DURATION = 20 * 60 * 5;

    /** Checked once a second. The effects last longer than that, so they never flicker. */
    private static final int INTERVAL = 20;

    private static final int EFFECT_TICKS = 90;

    private record Source(ResourceKey<Level> dimension, Vec3 at, long startedAt) {
    }

    private static final List<Source> SOURCES = new ArrayList<>();

    private Radiation() {
    }

    /** A core has just gone. Everything around it is now in the fallout. */
    public static void fallout(ServerLevel level, BlockPos at) {
        SOURCES.add(new Source(level.dimension(), Vec3.atCenterOf(at), level.getGameTime()));
    }

    public static boolean any() {
        return !SOURCES.isEmpty();
    }

    /** Every server tick, but only does anything once a second. */
    public static void tick(MinecraftServer server) {
        if (SOURCES.isEmpty() || server.getTickCount() % INTERVAL != 0) {
            return;
        }
        Iterator<Source> sources = SOURCES.iterator();
        while (sources.hasNext()) {
            Source source = sources.next();
            ServerLevel level = server.getLevel(source.dimension());
            if (level == null) {
                sources.remove();
                continue;
            }
            long elapsed = level.getGameTime() - source.startedAt();
            if (elapsed < 0L || elapsed >= DURATION) {
                sources.remove();
                continue;
            }
            // Squared, so the air clears slowly at first and then quickly - which is both how
            // fallout behaves and the shape that gives the player time to notice and leave.
            double age = 1.0D - (double) elapsed / DURATION;
            double fade = age * age;
            expose(level, source, fade);
        }
    }

    private static void expose(ServerLevel level, Source source, double fade) {
        for (ServerPlayer player : level.players()) {
            dose(player, source, fade, RADIUS);
        }
        AABB near = AABB.ofSize(source.at(), MOB_RADIUS * 2, MOB_RADIUS * 2, MOB_RADIUS * 2);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, near,
                living -> !(living instanceof Player))) {
            dose(entity, source, fade, MOB_RADIUS);
        }
    }

    private static void dose(LivingEntity entity, Source source, double fade, double reach) {
        if (entity instanceof Player player && (player.isCreative() || player.isSpectator())) {
            return;
        }
        double distance = entity.position().distanceTo(source.at());
        if (distance > reach) {
            return;
        }
        double strength = (1.0D - distance / reach) * fade;
        if (strength <= 0.05D) {
            return;
        }
        // Nausea is the tell, and it reaches furthest: the screen swims long before anything is
        // taking damage, so "something is very wrong here" arrives before the health bar does.
        add(entity, MobEffects.CONFUSION, 0);
        if (strength > 0.15D) {
            add(entity, MobEffects.HUNGER, 0);
        }
        if (strength > 0.30D) {
            add(entity, MobEffects.WEAKNESS, 0);
            add(entity, MobEffects.POISON, 0);
        }
        if (strength > 0.55D) {
            add(entity, MobEffects.POISON, 1);
            add(entity, MobEffects.BLINDNESS, 0);
        }
        if (strength > 0.75D) {
            add(entity, MobEffects.WITHER, 1);
        }
    }

    private static void add(LivingEntity entity, Holder<MobEffect> effect, int amplifier) {
        MobEffectInstance existing = entity.getEffect(effect);
        if (existing != null && existing.getAmplifier() > amplifier) {
            return;
        }
        entity.addEffect(new MobEffectInstance(effect, EFFECT_TICKS, amplifier, true, true));
    }

    /** Dropped when a world unloads, so a new one does not inherit the last one's fallout. */
    public static void clear() {
        SOURCES.clear();
    }
}
