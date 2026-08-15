package com.branciho.livingcities.entity;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * A representative citizen: one of the small number of physical bodies that stand in for the city's
 * virtual population.
 *
 * <p>These are deliberately disposable. They are never saved to disk, they carry no inventory, no
 * persistent identity and no per-citizen simulation state. A city of 50,000 might show 80 of these;
 * their only job is to make the streets look inhabited.
 *
 * <p>Kept intentionally cheap: a short goal list, no vanilla despawn/breeding/pickup behaviour, and
 * a small tracking range.
 */
public class CitizenEntity extends PathfinderMob {

    /** Chooses which skin variant the client renders, so a crowd is not all one person. */
    private static final EntityDataAccessor<Integer> DATA_VARIANT =
            SynchedEntityData.defineId(CitizenEntity.class, EntityDataSerializers.INT);

    public static final int VARIANT_COUNT = 4;

    public CitizenEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.23D)
                .add(Attributes.FOLLOW_RANGE, 16.0D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_VARIANT, 0);
    }

    public int variant() {
        return this.entityData.get(DATA_VARIANT);
    }

    public void setVariant(int variant) {
        this.entityData.set(DATA_VARIANT, Math.floorMod(variant, VARIANT_COUNT));
    }

    @Override
    protected void registerGoals() {
        // Enough behaviour to look alive, nothing that costs a full-city pathfind.
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    /**
     * Representative NPCs are regenerated from the simulation, never restored from disk. Saving them
     * would bloat region files with crowds that the spawn director would immediately replace anyway.
     */
    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return true;
    }

    /** Citizens are scenery, not a mob farm: they drop nothing and give no experience. */
    @Override
    protected int getBaseExperienceReward() {
        return 0;
    }
}
