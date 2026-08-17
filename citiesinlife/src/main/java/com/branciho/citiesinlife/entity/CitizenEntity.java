package com.branciho.citiesinlife.entity;

import com.branciho.citiesinlife.entity.ai.CitizenSleepGoal;
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
public class CitizenEntity extends PathfinderMob {

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

    /** The city this one belongs to, so the director can count its own. */
    private @Nullable UUID cityId;

    /** The bed it sleeps in, and the desk or till it works at. Either may be gone by morning. */
    private @Nullable BlockPos home;
    private @Nullable BlockPos workstation;

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
                .add(Attributes.FOLLOW_RANGE, 48.0D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SKIN, 0);
        builder.define(DATA_ACTIVITY, ACTIVITY_IDLE);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
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

    // ----------------------------------------------------------------- living

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) {
            return;
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

    public @Nullable UUID cityId() {
        return cityId;
    }

    public void setCityId(@Nullable UUID cityId) {
        this.cityId = cityId;
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
        tag.putInt("skin", skin());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        cityId = tag.hasUUID("city") ? tag.getUUID("city") : null;
        home = tag.contains("home") ? BlockPos.of(tag.getLong("home")) : null;
        workstation = tag.contains("workstation") ? BlockPos.of(tag.getLong("workstation")) : null;
        nightShift = tag.getBoolean("nightShift");
        setSkin(tag.getInt("skin"));
    }
}
