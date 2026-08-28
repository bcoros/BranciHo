package com.branciho.citiesinlife.entity;

import com.branciho.citiesinlife.block.AirfieldBlock;
import com.branciho.citiesinlife.city.CityMember;
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
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import com.branciho.citiesinlife.entity.ai.GoHomeGoal;

/**
 * Somebody visiting, off a plane that does not exist.
 *
 * <p>Scenery with a lifespan. A tourist has no bed, no job and no place in the population count -
 * they wander for a while and then go home - which is why they are their own entity rather than a
 * citizen with a flag. A citizen without a bed is a bug in this mod; a tourist without one is the
 * whole idea.
 *
 * <p>Deliberately <em>not</em> persistence-required, and deliberately happy to be removed when far
 * away. The tourist ages only while its chunk is ticking, so a visitor who wandered off and got
 * unloaded would otherwise never reach the end of its stay and never leave. Letting vanilla clean
 * up anything the airport loses track of is what stops that being a slow leak, and this mod's
 * standing rule is that cost scales with the number of buildings, never with the number of people.
 */
public class TouristEntity extends PathfinderMob implements CityMember, Homebound {

    private static final EntityDataAccessor<Integer> DATA_SKIN =
            SynchedEntityData.defineId(TouristEntity.class, EntityDataSerializers.INT);

    /** Five minutes of ticked time, then they catch their flight back. */
    public static final int LIFETIME_TICKS = 6000;

    private @Nullable UUID cityId;
    private @Nullable BlockPos airfield;
    private int lifeTicks;

    /**
     * Whether their visit is over and they are walking back to the plane.
     *
     * <p>Saved, because the walk can easily outlive a chunk unload and a tourist who forgot they
     * were leaving would go back to strolling around a city they have already checked out of.
     */
    private boolean leaving;

    public TouristEntity(EntityType<? extends TouristEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.30D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SKIN, 0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        // Above the strolling, so somebody whose flight is called stops sightseeing.
        goalSelector.addGoal(1, new GoHomeGoal<>(this, 1.0D));
        // No StrollOnPathGoal: it is typed to CitizenEntity, and a visitor has no reason to know
        // where the city's pavements are anyway.
        goalSelector.addGoal(2, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(4, new RandomLookAroundGoal(this));
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) {
            return;
        }
        lifeTicks++;
        if (tickCount % 200 != 0 || !(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        // The airport they arrived at has been knocked down while they were here. There is
        // nowhere to walk back to, so this is the one case that still ends on the spot.
        if (airfield != null && serverLevel.isLoaded(airfield)
                && !(serverLevel.getBlockState(airfield).getBlock() instanceof AirfieldBlock)) {
            discard();
            return;
        }
        // Time up. They do not vanish out of the street any more - they walk back to the plane
        // they came in on, and GoHomeGoal sees them through the door.
        if (lifeTicks >= LIFETIME_TICKS) {
            startLeaving();
        }
    }

    // -------------------------------------------------------------- going home

    /**
     * The airfield they arrived at.
     *
     * <p>Already stored and already saved, because the visit was always tied to one airport - it
     * simply had nothing to do with it beyond noticing when it was demolished. Now it is where
     * they walk back to.
     */
    @Override
    public @Nullable BlockPos homeBlock() {
        return airfield;
    }

    @Override
    public boolean leaving() {
        return leaving;
    }

    @Override
    public void startLeaving() {
        leaving = true;
    }

    /**
     * Despawnable, right up until they set off for the airport.
     *
     * <p>The first half is deliberate and is explained on the class: a tourist only ages while its
     * chunk ticks, so one that wandered off and got unloaded would never reach the end of its stay,
     * and letting vanilla clean those up is what stops a slow leak.
     *
     * <p>The second half is what makes the walk home mean anything. A visitor two hundred blocks
     * out, finally heading back to the plane, is exactly the one vanilla would pick off — and being
     * deleted on the way home is the bug this was all meant to fix, just further down the street.
     * The ninety-second patience in {@link com.branciho.citiesinlife.entity.ai.GoHomeGoal} is what
     * keeps that from becoming a licence to exist forever.
     */
    @Override
    public boolean removeWhenFarAway(double distance) {
        return !leaving;
    }

    @Override
    public @Nullable UUID cityId() {
        return cityId;
    }

    public void setCityId(@Nullable UUID cityId) {
        this.cityId = cityId;
    }

    public @Nullable BlockPos airfield() {
        return airfield;
    }

    public void setAirfield(@Nullable BlockPos airfield) {
        this.airfield = airfield;
    }

    public int skin() {
        return entityData.get(DATA_SKIN);
    }

    public void setSkin(int skin) {
        entityData.set(DATA_SKIN, Math.floorMod(skin, CitizenEntity.SKINS));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (cityId != null) {
            tag.putUUID("city", cityId);
        }
        if (airfield != null) {
            tag.putLong("airfield", airfield.asLong());
        }
        tag.putInt("lifeTicks", lifeTicks);
        tag.putBoolean("leaving", leaving);
        tag.putInt("skin", skin());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        cityId = tag.hasUUID("city") ? tag.getUUID("city") : null;
        airfield = tag.contains("airfield") ? BlockPos.of(tag.getLong("airfield")) : null;
        lifeTicks = tag.getInt("lifeTicks");
        leaving = tag.getBoolean("leaving");
        setSkin(tag.getInt("skin"));
    }
}
