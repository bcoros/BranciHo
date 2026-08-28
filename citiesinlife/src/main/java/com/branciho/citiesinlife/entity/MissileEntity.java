package com.branciho.citiesinlife.entity;

import com.branciho.citiesinlife.missile.MissileKind;
import com.branciho.citiesinlife.missile.Warhead;
import com.branciho.citiesinlife.registry.ModEntities;
import com.branciho.citiesinlife.sound.MachineSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A missile in the air.
 *
 * <p>A plain {@link Entity}, like the car, because it needs none of what a Mob brings: no goals, no
 * pathfinding, no attributes. It knows where it left from, where it is going and how long it has
 * been in the air, and every tick it works out where it should be from those three numbers. Nothing
 * about it is simulated — a rocket that fell out of the sky because the server hiccuped would be a
 * strange kind of weapon.
 *
 * <p>The path is a <b>parabola</b>, and that is most of the feature. The obvious version teleports
 * the warhead to its target and detonates, and it is over before anybody looks up. This climbs,
 * arcs, and comes down nose-first over the better part of a minute, in the open, where the city it
 * is aimed at can see it coming and the city that fired it can watch it go. Everything else —
 * sirens, interceptors, the warning — only means anything because the flight takes time.
 *
 * <p>Apex scales with distance. A shot across a valley should not go to the edge of the world and a
 * shot across a continent should not skim the treetops.
 */
public class MissileEntity extends Entity {

    private static final EntityDataAccessor<Byte> DATA_KIND =
            SynchedEntityData.defineId(MissileEntity.class, EntityDataSerializers.BYTE);

    /** Ticks in the air per block of ground covered, and the floor and ceiling on the whole trip. */
    private static final double TICKS_PER_BLOCK = 0.055D;
    private static final int MIN_FLIGHT = 20 * 25;
    private static final int MAX_FLIGHT = 20 * 45;

    /** How high the arc goes, as a fraction of the distance, and the limits on that. */
    private static final double APEX_SHARE = 0.42D;
    private static final double MIN_APEX = 70.0D;
    private static final double MAX_APEX = 260.0D;

    /** An interceptor goes almost straight up and does not need forty seconds to do it. */
    private static final int INTERCEPT_FLIGHT = 20 * 8;

    private Vec3 from = Vec3.ZERO;
    private Vec3 to = Vec3.ZERO;
    private double apex;
    private int flightTicks = MIN_FLIGHT;
    private int age;

    /** Whose it is, so the warhead knows who to blame and the map knows whose track to draw. */
    private @Nullable UUID cityId;

    /** What this one is chasing, for an interceptor. Null for anything on its way to a city. */
    private @Nullable UUID targetMissile;

    public MissileEntity(EntityType<? extends MissileEntity> type, Level level) {
        super(type, level);
        // It follows an arithmetic path rather than falling, and nothing may shove it off it.
        setNoGravity(true);
        this.noPhysics = true;
        // Ten blocks of rocket on a two-block hitbox: a hitbox-sized cull test pops it out of view.
        this.noCulling = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_KIND, (byte) MissileKind.BALLISTIC.ordinal());
    }

    // ------------------------------------------------------------- launching

    /**
     * Point it at something and let go.
     *
     * <p>Everything the flight needs is settled here, once. Working the arc out per tick from the
     * current position would let a rounding error compound over nine hundred ticks into a warhead
     * that misses the city it was aimed at.
     */
    public void aim(MissileKind kind, Vec3 origin, Vec3 target, @Nullable UUID owner) {
        setKind(kind);
        this.from = origin;
        this.to = target;
        this.cityId = owner;

        double distance = Math.sqrt(
                Math.pow(target.x - origin.x, 2.0D) + Math.pow(target.z - origin.z, 2.0D));
        this.apex = Mth.clamp(distance * APEX_SHARE, MIN_APEX, MAX_APEX);
        this.flightTicks = kind == MissileKind.INTERCEPTOR
                ? INTERCEPT_FLIGHT
                : Mth.clamp((int) (distance * TICKS_PER_BLOCK * 20.0D), MIN_FLIGHT, MAX_FLIGHT);
        this.age = 0;
        setPos(origin.x, origin.y, origin.z);
    }

    /** Send this one up after another. It meets it or it does not; either way it is spent. */
    public void chase(Vec3 origin, MissileEntity quarry) {
        aim(MissileKind.INTERCEPTOR, origin, quarry.position(), null);
        this.targetMissile = quarry.getUUID();
    }

    // ---------------------------------------------------------------- flying

    /**
     * Fly.
     *
     * <p>The path is worked out on the <b>server only</b>, and the client is simply told where the
     * rocket is. It has to be that way: where it came from, where it is going and how long it takes
     * live in NBT, which the client never sees — so a client that tried to derive the arc itself
     * would compute it between the origin and the origin, put the missile at world zero, and be
     * yanked back by the next position packet. Every tick. That is a rocket vibrating between the
     * sky and the middle of the map, pointing nowhere, and it is exactly what happened.
     *
     * <p>So the client does the two things it genuinely owns — the smoke and the noise — and takes
     * the position and the angle off the wire like any other entity. This is the same division the
     * car already uses.
     */
    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            trail();
            return;
        }

        age++;
        float progress = Mth.clamp((float) age / flightTicks, 0.0F, 1.0F);
        Vec3 next = pointAt(progress);
        // Where it was a moment ago, so it can be pointed along its own path rather than at its
        // target - the difference is the whole reason it looks like it is flying and not sliding.
        Vec3 previous = pointAt(Math.max(0.0F, progress - 0.01F));
        setPos(next.x, next.y, next.z);
        aimAlong(next.subtract(previous));

        if (age >= flightTicks) {
            arrive();
        }
    }

    /**
     * Where the rocket is at a given fraction of its flight.
     *
     * <p>Straight line across the ground, parabola in the air. The vertical term peaks at the
     * halfway point and is zero at both ends, so it leaves the pad and reaches its target at
     * exactly the heights it was given.
     */
    private Vec3 pointAt(float progress) {
        double x = Mth.lerp(progress, from.x, to.x);
        double z = Mth.lerp(progress, from.z, to.z);
        double ground = Mth.lerp(progress, from.y, to.y);
        double lift = 4.0D * apex * progress * (1.0D - progress);
        return new Vec3(x, ground + lift, z);
    }

    /** Turn the nose along the direction of travel. */
    private void aimAlong(Vec3 heading) {
        double flat = Math.sqrt(heading.x * heading.x + heading.z * heading.z);
        setYRot((float) (Mth.atan2(heading.z, heading.x) * (180.0D / Math.PI)) - 90.0F);
        setXRot((float) (-(Mth.atan2(heading.y, flat) * (180.0D / Math.PI))));
    }

    /**
     * The contrail and the roar. Client side, because both are scenery.
     *
     * <p>The engine note is played once a second and not once every four ticks. The sound it is
     * built from runs for well over a second, so five overlapping copies a second was five copies
     * of the same roar playing at once — which is not a louder rocket, it is a wall of noise, and
     * it was genuinely painful.
     */
    private void trail() {
        for (int i = 0; i < 2; i++) {
            level().addParticle(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE,
                    getX() + (random.nextDouble() - 0.5D) * 0.6D,
                    getY() + (random.nextDouble() - 0.5D) * 0.6D,
                    getZ() + (random.nextDouble() - 0.5D) * 0.6D,
                    0.0D, -0.02D, 0.0D);
        }
        if (tickCount % 2 == 0) {
            level().addParticle(ParticleTypes.FLAME, getX(), getY(), getZ(), 0.0D, 0.0D, 0.0D);
        }
        if (tickCount % 20 == 0) {
            MachineSounds.rocket(level(), getX(), getY(), getZ(), random);
        }
    }

    // --------------------------------------------------------------- landing

    private void arrive() {
        if (!(level() instanceof ServerLevel serverLevel)) {
            discard();
            return;
        }
        if (kind() == MissileKind.INTERCEPTOR) {
            // It got to where the thing it was chasing was going to be. Whether that was good
            // enough is the director's call, not the rocket's.
            discard();
            return;
        }
        Warhead.detonate(serverLevel, BlockPos.containing(to), kind(), cityId);
        discard();
    }

    /**
     * Shot down.
     *
     * <p>A hit is a fireball where the two met, well above anybody's roof, and then nothing. The
     * warhead never gets to the ground, which is the entire point of paying for interceptors.
     */
    public void intercepted() {
        if (level() instanceof ServerLevel serverLevel) {
            for (ServerPlayer player : serverLevel.players()) {
                serverLevel.sendParticles(player, ParticleTypes.EXPLOSION_EMITTER, true,
                        getX(), getY(), getZ(), 8, 3.0D, 3.0D, 3.0D, 0.0D);
            }
            Warhead.bang(serverLevel, blockPosition(), 4.0F, 0.9F);
        }
        discard();
    }

    // -------------------------------------------------------------- the state

    public MissileKind kind() {
        MissileKind[] values = MissileKind.values();
        int index = entityData.get(DATA_KIND);
        return index >= 0 && index < values.length ? values[index] : MissileKind.BALLISTIC;
    }

    public void setKind(MissileKind kind) {
        entityData.set(DATA_KIND, (byte) kind.ordinal());
    }

    public @Nullable UUID cityId() {
        return cityId;
    }

    public @Nullable UUID targetMissile() {
        return targetMissile;
    }

    /** Where it is going, for the map and for the sirens. */
    public Vec3 target() {
        return to;
    }

    /** How many ticks until it lands, for the countdown a defender is given. */
    public int ticksToImpact() {
        return Math.max(0, flightTicks - age);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    // ------------------------------------------------------------ persistence

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putByte("kind", (byte) kind().ordinal());
        tag.putDouble("fromX", from.x);
        tag.putDouble("fromY", from.y);
        tag.putDouble("fromZ", from.z);
        tag.putDouble("toX", to.x);
        tag.putDouble("toY", to.y);
        tag.putDouble("toZ", to.z);
        tag.putDouble("apex", apex);
        tag.putInt("flight", flightTicks);
        tag.putInt("age", age);
        if (cityId != null) {
            tag.putUUID("city", cityId);
        }
        if (targetMissile != null) {
            tag.putUUID("quarry", targetMissile);
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        setKind(MissileKind.values()[Mth.clamp(tag.getByte("kind"), 0,
                MissileKind.values().length - 1)]);
        from = new Vec3(tag.getDouble("fromX"), tag.getDouble("fromY"), tag.getDouble("fromZ"));
        to = new Vec3(tag.getDouble("toX"), tag.getDouble("toY"), tag.getDouble("toZ"));
        apex = tag.getDouble("apex");
        // A missile saved before it had been aimed would otherwise divide by zero on its next tick
        // and arrive instantly at the origin.
        flightTicks = Math.max(1, tag.getInt("flight"));
        age = tag.getInt("age");
        cityId = tag.hasUUID("city") ? tag.getUUID("city") : null;
        targetMissile = tag.hasUUID("quarry") ? tag.getUUID("quarry") : null;
    }

    /** So a launcher can make one without repeating the registry lookup. */
    public static @Nullable MissileEntity create(ServerLevel level) {
        return ModEntities.MISSILE.get().create(level);
    }
}
