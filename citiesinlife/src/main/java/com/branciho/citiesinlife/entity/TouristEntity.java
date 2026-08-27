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
public class TouristEntity extends PathfinderMob implements CityMember {

    private static final EntityDataAccessor<Integer> DATA_SKIN =
            SynchedEntityData.defineId(TouristEntity.class, EntityDataSerializers.INT);

    /** Five minutes of ticked time, then they catch their flight back. */
    public static final int LIFETIME_TICKS = 6000;

    private @Nullable UUID cityId;
    private @Nullable BlockPos airfield;
    private int lifeTicks;

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
        // No StrollOnPathGoal: it is typed to CitizenEntity, and a visitor has no reason to know
        // where the city's pavements are anyway.
        goalSelector.addGoal(1, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(3, new RandomLookAroundGoal(this));
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
        // Time up, or the airport they arrived at has been knocked down while they were here.
        if (lifeTicks >= LIFETIME_TICKS
                || (airfield != null && serverLevel.isLoaded(airfield)
                        && !(serverLevel.getBlockState(airfield).getBlock() instanceof AirfieldBlock))) {
            discard();
        }
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
        tag.putInt("skin", skin());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        cityId = tag.hasUUID("city") ? tag.getUUID("city") : null;
        airfield = tag.contains("airfield") ? BlockPos.of(tag.getLong("airfield")) : null;
        lifeTicks = tag.getInt("lifeTicks");
        setSkin(tag.getInt("skin"));
    }
}
