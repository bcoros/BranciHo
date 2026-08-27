package com.branciho.citiesinlife.entity;

import com.branciho.citiesinlife.city.CityData;
import com.branciho.citiesinlife.city.CityMember;
import com.branciho.citiesinlife.entity.ai.CitizenSleepGoal;
import com.branciho.citiesinlife.entity.ai.CommitMurderGoal;
import com.branciho.citiesinlife.entity.ai.CitizenWorkGoal;
import com.branciho.citiesinlife.entity.ai.StrollOnPathGoal;
import com.branciho.citiesinlife.path.PathNetwork;
import com.branciho.citiesinlife.work.Workplace;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A person.
 *
 * <p>There are two populations in this mod and they are not the same thing. The city's population is
 * a number — tens of thousands of virtual people who fill offices, pay tax and never exist as
 * anything but arithmetic. A citizen is one of a handful of physical bodies walking about as a
 * <em>sample</em> of that number. Fifteen of these do not mean the city has fifteen people in it;
 * they mean fifteen of its people are somewhere you can watch them.
 *
 * <p>Keeping the two apart is what makes a city of forty thousand possible at all. Nothing about the
 * economy is derived from how many citizens are loaded, so turning them down to two — or off — costs
 * the player nothing but the sight of them.
 *
 * <p>Paths are a preference, never a requirement. A citizen with no pavement anywhere near it wanders
 * perfectly happily; a citizen with pavement finds standing on it far more appealing than standing
 * anywhere else, and so ends up walking the streets that were drawn for it. Routing them along a node
 * graph was the obvious alternative and is exactly the thing that breaks the first time a junction is
 * missing.
 */
public class CitizenEntity extends PathfinderMob implements CityMember {

    /** How many faces citizens come in. Purely cosmetic; the director picks one at random. */
    public static final int SKINS = 4;

    private static final EntityDataAccessor<Integer> DATA_SKIN =
            SynchedEntityData.defineId(CitizenEntity.class, EntityDataSerializers.INT);

    /** What the citizen is visibly doing, so the model can sit it down or set it typing. */
    private static final EntityDataAccessor<Byte> DATA_ACTIVITY =
            SynchedEntityData.defineId(CitizenEntity.class, EntityDataSerializers.BYTE);

    public static final byte ACTIVITY_IDLE = 0;
    public static final byte ACTIVITY_TYPING = 1;
    public static final byte ACTIVITY_SERVING = 2;

    /**
     * In a car, being carried.
     *
     * <p>Worth a constant of its own purely because StrollOnPathGoal only runs while a citizen is
     * ACTIVITY_IDLE, so this one value stops them trying to wander off mid-journey for free.
     */
    public static final byte ACTIVITY_DRIVING = 3;

    /**
     * Whether this one has snapped.
     *
     * <p>The only crime in this city is one citizen killing another, and it is meant to be rare
     * enough that most players will never see it twice. It is synched because the flag is the whole
     * of the police's job: an officer is spawned because somebody is wearing this, and goes home
     * again when nobody is.
     */
    private static final EntityDataAccessor<Boolean> DATA_CRIMINAL =
            SynchedEntityData.defineId(CitizenEntity.class, EntityDataSerializers.BOOLEAN);

    /** How far a criminal will go looking for somebody to take it out on. */
    private static final double VICTIM_RANGE = 20.0D;

    /**
     * How long somebody stays dangerous before they give it up on their own.
     *
     * <p>Five minutes. A city with no police station should be a worse place to live, not a
     * permanently broken one — without this, one bad afternoon leaves a killer walking the streets
     * for the rest of the save picking off everybody who spawns.
     */
    private static final int CRIME_TICKS = 6_000;

    /** The city this one belongs to, so the director can count its own. */
    private @Nullable UUID cityId;

    /** The car currently carrying this citizen, if any. */
    private @Nullable UUID carId;

    /**
     * Ticks before this citizen will try to find a car again.
     *
     * <p>Deliberately not saved. A failed search means "there was no route from that car park just
     * now", which is a fact about the world rather than about the citizen, and it should not
     * survive a reload.
     */
    private int driveCooldown;

    /** The bed it sleeps in, and the desk or till it works at. Either may be gone by morning. */
    private @Nullable BlockPos home;
    private @Nullable BlockPos workstation;

    /** Game time this one comes to their senses, if they have lost them. */
    private long criminalUntil;

    /**
     * Whether this one works nights.
     *
     * <p>Only tills have a night shift — a shop can open late and an office cannot. It is stored on
     * the citizen rather than on the till so that two people behind the same counter can be on
     * opposite shifts, which is what makes a shop that never closes look like one.
     */
    private boolean nightShift;

    /** Cached rather than looked up per call: the wander scorer asks about paths ten times a go. */
    private @Nullable PathNetwork paths;

    public CitizenEntity(EntityType<? extends CitizenEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.32D)
                // Ordinary people can throw a punch. Nothing uses this until one of them turns, and
                // an attribute that is missing when it is finally needed is a crash rather than a
                // harmless zero.
                .add(Attributes.ATTACK_DAMAGE, 3.0D)
                .add(Attributes.FOLLOW_RANGE, 48.0D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SKIN, 0);
        builder.define(DATA_ACTIVITY, ACTIVITY_IDLE);
        builder.define(DATA_CRIMINAL, false);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        // Above work and sleep, because somebody who has decided to do this is not going to the
        // office first.
        goalSelector.addGoal(1, new CommitMurderGoal(this));
        // Work and sleep come first because they are the only things with a schedule. Everything
        // below them is what a citizen does with the rest of its day.
        goalSelector.addGoal(2, new CitizenWorkGoal(this));
        goalSelector.addGoal(3, new CitizenSleepGoal(this));
        goalSelector.addGoal(5, new StrollOnPathGoal(this, 0.9D));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    // ------------------------------------------------------------------ paths

    /**
     * How much this citizen would like to be standing here.
     *
     * <p>This is the whole of the path system's effect on movement. Vanilla asks this of ten
     * candidate destinations every time a mob decides to wander and takes the best-scoring one, so
     * making pavement score high means a citizen with a street nearby keeps choosing to be on it —
     * without anything ever telling it to follow a route.
     *
     * <p>The block below counts for more than the block itself, because a street is marked at ground
     * level and a citizen walking down it is standing on top of the marks, not inside them.
     */
    @Override
    public float getWalkTargetValue(BlockPos pos, LevelReader level) {
        float base = super.getWalkTargetValue(pos, level);
        PathNetwork network = pathNetwork();
        if (network == null) {
            return base;
        }
        if (network.isPath(level().dimension(), pos.below())) {
            return base + 12.0F;
        }
        if (network.isPath(level().dimension(), pos)) {
            return base + 6.0F;
        }
        return base;
    }

    public @Nullable PathNetwork pathNetwork() {
        if (paths == null && level() instanceof ServerLevel serverLevel) {
            paths = PathNetwork.get(serverLevel.getServer());
        }
        return paths;
    }

    // ------------------------------------------------------------------ crime

    public boolean criminal() {
        return entityData.get(DATA_CRIMINAL);
    }

    public void setCriminal(boolean criminal) {
        if (entityData.get(DATA_CRIMINAL) != criminal) {
            entityData.set(DATA_CRIMINAL, criminal);
        }
        if (criminal) {
            criminalUntil = level().getGameTime() + CRIME_TICKS;
        } else {
            criminalUntil = 0L;
            setTarget(null);
        }
    }

    /**
     * Somebody nearby to take it out on.
     *
     * <p>Their own neighbours only. A criminal who wandered across the city boundary and attacked a
     * stranger would be somebody else's police force's problem, and working out whose is a knot not
     * worth tying for a thing that happens once an hour.
     */
    public @Nullable CitizenEntity findVictim() {
        if (cityId == null) {
            return null;
        }
        CitizenEntity nearest = null;
        double best = VICTIM_RANGE * VICTIM_RANGE;
        for (CitizenEntity other : level().getEntitiesOfClass(CitizenEntity.class,
                getBoundingBox().inflate(VICTIM_RANGE))) {
            if (other == this || !other.isAlive() || other.criminal()
                    || !cityId.equals(other.cityId())) {
                continue;
            }
            double distance = distanceToSqr(other);
            if (distance < best) {
                best = distance;
                nearest = other;
            }
        }
        return nearest;
    }

    // ----------------------------------------------------------------- living

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) {
            return;
        }
        if (criminal() && level().getGameTime() >= criminalUntil) {
            setCriminal(false);
        }
        if (driveCooldown > 0) {
            driveCooldown--;
        }

        // A car lost to a chunk unload or a crash would otherwise leave its passenger invisible and
        // held still forever. This runs from aiStep, which is called before the AI gate, so it is
        // the one check that still fires whatever state the citizen is in.
        if (tickCount % 40 == 0 && activity() == ACTIVITY_DRIVING
                && level() instanceof ServerLevel serverLevel
                && (carId == null || !(serverLevel.getEntity(carId) instanceof CarEntity))) {
            CarEntity.release(this);
        }

        // A razed city leaves its people behind. Nothing counts them against a cap any longer and
        // nothing protects them from being killed, so they would wander a dead city forever as a
        // small permanent leak. They go with it.
        if (tickCount % 200 == 0 && level() instanceof ServerLevel serverLevel) {
            if (cityId == null || CityData.get(serverLevel.getServer()).city(cityId) == null) {
                discard();
                return;
            }
        }

        // Hold the job open. A desk forgets anybody who stops checking in, which is how it recovers
        // from a worker that was killed, unloaded, or removed because the cap was turned down —
        // none of which give the citizen a chance to hand its notice in.
        //
        // An unloaded desk is not a lost job, though. Somebody walking to work across a chunk border
        // that has not caught up yet would otherwise resign on the doorstep, every time.
        if (workstation != null && tickCount % 40 == 0 && level().isLoaded(workstation)) {
            Workplace place = workplace();
            if (place == null || !place.checkIn(getUUID(), level().getGameTime())) {
                workstation = null;
            }
        }
    }

    /** The desk or till itself, or null if it has been broken or is not loaded right now. */
    public @Nullable Workplace workplace() {
        if (workstation == null || !level().isLoaded(workstation)) {
            return null;
        }
        BlockEntity blockEntity = level().getBlockEntity(workstation);
        return blockEntity instanceof Workplace place ? place : null;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        // Citizens are placed deliberately by the director and counted against a cap, so letting
        // vanilla quietly delete them would mean the cap drifted every time a player walked away.
        return false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    // --------------------------------------------------------------- identity

    @Override
    public @Nullable UUID cityId() {
        return cityId;
    }

    public void setCityId(@Nullable UUID cityId) {
        this.cityId = cityId;
    }

    public @Nullable UUID carId() {
        return carId;
    }

    public void setCarId(@Nullable UUID carId) {
        this.carId = carId;
    }

    public boolean mayLookForCar() {
        return driveCooldown <= 0;
    }

    public void holdOffDriving(int ticks) {
        driveCooldown = ticks;
    }

    public @Nullable BlockPos home() {
        return home;
    }

    public void setHome(@Nullable BlockPos home) {
        this.home = home;
    }

    public @Nullable BlockPos workstation() {
        return workstation;
    }

    public void setWorkstation(@Nullable BlockPos workstation) {
        this.workstation = workstation;
    }

    public boolean nightShift() {
        return nightShift;
    }

    public void setNightShift(boolean nightShift) {
        this.nightShift = nightShift;
    }

    public int skin() {
        return entityData.get(DATA_SKIN);
    }

    public void setSkin(int skin) {
        entityData.set(DATA_SKIN, Math.floorMod(skin, SKINS));
    }

    public byte activity() {
        return entityData.get(DATA_ACTIVITY);
    }

    public void setActivity(byte activity) {
        if (entityData.get(DATA_ACTIVITY) != activity) {
            entityData.set(DATA_ACTIVITY, activity);
        }
    }

    public boolean seated() {
        return activity() == ACTIVITY_TYPING;
    }

    // ------------------------------------------------------------ persistence

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (cityId != null) {
            tag.putUUID("city", cityId);
        }
        if (home != null) {
            tag.putLong("home", home.asLong());
        }
        if (workstation != null) {
            tag.putLong("workstation", workstation.asLong());
        }
        tag.putBoolean("nightShift", nightShift);
        tag.putBoolean("criminal", criminal());
        tag.putLong("criminalUntil", criminalUntil);
        tag.putInt("skin", skin());
        if (carId != null) {
            tag.putUUID("car", carId);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        cityId = tag.hasUUID("city") ? tag.getUUID("city") : null;
        home = tag.contains("home") ? BlockPos.of(tag.getLong("home")) : null;
        workstation = tag.contains("workstation") ? BlockPos.of(tag.getLong("workstation")) : null;
        nightShift = tag.getBoolean("nightShift");
        setCriminal(tag.getBoolean("criminal"));
        if (tag.contains("criminalUntil")) {
            criminalUntil = tag.getLong("criminalUntil");
        }
        setSkin(tag.getInt("skin"));
        carId = tag.hasUUID("car") ? tag.getUUID("car") : null;
    }
}
