package com.branciho.citiesinlife.entity.ai;

import com.branciho.citiesinlife.entity.Homebound;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Walk back to where you came from, and go in.
 *
 * <p>The last thing a tourist or an off-duty service worker does. It exists because the alternative
 * — vanishing on the spot the moment the bookkeeping said so — is indistinguishable from a bug to
 * anybody watching, and this mod's people are watched: they are most of what a city looks like.
 *
 * <p>Walked in <b>hops</b> rather than in one go, and that is the load-bearing detail. Vanilla
 * pathfinding will not deliver a path much beyond a mob's FOLLOW_RANGE — 32 blocks for a tourist,
 * 48 for a service worker — and asking for one silently returns nothing at all. A visitor two
 * hundred blocks from the airport would be told to walk there, be given no path, and stand in the
 * street forever. So each leg aims at a point a short way along the straight line home and the goal
 * simply keeps issuing new ones, which vanilla handles perfectly well.
 *
 * <p>And it gives up. A door behind a wall, a bridge somebody mined, a hill the pathfinder will not
 * climb — all of them end with somebody stuck halfway home, which is a worse bug than the one this
 * replaces. After ninety seconds of trying they leave from wherever they got to.
 */
public class GoHomeGoal<T extends PathfinderMob & Homebound> extends Goal {

    /** How far ahead each leg aims. Comfortably inside what vanilla will path. */
    private static final int HOP = 20;

    /** Close enough to the door to have gone in. */
    private static final double ARRIVED = 3.0D;

    /** How long they get before they leave from wherever they are. Ninety seconds. */
    private static final int PATIENCE = 20 * 90;

    /** How often a new leg is issued. Long enough to actually walk one. */
    private static final int REPATH_INTERVAL = 30;

    private final T mob;
    private final double speed;

    private int spent;
    private int untilRepath;

    public GoHomeGoal(T mob, double speed) {
        this.mob = mob;
        this.speed = speed;
        setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return mob.leaving() && mob.homeBlock() != null;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse() && spent < PATIENCE;
    }

    @Override
    public void start() {
        spent = 0;
        untilRepath = 0;
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
        // Only when the patience genuinely ran out. stop() is also called whenever a
        // higher-priority goal takes the MOVE flag - swimming, most obviously - and vanishing
        // somebody because they fell in a canal on the way home would be a far stranger bug than
        // the one this goal exists to fix.
        if (mob.leaving() && spent >= PATIENCE) {
            vanish();
        }
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        BlockPos home = mob.homeBlock();
        if (home == null) {
            return;
        }
        spent++;

        if (mob.distanceToSqr(home.getX() + 0.5D, home.getY(), home.getZ() + 0.5D)
                <= ARRIVED * ARRIVED) {
            vanish();
            return;
        }

        if (--untilRepath > 0) {
            return;
        }
        untilRepath = REPATH_INTERVAL;

        // The next leg: a point HOP blocks along the line home, or home itself if that is nearer.
        Vec3 here = mob.position();
        Vec3 there = new Vec3(home.getX() + 0.5D, home.getY(), home.getZ() + 0.5D);
        Vec3 step = there.subtract(here);
        double distance = step.length();
        Vec3 aim = distance <= HOP ? there : here.add(step.scale(HOP / distance));
        mob.getNavigation().moveTo(aim.x, aim.y, aim.z, speed);
    }

    /** In through the door, with just enough of a puff that it reads as going inside. */
    private void vanish() {
        if (mob.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.POOF,
                    mob.getX(), mob.getY() + 1.0D, mob.getZ(), 6, 0.2D, 0.3D, 0.2D, 0.01D);
            level.playSound(null, mob.blockPosition(), SoundEvents.WOODEN_DOOR_CLOSE,
                    SoundSource.NEUTRAL, 0.5F, 1.0F);
        }
        mob.discard();
    }
}
