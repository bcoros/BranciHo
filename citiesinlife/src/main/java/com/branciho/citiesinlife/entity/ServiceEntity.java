package com.branciho.citiesinlife.entity;

import com.branciho.citiesinlife.city.City;
import com.branciho.citiesinlife.city.CityData;
import com.branciho.citiesinlife.city.CityMember;
import com.branciho.citiesinlife.city.Diplomacy;
import com.branciho.citiesinlife.city.Relation;
import com.branciho.citiesinlife.entity.ai.FireDutyGoal;
import com.branciho.citiesinlife.entity.ai.MedicGoal;
import com.branciho.citiesinlife.entity.ai.PoliceGoal;
import com.branciho.citiesinlife.entity.ai.RefuseGoal;
import com.branciho.citiesinlife.entity.ai.SoldierGoal;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

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
public class ServiceEntity extends PathfinderMob implements CityMember {

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
                .add(Attributes.FOLLOW_RANGE, 64.0D);
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
        goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.2D, true));
        goalSelector.addGoal(2, new PoliceGoal(this));
        goalSelector.addGoal(2, new FireDutyGoal(this));
        goalSelector.addGoal(2, new MedicGoal(this));
        goalSelector.addGoal(2, new RefuseGoal(this));
        goalSelector.addGoal(2, new SoldierGoal(this));
        goalSelector.addGoal(3, new SoldierTargetGoal(this));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.9D));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
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

    public @Nullable UUID soldierId() {
        return soldierId;
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
        idleTicks++;

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
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setRole(ServiceType.byId(tag.getString("role"), ServiceType.POLICE));
        setTraining(tag.getInt("training"));
        cityId = tag.hasUUID("city") ? tag.getUUID("city") : null;
        station = tag.contains("station") ? BlockPos.of(tag.getLong("station")) : null;
        soldierId = tag.hasUUID("soldier") ? tag.getUUID("soldier") : null;
    }
}
