package com.branciho.citiesinlife.entity;

import com.branciho.citiesinlife.city.City;
import com.branciho.citiesinlife.city.CityData;
import com.branciho.citiesinlife.city.CityMember;
import com.branciho.citiesinlife.city.Diplomacy;
import com.branciho.citiesinlife.city.Relation;
import com.branciho.citiesinlife.entity.ai.BodyguardFollowGoal;
import com.branciho.citiesinlife.entity.ai.BodyguardTargetGoal;
import com.branciho.citiesinlife.entity.ai.FireDutyGoal;
import com.branciho.citiesinlife.entity.ai.MedicGoal;
import com.branciho.citiesinlife.entity.ai.PoliceGoal;
import com.branciho.citiesinlife.entity.ai.RefuseGoal;
import com.branciho.citiesinlife.entity.ai.RifleGoal;
import com.branciho.citiesinlife.entity.ai.SoldierGoal;
import com.branciho.citiesinlife.entity.ai.TownNavigation;
import com.branciho.citiesinlife.entity.ai.TrenchGoal;
import com.branciho.citiesinlife.entity.ai.SoldierTargetGoal;
import com.branciho.citiesinlife.service.ServiceType;
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
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import com.branciho.citiesinlife.entity.ai.GoHomeGoal;

/**
 * Somebody in a uniform.
 *
 * <p>One entity for all five staffed services rather than five, because the difference between a
 * police officer and a bin man is entirely which errand they are on: the body, the walk and the
 * pathfinder are identical, and five copies of those would be five places to fix the next bug in
 * them. Every goal below asks what this one is before it does anything.
 *
 * <p>They are not citizens and are not counted as such. A city with its full fifteen people can
 * still have an ambulance turn up, which is the point — a service that could not appear because the
 * streets were busy would be a service that never appeared when it mattered.
 *
 * <p>All of them except soldiers go home when there is nothing to do. That is what makes the cap on
 * a service a cap rather than a headcount: a High police station with a quiet week has nobody
 * standing outside it.
 */
public class ServiceEntity extends PathfinderMob implements CityMember, Motorist, Homebound {

    private static final EntityDataAccessor<Byte> DATA_ROLE =
            SynchedEntityData.defineId(ServiceEntity.class, EntityDataSerializers.BYTE);

    /** Soldiers wear their training, so a trained one is worth looking at. */
    private static final EntityDataAccessor<Byte> DATA_TRAINING =
            SynchedEntityData.defineId(ServiceEntity.class, EntityDataSerializers.BYTE);

    /** How trained a soldier can get, and what each level is worth. */
    public static final int MAX_TRAINING = 3;

    /**
     * How long with nothing to do before one of them clocks off.
     *
     * <p>Half a minute. Long enough that an officer who has just finished an arrest does not vanish
     * in front of the player, short enough that a quiet city is an empty one.
     */
    private static final int IDLE_TICKS_BEFORE_LEAVING = 600;

    private @Nullable UUID cityId;

    /** The spawner that sent them out, so it can count its own and they can go back to it. */
    private @Nullable BlockPos station;

    /** For a soldier, which entry in the city's army roll this body belongs to. */
    private @Nullable UUID soldierId;

    /**
     * The player a bodyguard is assigned to, and where in the wedge they walk.
     *
     * <p>The employer is the city's owner in practice, but stored as a UUID rather than resolved
     * from the city every time: a guard has to answer "who am I with" twenty times a second, and
     * the answer must survive the city being handed over or the owner logging out mid-walk.
     */
    private @Nullable UUID employerId;
    private int formationSlot;

    /** Ticks since this one last had a job. Reset by every duty goal that finds work. */
    private int idleTicks;

    public ServiceEntity(EntityType<? extends ServiceEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 24.0D)
                // Faster than a civilian. Everybody here is on their way to something.
                .add(Attributes.MOVEMENT_SPEED, 0.38D)
                .add(Attributes.ATTACK_DAMAGE, 4.0D)
                // Same two as the citizens, and for the same reasons: a kerb should be stepped over
                // rather than jumped, and sixty-four blocks is not the length of a call-out.
                .add(Attributes.STEP_HEIGHT, 1.0D)
                .add(Attributes.FOLLOW_RANGE, 128.0D);
    }

    /**
     * The town router, not the field one.
     *
     * <p>See {@link TownNavigation}. A police officer who cannot get round a wall is the same bug
     * as a citizen who cannot, and rather more noticeable: nobody minds a civilian dawdling, but an
     * ambulance stuck against a fence is the mod visibly not working.
     */
    @Override
    protected PathNavigation createNavigation(Level level) {
        return new TownNavigation(this, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ROLE, (byte) ServiceType.POLICE.ordinal());
        builder.define(DATA_TRAINING, (byte) 0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        // Above everything but drowning. Somebody who has been stood down is off the roll already;
        // letting them start a fight or a bin round on the way back to the station would be a
        // person the city no longer employs still doing the job.
        goalSelector.addGoal(1, new GoHomeGoal<>(this, 1.0D));
        // Shooting outranks swinging, so an armed soldier holds their distance instead of charging
        // into a knife fight they did not need to be in.
        goalSelector.addGoal(2, new RifleGoal(this));
        goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.2D, true));
        // These two hold no movement flags: they only decide who to fight, and have to keep
        // deciding while the goals above are already running.
        goalSelector.addGoal(4, new PoliceGoal(this));
        goalSelector.addGoal(4, new SoldierTargetGoal(this));
        goalSelector.addGoal(4, new BodyguardTargetGoal(this));
        goalSelector.addGoal(5, new FireDutyGoal(this));
        goalSelector.addGoal(5, new MedicGoal(this));
        goalSelector.addGoal(5, new RefuseGoal(this));
        goalSelector.addGoal(5, new SoldierGoal(this));
        // The other half of a war. Mutually exclusive with the one above by their own checks:
        // you are either advancing on their ground or dug into yours, never both.
        goalSelector.addGoal(5, new TrenchGoal(this));
        // Below the fighting and above the strolling: a bodyguard breaks formation to deal with
        // somebody and walks back into it afterwards, which is the whole behaviour.
        goalSelector.addGoal(6, new BodyguardFollowGoal(this, 1.05D));
        goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.9D));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(9, new RandomLookAroundGoal(this));
    }

    // --------------------------------------------------------------- identity

    public ServiceType role() {
        ServiceType[] values = ServiceType.values();
        int index = entityData.get(DATA_ROLE);
        return index >= 0 && index < values.length ? values[index] : ServiceType.POLICE;
    }

    public void setRole(ServiceType role) {
        entityData.set(DATA_ROLE, (byte) role.ordinal());
    }

    public int training() {
        return entityData.get(DATA_TRAINING);
    }

    public void setTraining(int training) {
        entityData.set(DATA_TRAINING, (byte) Math.max(0, Math.min(MAX_TRAINING, training)));
    }

    @Override
    public @Nullable UUID cityId() {
        return cityId;
    }

    public void setCityId(@Nullable UUID cityId) {
        this.cityId = cityId;
    }

    public @Nullable BlockPos station() {
        return station;
    }

    public void setStation(@Nullable BlockPos station) {
        this.station = station;
    }

    /** The vehicle this one is riding in. See {@link #carId()}. */
    private @Nullable UUID carId;

    /**
     * The vehicle this one is riding in, if any.
     *
     * <p>Not saved. A patrol is at most a couple of minutes long and the car itself is not
     * persisted across an unload either, so writing it down would only mean loading a world into
     * a paramedic who believes they are in an ambulance that no longer exists.
     */
    @Override
    public @Nullable UUID carId() {
        return carId;
    }

    @Override
    public void setCarId(@Nullable UUID carId) {
        this.carId = carId;
    }

    /**
     * Nothing to do.
     *
     * <p>A citizen uses this to hold an activity byte; a service worker has no such thing. Their
     * goals are all conditioned on finding somebody to police or treat, and one riding past in a
     * car simply does not find anybody - which is the correct behaviour and needed no flag.
     */
    @Override
    public void ridingChanged(boolean aboard) {
    }

    /** What they turn up in, decided by what they do. */
    @Override
    public CarEntity.Livery livery() {
        return switch (role()) {
            case POLICE -> CarEntity.Livery.POLICE;
            case FIRE -> CarEntity.Livery.FIRE;
            case HOSPITAL -> CarEntity.Livery.AMBULANCE;
            default -> CarEntity.Livery.SALOON;
        };
    }

    /** Whether this service has a vehicle of its own at all. */
    public boolean drives() {
        return livery().emergency();
    }

    public @Nullable UUID soldierId() {
        return soldierId;
    }

    /** The player this bodyguard is walking with, or null if they are not one, or nobody is on. */
    public @Nullable Player employer() {
        if (employerId == null || role() != ServiceType.BODYGUARD) {
            return null;
        }
        Player boss = level().getPlayerByUUID(employerId);
        return boss != null && boss.isAlive() ? boss : null;
    }

    public void setEmployerId(@Nullable UUID employerId) {
        this.employerId = employerId;
    }

    /** Which station in the formation this one walks. */
    public int formationSlot() {
        return formationSlot;
    }

    public void setFormationSlot(int formationSlot) {
        this.formationSlot = formationSlot;
    }

    public void setSoldierId(@Nullable UUID soldierId) {
        this.soldierId = soldierId;
    }

    public @Nullable City city() {
        if (cityId == null || !(level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        return CityData.get(serverLevel.getServer()).city(cityId);
    }

    /** Told by a duty goal that it found something to do, so this one is not sent home. */
    public void reportBusy() {
        idleTicks = 0;
    }

    public boolean overstayed() {
        return !role().permanent() && idleTicks >= IDLE_TICKS_BEFORE_LEAVING;
    }

    // -------------------------------------------------------------- going home

    /** Whether this one has been stood down and is walking back to the station. */
    private boolean leaving;

    /**
     * The station that hired them, which is already stored and already saved.
     *
     * <p>They walk back to the block itself rather than to the doorway they came out of. The
     * doorway is worked out fresh each time the station deploys somebody and is not worth
     * remembering; the station has not moved.
     */
    @Override
    public @Nullable BlockPos homeBlock() {
        return station;
    }

    @Override
    public boolean leaving() {
        return leaving;
    }

    @Override
    public void startLeaving() {
        leaving = true;
    }

    // ---------------------------------------------------------------- combat

    /**
     * Whether this one is allowed to raise a hand to somebody else's citizen.
     *
     * <p>Two answers, and neither of them is "yes". Police may only touch a criminal, and only one
     * of their own; soldiers may only touch a city their own city is at war with. Anything else is
     * refused by the same rule that stops a player doing it.
     */
    public boolean mayHarm(@Nullable City victimCity, boolean victimIsCriminal) {
        if (victimCity == null || cityId == null) {
            return false;
        }
        return switch (role()) {
            case POLICE -> victimIsCriminal && cityId.equals(victimCity.id());
            case MILITARY -> {
                City mine = city();
                yield mine != null && Diplomacy.stance(victimCity, mine) == Relation.WAR;
            }
            // A bodyguard answers to a person, not to a map, so the war table cannot decide this
            // for them. Their own targeting goal has already refused to pick anybody who is not in
            // a fight with their employer; without this arm the protection rules would cancel
            // every blow they ever landed and they would be an expensive escort of mimes.
            case BODYGUARD -> !cityId.equals(victimCity.id());
            default -> false;
        };
    }

    // ----------------------------------------------------------------- living

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) {
            return;
        }
        // Not while they are in a vehicle. The clock is "how long since this one had anything to
        // do", and a crew halfway across the city in an ambulance is the single busiest they ever
        // get - but the clock ran anyway, so a long drive made them overstay, the station stood
        // them down mid-journey, and the car they were sitting in deleted itself out from under
        // them. Being on the way to a job counts as having one.
        if (carId == null) {
            idleTicks++;
        }

        // The city they work for may have been razed, or the station they came out of knocked down.
        // Either way they are on somebody else's street in a uniform nobody recognises.
        if (tickCount % 200 == 0 && level() instanceof ServerLevel serverLevel) {
            if (cityId == null || CityData.get(serverLevel.getServer()).city(cityId) == null) {
                discard();
            }
        }
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        // Spawned deliberately and counted against a station's cap, so vanilla despawning them would
        // leave the station believing it still had somebody out.
        return false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    // ------------------------------------------------------------ persistence

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("role", role().id());
        tag.putBoolean("leaving", leaving);
        tag.putInt("training", training());
        if (cityId != null) {
            tag.putUUID("city", cityId);
        }
        if (station != null) {
            tag.putLong("station", station.asLong());
        }
        if (soldierId != null) {
            tag.putUUID("soldier", soldierId);
        }
        if (employerId != null) {
            tag.putUUID("employer", employerId);
        }
        tag.putInt("formationSlot", formationSlot);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setRole(ServiceType.byId(tag.getString("role"), ServiceType.POLICE));
        leaving = tag.getBoolean("leaving");
        setTraining(tag.getInt("training"));
        cityId = tag.hasUUID("city") ? tag.getUUID("city") : null;
        station = tag.contains("station") ? BlockPos.of(tag.getLong("station")) : null;
        soldierId = tag.hasUUID("soldier") ? tag.getUUID("soldier") : null;
        employerId = tag.hasUUID("employer") ? tag.getUUID("employer") : null;
        formationSlot = tag.getInt("formationSlot");
    }
}
