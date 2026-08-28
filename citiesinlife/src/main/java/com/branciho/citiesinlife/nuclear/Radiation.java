package com.branciho.citiesinlife.nuclear;

import com.branciho.citiesinlife.net.CitiesInLifeNetwork;
import com.branciho.citiesinlife.net.payload.RadiationPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * What is left in the air after a core goes.
 *
 * <p>The explosions are over in thirteen seconds. This is the part that is not: everything within
 * half a kilometre of the crater is in the fallout, and being outside the blast radius is not the
 * same as being safe. It fades — slowly at first, then quickly, and after ten minutes the site is
 * just a hole in the ground.
 *
 * <p>Deliberately in memory rather than saved. Fallout is a consequence you live through in the
 * minutes after a meltdown, not a permanent property of a chunk, and a restart clearing it is the
 * right amount of forgiving for something the player has already paid for once.
 *
 * <p>It doses <em>everything</em> alive, and the way it finds what to dose is the only compromise
 * in here. Sweeping every entity in a kilometre-wide cube once a second would cost more than the
 * entire meltdown did, so it sweeps around the crater and around each player who is themselves
 * standing in it. Which comes to the same thing from where anybody is looking: the mobs that get
 * cooked are the ones near the middle and the ones near you, and nothing that anybody could see
 * walks out of this unharmed.
 */
public final class Radiation {

    /** How far the fallout reaches. Far enough that running was the right call. */
    public static final double RADIUS = 520.0D;

    /** How far around the crater everything alive is checked, watched or not. */
    private static final double CRATER_SWEEP = 96.0D;

    /** How far around a player who is in the fallout everything else is checked. */
    private static final double NEARBY_SWEEP = 64.0D;

    /** Ten minutes. Long enough that leaving is a decision, short enough that the land comes back. */
    private static final int DURATION = 20 * 60 * 10;

    /** Checked once a second. The effects last longer than that, so they never flicker. */
    private static final int INTERVAL = 20;

    private static final int EFFECT_TICKS = 90;

    /** Below this the air is clean enough not to bother anybody. */
    private static final double MINIMUM = 0.05D;

    /** Everything the fallout hands out, so it can be taken back off again in one go. */
    private static final List<Holder<MobEffect>> SYMPTOMS = List.of(
            MobEffects.CONFUSION, MobEffects.HUNGER, MobEffects.WEAKNESS,
            MobEffects.POISON, MobEffects.BLINDNESS, MobEffects.WITHER);

    private record Source(ResourceKey<Level> dimension, Vec3 at, long startedAt) {
    }

    private static final List<Source> SOURCES = new ArrayList<>();

    /**
     * Players who were last told they are standing in something.
     *
     * <p>The client has no idea any of this exists and only knows what it is sent, so somebody has
     * to send the zero. Without this list nobody ever did: the number stopped arriving the instant
     * a player stopped qualifying, and the last one to arrive stayed on screen — which is why
     * switching to creative left the green cast and the clicking running forever.
     */
    private static final Set<UUID> DOSED = new HashSet<>();

    private Radiation() {
    }

    /** A core has just gone. Everything around it is now in the fallout. */
    public static void fallout(ServerLevel level, BlockPos at) {
        SOURCES.add(new Source(level.dimension(), Vec3.atCenterOf(at), level.getGameTime()));
    }

    public static boolean any() {
        return !SOURCES.isEmpty();
    }

    /**
     * Whether this entity is standing in fallout at all.
     *
     * <p>Asked by the protection rules, which otherwise make a city's own people the only living
     * things in the world that a meltdown cannot touch. Being immune to your own reactor is not a
     * protection anybody wanted.
     */
    public static boolean inFallout(LivingEntity entity) {
        return entity.level() instanceof ServerLevel level
                && strengthAt(level, entity.position()) > MINIMUM;
    }

    /** Every server tick, but only does anything once a second. */
    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % INTERVAL != 0) {
            return;
        }
        // Not "no sources": a source that has just expired still owes everybody who was standing
        // in it one last zero, and returning here before sending it is how it got stuck before.
        if (SOURCES.isEmpty() && DOSED.isEmpty()) {
            return;
        }
        prune(server);
        for (ServerLevel level : server.getAllLevels()) {
            expose(level);
        }
    }

    /** Drop anything that has burned itself out, or whose world has gone. */
    private static void prune(MinecraftServer server) {
        SOURCES.removeIf(source -> {
            ServerLevel level = server.getLevel(source.dimension());
            if (level == null) {
                return true;
            }
            long elapsed = level.getGameTime() - source.startedAt();
            return elapsed < 0L || elapsed >= DURATION;
        });
    }

    /**
     * How much fallout is in the air at one point, from whichever crater is worst.
     *
     * <p>The maximum rather than the sum. Two meltdowns beside each other are not twice as lethal
     * as one — one is already as bad as this gets — and adding them would let a player build a
     * dose above what the scale can express.
     */
    private static double strengthAt(ServerLevel level, Vec3 at) {
        double worst = 0.0D;
        for (Source source : SOURCES) {
            if (!source.dimension().equals(level.dimension())) {
                continue;
            }
            long elapsed = level.getGameTime() - source.startedAt();
            if (elapsed < 0L || elapsed >= DURATION) {
                continue;
            }
            double distance = at.distanceTo(source.at());
            if (distance > RADIUS) {
                continue;
            }
            // Squared, so the air clears slowly at first and then quickly - which is both how
            // fallout behaves and the shape that gives the player time to notice and leave.
            double age = 1.0D - (double) elapsed / DURATION;
            worst = Math.max(worst, (1.0D - distance / RADIUS) * age * age);
        }
        return worst;
    }

    private static void expose(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            boolean exempt = player.isCreative() || player.isSpectator();
            double strength = exempt ? 0.0D : strengthAt(level, player.position());
            tell(player, strength, exempt);
            if (strength <= MINIMUM) {
                continue;
            }
            dose(player, strength);
            // Everything standing near somebody who is in it. This is the half of the sweep that
            // makes "it damages everybody" true where anybody can see it happening.
            sweep(level, player.position(), NEARBY_SWEEP);
        }
        // And the middle of the crater regardless of whether anybody is watching, so a hole in
        // the ground is not quietly a wildlife sanctuary.
        for (Source source : SOURCES) {
            if (source.dimension().equals(level.dimension())) {
                sweep(level, source.at(), CRATER_SWEEP);
            }
        }
    }

    /** Dose everything alive in a box, each by its own distance from the nearest crater. */
    private static void sweep(ServerLevel level, Vec3 centre, double reach) {
        AABB box = AABB.ofSize(centre, reach * 2, reach * 2, reach * 2);
        // Players are handled one by one above, where the exemptions and the packet live.
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box,
                living -> !(living instanceof Player))) {
            double strength = strengthAt(level, entity.position());
            if (strength > MINIMUM) {
                dose(entity, strength);
            }
        }
    }

    /**
     * What standing in it does to one living thing.
     *
     * <p>The same ladder for a player, a citizen and a zombie. A meltdown is the one thing in this
     * mod that does not care whose side anybody is on.
     */
    private static void dose(LivingEntity entity, double strength) {
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

    /**
     * Tell one player how much of it they are standing in.
     *
     * <p>Sent rather than inferred, because the client has no idea any of this exists: the effects
     * a player picks up here are ordinary vanilla nausea and poison, and "why is my screen
     * swimming" is not a question the game should leave them to answer. With a number they get a
     * green cast over everything, green motes in the air and a counter clicking faster the closer
     * they are, which between them say radiation without a single line of text.
     *
     * <p>Which cuts both ways, and used not to. A zero is as much a message as anything else: the
     * green goes, the clicking stops. Sending it only while somebody qualifies meant that the
     * moment they stopped qualifying — walked out, or switched to creative — the last number they
     * were sent simply stayed on screen for the rest of the session.
     */
    private static void tell(ServerPlayer player, double strength, boolean exempt) {
        int packed = (int) Math.round(Mth.clamp(strength, 0.0D, 1.0D) * 100.0D);
        if (packed > 0) {
            DOSED.add(player.getUUID());
            CitiesInLifeNetwork.sendTo(player, new RadiationPayload(packed));
            return;
        }
        if (!DOSED.remove(player.getUUID())) {
            return;
        }
        CitiesInLifeNetwork.sendTo(player, new RadiationPayload(0));
        if (exempt) {
            // Switched to creative mid-dose. The effects would wear off on their own in a few
            // seconds, but "I am in creative and still being poisoned" is not a state worth
            // making somebody sit through - and creative is where you go to stop being in it.
            for (Holder<MobEffect> symptom : SYMPTOMS) {
                player.removeEffect(symptom);
            }
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
        DOSED.clear();
    }
}
