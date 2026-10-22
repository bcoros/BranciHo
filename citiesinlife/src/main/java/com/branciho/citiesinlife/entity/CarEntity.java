package com.branciho.citiesinlife.entity;

import com.branciho.citiesinlife.road.RoadNetwork;
import com.branciho.citiesinlife.road.RoadTile;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import com.branciho.citiesinlife.sound.MachineSounds;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.world.entity.PathfinderMob;

/**
 * A citizen's car, driven along a route worked out before it ever existed.
 *
 * <p>A plain {@link Entity} rather than a Mob, the way a boat is. It has no attributes, no goals and
 * no navigation, because it must not have any: vanilla pathfinding is what the car exists to avoid.
 * {@code CitizenEntity} has a FOLLOW_RANGE of 48, and a {@code moveTo} much beyond about 56 blocks
 * returns no path at all — silently, with no exception and no log line. A citizen asked to walk 140
 * blocks to work simply stands still. So the car covers the long middle of the journey by moving
 * itself, and vanilla only ever handles the two short walks at either end.
 *
 * <p>The passenger is <em>not</em> a vanilla passenger. They are made invisible and dragged along by
 * {@code setPos}. That is deliberate on two counts: 1.21.1 replaced the old riding-offset method
 * with the EntityAttachments API, and this project has no local Minecraft to check a signature
 * against; and, more importantly, a real {@code setNoAi} passenger would stop the goal selector
 * ticking, which would make the two places that clean a broken trip up — the goals' {@code tick}
 * and {@code stop} — unreachable for the whole drive. The citizen keeps its AI; the car simply
 * overrules it every tick.
 */
public class CarEntity extends Entity {

    /**
     * Which vehicle this is.
     *
     * <p>One entity type rather than four, because the differences between a saloon and an
     * ambulance that the game actually has to model are a texture, a light bar and a noise. Four
     * entity types would have meant four registrations, four renderers and four copies of the
     * routing for the sake of a paint job.
     */
    public enum Livery {

        /** A citizen's own car. No light bar, no siren. */
        SALOON("car", false),
        POLICE("police_car", true),
        FIRE("fire_truck", true),
        AMBULANCE("ambulance", true);

        private final String texture;
        private final boolean emergency;

        Livery(String texture, boolean emergency) {
            this.texture = texture;
            this.emergency = emergency;
        }

        public String texture() {
            return texture;
        }

        /** Whether it has a light bar on the roof and a siren under the bonnet. */
        public boolean emergency() {
            return emergency;
        }

        public static Livery byId(int id) {
            Livery[] values = values();
            return id >= 0 && id < values.length ? values[id] : SALOON;
        }
    }

    private static final EntityDataAccessor<Byte> DATA_LIVERY =
            SynchedEntityData.defineId(CarEntity.class, EntityDataSerializers.BYTE);

    /** Blocks per tick on an ordinary street. About 3.6 m/s - a touch faster than walking. */
    private static final double SPEED_ROAD = 0.18D;

    /** Blocks per tick on a motorway. Nearly twice as quick, which is the point of one. */
    private static final double SPEED_HIGHWAY = 0.35D;

    /** How near the middle of a tile counts as reaching it. */
    private static final double ARRIVED = 0.35D;

    /**
     * Two minutes, after which the trip is written off wherever it has got to.
     *
     * <p>A car that has jammed - a route that loops, a tile that cannot be reached - must not hold
     * an invisible citizen for the rest of the day.
     */
    private static final int MAX_TRIP_TICKS = 2400;

    private final LongArrayList route = new LongArrayList();
    private int index;
    private @Nullable UUID passengerId;

    /** How long since this car last made a noise, and how long it waits between them. */
    private static final int ENGINE_INTERVAL = 8;

    private int engineTicks;

    /** Which half of the two-tone the siren is on. */
    private int sirenTicks;

    private double lastX;
    private double lastZ;
    private float travelled;
    private float travelledPrev;

    public CarEntity(EntityType<? extends CarEntity> type, Level level) {
        super(type, level);
        // It follows a route rather than falling down hills, and it must not shove its own
        // passenger about.
        setNoGravity(true);
        this.noPhysics = true;
        // Three and a half blocks long, so a hitbox-sized cull test pops it out of view on the
        // diagonal.
        this.noCulling = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        // The livery, and only the livery. Everything else about a car is either its position,
        // which every entity already sends, or the route, which is nobody's business but its own.
        builder.define(DATA_LIVERY, (byte) Livery.SALOON.ordinal());
    }

    public Livery livery() {
        return Livery.byId(entityData.get(DATA_LIVERY));
    }

    public void setLivery(Livery livery) {
        entityData.set(DATA_LIVERY, (byte) livery.ordinal());
    }

    public void setRoute(LongArrayList tiles) {
        route.clear();
        route.addAll(tiles);
        index = 0;
    }

    /**
     * Take somebody aboard: invisible, held still, and driving whatever they drive.
     *
     * <p>The generic bound is doing real work. It says the passenger is both a mob this car can
     * hold still and a person who owns a car - and it means the car takes its livery from whoever
     * got in rather than from whoever spawned it, so a paramedic is in an ambulance because they
     * are a paramedic and there is no second place for that to be decided wrongly.
     */
    public <T extends PathfinderMob & Motorist> void board(T rider) {
        passengerId = rider.getUUID();
        rider.setCarId(getUUID());
        rider.ridingChanged(true);
        setLivery(rider.livery());
        rider.setInvisible(true);
        rider.getNavigation().stop();
    }

    public @Nullable PathfinderMob rider(ServerLevel level) {
        if (passengerId == null) {
            return null;
        }
        return level.getEntity(passengerId) instanceof PathfinderMob mob && mob instanceof Motorist
                ? mob : null;
    }

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel serverLevel) {
            drive(serverLevel);
        }
        // Both sides, because the wheels are turned by how far the car has actually moved and the
        // client only ever sees interpolated positions.
        travelledPrev = travelled;
        double dx = getX() - lastX;
        double dz = getZ() - lastZ;
        float moved = (float) Math.sqrt(dx * dx + dz * dz);
        travelled += moved;
        lastX = getX();
        lastZ = getZ();

        if (level().isClientSide) {
            listen(moved);
        }
    }

    /**
     * The engine, from the client only.
     *
     * <p>Every eight ticks rather than every tick: the sound is about half a second long, so any
     * faster and a car would be playing four copies of itself over one another. Keyed off how far
     * it actually moved this tick, so a car crawling through a junction sounds like one.
     */
    private void listen(float moved) {
        if (++engineTicks < ENGINE_INTERVAL) {
            return;
        }
        engineTicks = 0;
        if (moved <= 0.005F) {
            return;
        }
        MachineSounds.engine(level(), getX(), getY() + 0.5D, getZ(), level().random,
                (float) (moved / SPEED_HIGHWAY));
        if (livery().emergency()) {
            // Two tones, alternating, taken from where the siren cycle happens to be. A siren
            // that played one note would be an alarm; it is the swap between them that makes it
            // read as something on its way somewhere.
            MachineSounds.siren(level(), getX(), getY() + 1.0D, getZ(), sirenTicks++ % 2 == 0);
        }
    }

    private void drive(ServerLevel level) {
        PathfinderMob citizen = rider(level);
        if (citizen == null || !citizen.isAlive()) {
            // The passenger is gone - unloaded, killed, or the city was razed. Nothing to carry.
            discard();
            return;
        }
        if (index >= route.size()) {
            dropOff(true);
            return;
        }
        if (tickCount > MAX_TRIP_TICKS) {
            dropOff(false);
            return;
        }

        long tile = route.getLong(index);
        double targetX = BlockPos.getX(tile) + 0.5D;
        double targetY = BlockPos.getY(tile) + 1.0D;
        double targetZ = BlockPos.getZ(tile) + 0.5D;

        double dx = targetX - getX();
        double dz = targetZ - getZ();
        if (Math.sqrt(dx * dx + dz * dz) <= ARRIVED) {
            index++;
            return;
        }

        double speed = RoadTile.is(
                RoadNetwork.get(level.getServer()).flagsAt(level.dimension(), tile), RoadTile.HIGHWAY)
                ? SPEED_HIGHWAY : SPEED_ROAD;

        double length = Math.max(1.0E-4D, Math.sqrt(dx * dx + dz * dz));
        double stepX = dx / length * speed;
        double stepZ = dz / length * speed;
        double stepY = Mth.clamp(targetY - getY(), -speed, speed);
        setPos(getX() + stepX, getY() + stepY, getZ() + stepZ);
        setYRot((float) (Mth.atan2(stepZ, stepX) * (180.0D / Math.PI)) - 90.0F);

        // Drag the passenger. Their navigation is cleared every tick rather than once, because
        // their goals are still running and one of them will happily re-issue a walk.
        citizen.getNavigation().stop();
        citizen.setPos(getX(), getY(), getZ());
        citizen.setYRot(getYRot());
    }

    /**
     * Put the passenger down and vanish.
     *
     * <p>{@code arrived} decides <em>where</em>, and it matters: a trip that timed out a third of
     * the way there must not still deposit its passenger at the destination. That would be a free
     * cross-map teleport handed out for a car breaking down.
     */
    public void dropOff(boolean arrived) {
        if (!(level() instanceof ServerLevel serverLevel)) {
            discard();
            return;
        }
        PathfinderMob citizen = rider(serverLevel);
        if (citizen != null) {
            BlockPos near = arrived && !route.isEmpty()
                    ? BlockPos.of(route.getLong(route.size() - 1))
                    : blockPosition();
            BlockPos spot = kerbside(serverLevel, near);
            if (spot != null) {
                citizen.teleportTo(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D);
            }
            release(citizen);
        }
        discard();
    }

    /** Hand the passenger back their own body. Safe when the car is already gone. */
    public static void release(PathfinderMob rider) {
        rider.setInvisible(false);
        if (rider instanceof Motorist motorist) {
            motorist.setCarId(null);
            motorist.ridingChanged(false);
        }
        rider.getNavigation().stop();
    }

    /**
     * Somewhere beside the road with room to stand.
     *
     * <p>The same three-part test the rest of the mod uses. Dropping somebody inside a wall would
     * normally kill them free, but this mod cancels damage to a city's own people, so a citizen put
     * down in solid rock stays there.
     */
    private static @Nullable BlockPos kerbside(ServerLevel level, BlockPos near) {
        if (roomToStand(level, near.above())) {
            return near.above();
        }
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos candidate = near.relative(direction).above();
            if (roomToStand(level, candidate)) {
                return candidate;
            }
        }
        return roomToStand(level, near) ? near : null;
    }

    private static boolean roomToStand(ServerLevel level, BlockPos pos) {
        return level.isLoaded(pos)
                && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
                && !level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty();
    }

    /** How far the car has driven, for turning the wheels. */
    public float travelled(float partialTick) {
        return Mth.lerp(partialTick, travelledPrev, travelled);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putLongArray("route", route.toLongArray());
        tag.putInt("index", index);
        if (passengerId != null) {
            tag.putUUID("passenger", passengerId);
        }
        tag.putByte("livery", (byte) livery().ordinal());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        route.clear();
        for (long tile : tag.getLongArray("route")) {
            route.add(tile);
        }
        index = tag.getInt("index");
        passengerId = tag.hasUUID("passenger") ? tag.getUUID("passenger") : null;
        // Absent on a car saved before the emergency services existed, and a car saved then was a
        // citizen's own: the default is both the old behaviour and the right answer.
        setLivery(Livery.byId(tag.getByte("livery")));
    }
}
