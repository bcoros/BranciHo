package com.branciho.citiesinlife.item;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Firing a gun.
 *
 * <p>Hitscan rather than a projectile entity, and that is a deliberate limitation as much as a
 * design: a bullet that arrives the moment it is fired needs no entity type, no renderer and no
 * version-specific arrow constructor, and at these ranges nobody can tell the difference. What it
 * costs is that a bullet cannot be dodged or shot out of the air.
 *
 * <p>Shared between the player pulling the trigger and a soldier doing it, so the two cannot drift
 * apart in damage or range — which they would, immediately, if they were written twice.
 */
public final class Gunfire {

    /** How far a shot carries. Beyond this it is somebody else's problem. */
    public static final double RANGE = 42.0D;

    /** What a hit is worth, and how hard it shoves. */
    private static final float DAMAGE = 6.0F;
    private static final double KNOCKBACK = 0.28D;

    /** How far the smoke trail is drawn between the muzzle and whatever stopped it. */
    private static final double TRAIL_STEP = 0.9D;

    private Gunfire() {
    }

    /**
     * Whether a soldier handed this would shoot with it or hit somebody over the head with it.
     *
     * <p>Deliberately generous. The player's own gun mod is the reason this exists, and there is no
     * way to ask an arbitrary modded item "are you a gun" — so anything that is not obviously a
     * blade, a tool or a block gets treated as one. Hand a soldier a rifle from any mod and they use
     * it as a rifle; hand them a sword and they use it as a sword.
     *
     * <p>The ballistics are ours either way. A modded gun's own aiming, recoil and ammunition are
     * driven by that mod's player-side code, which nothing here can call on an NPC's behalf.
     */
    public static boolean firearm(ItemStack held) {
        if (held.isEmpty()) {
            return false;
        }
        return !(held.getItem() instanceof SwordItem)
                && !(held.getItem() instanceof DiggerItem)
                && !(held.getItem() instanceof BlockItem);
    }

    /**
     * Take a shot from this entity's eyes along its line of sight.
     *
     * @param spread how far off true the shot may go, in blocks at maximum range; 0 is perfect
     */
    public static void fire(Level level, LivingEntity shooter, double spread) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        Vec3 eye = shooter.getEyePosition();
        Vec3 aim = shooter.getViewVector(1.0F);
        if (spread > 0.0D) {
            aim = aim.add(
                    (level.random.nextDouble() - 0.5D) * spread,
                    (level.random.nextDouble() - 0.5D) * spread,
                    (level.random.nextDouble() - 0.5D) * spread).normalize();
        }
        Vec3 end = eye.add(aim.scale(RANGE));

        BlockHitResult wall = level.clip(new ClipContext(
                eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter));
        Vec3 stop = wall.getType() == HitResult.Type.MISS ? end : wall.getLocation();

        LivingEntity struck = firstAlong(level, shooter, eye, stop);
        if (struck != null) {
            stop = struck.position().add(0.0D, struck.getBbHeight() * 0.5D, 0.0D);
            struck.hurt(level.damageSources().mobProjectile(shooter, shooter), DAMAGE);
            Vec3 shove = aim.scale(KNOCKBACK);
            struck.push(shove.x, 0.05D, shove.z);
        }

        drawTrail(serverLevel, eye, stop);
        level.playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(),
                SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.55F, 1.9F);
    }

    /**
     * The first living thing on the line, ignoring the shooter and anything lying on the floor.
     *
     * <p>Written out rather than handed to the projectile helper because that helper's signature has
     * moved between versions, and a gun that stops compiling on the next update is worse than nine
     * lines of arithmetic.
     */
    private static @Nullable LivingEntity firstAlong(Level level, LivingEntity shooter, Vec3 from, Vec3 to) {
        AABB corridor = new AABB(from, to).inflate(1.0D);
        LivingEntity closest = null;
        double best = Double.MAX_VALUE;
        for (Entity candidate : level.getEntities(shooter, corridor,
                entity -> entity.isAlive() && entity.isPickable() && !(entity instanceof ItemEntity))) {
            if (!(candidate instanceof LivingEntity living)) {
                continue;
            }
            Optional<Vec3> hit = living.getBoundingBox().inflate(0.25D).clip(from, to);
            if (hit.isEmpty()) {
                continue;
            }
            double distance = from.distanceToSqr(hit.get());
            if (distance < best) {
                best = distance;
                closest = living;
            }
        }
        return closest;
    }

    private static void drawTrail(ServerLevel level, Vec3 from, Vec3 to) {
        double length = from.distanceTo(to);
        Vec3 step = to.subtract(from).normalize().scale(TRAIL_STEP);
        Vec3 at = from;
        for (double travelled = 0.0D; travelled < length; travelled += TRAIL_STEP) {
            level.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            at = at.add(step);
        }
    }
}
