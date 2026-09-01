package com.branciho.citiesinlife.entity;

import com.branciho.citiesinlife.city.CityData;
import com.branciho.citiesinlife.entity.ai.Shifts;
import com.branciho.citiesinlife.entity.ai.TownNavigation;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.phys.Vec3;
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
public class CitizenEntity extends PathfinderMob implements CityMember, Motorist {

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

    /**
     * How long this citizen has been trying to reach a car park.
     *
     * <p>Not saved, for the same reason the cooldown is not: it describes an attempt in progress,
     * not the person. Its whole job is to make "walked to the car park and stood there forever"
     * impossible, whatever the reason the bay could not be stood on.
     */
    private int boardingTicks;

    /** The bed it sleeps in, and the desk or till it works at. Either may be gone by morning. */
    private @Nullable BlockPos home;
    private @Nullable BlockPos workstation;

    /**
     * Whether the job is in somebody else's city.
     *
     * <p>Carried on the citizen rather than looked up from the workplace each time, because the
     * thing it governs is a refusal - a foreign commuter must never fall back to walking - and a
     * refusal that depends on a lookup succeeding is a refusal that stops working the moment the
     * lookup fails. Stored, so a reload does not turn an international commuter into somebody
     * setting off across the map on foot.
     */
    private boolean workAbroad;

    /**
     * A flight in progress: where it is going, and how much of it is left.
     *
     * <p>The citizen keeps its entity and its AI throughout and is simply carried. Discarding and
     * respawning it at the far end would be simpler and would lose the job, the home, the shift and
     * the name every time somebody flew to work.
     */
    private @Nullable BlockPos flightTo;
    private int flightTicks;

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

    /**
     * Whether the city has told everybody to get indoors and stay there.
     *
     * <p>Set by the director from the city's alert level rather than asked for per citizen, because
     * every goal that reads it runs at twenty hertz and a territory lookup at that rate is not
     * worth the freshness. Saved, so a reload in the middle of a war does not give one loose
     * five-second window of people strolling out to work.
     */
    private boolean curfew;

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
                // Doubles as the longest route the pathfinder will build, which is the number that
                // matters here: forty-eight blocks is not a commute across a city, it is a walk to
                // the end of the street. A route that has to go round a wall is far longer than the
                // straight line it replaces, so this is the distance a detour is allowed to be.
                .add(Attributes.FOLLOW_RANGE, 128.0D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SKIN, 0);
        builder.define(DATA_ACTIVITY, ACTIVITY_IDLE);
        builder.define(DATA_CRIMINAL, false);
    }

    /**
     * Think further ahead than a cow does.
     *
     * <p>See {@link TownNavigation}. Short version: vanilla's node budget cannot get round a wall,
     * so a citizen walks up to one and stops.
     */
    @Override
    protected PathNavigation createNavigation(Level level) {
        return new TownNavigation(this, level);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        // Immediately after floating, and above everything with a schedule. The router now plans
        // routes through closed doors, and a planned route through a door nobody opens is a route
        // into a door. Closing it behind them, because a town where every door stands open is a
        // town somebody has to walk round shutting.
        goalSelector.addGoal(0, new OpenDoorGoal(this, true));
        // Above work and sleep, because somebody who has decided to do this is not going to the
        // office first.
        goalSelector.addGoal(1, new CommitMurderGoal(this));
        // Work and sleep come first because they are the only things with a schedule. Everything
        // below them is what a citizen does with the rest of its day.
        goalSelector.addGoal(2, new CitizenWorkGoal(this));
        goalSelector.addGoal(3, new CitizenSleepGoal(this));
        goalSelector.addGoal(5, new StrollOnPathGoal(this, 0.9D));
        // Vanilla's stroll has no idea what a curfew is, so it is wrapped rather than used. Left
        // at the same priority it always had: this is the same goal, with one more reason to say no.
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8D) {
            @Override
            public boolean canUse() {
                return !curfew && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return !curfew && super.canContinueToUse();
            }
        });
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

    /** How high the arc gets over the halfway point. Above the trees, under the clouds. */
    private static final double FLIGHT_ALTITUDE = 28.0D;

    /**
     * One tick of a flight.
     *
     * <p>Nothing here is pathfinding. The citizen is off the ground with its navigation stopped and
     * is simply placed, which is the only way to cross the several hundred blocks an international
     * commute can be - the vanilla pathfinder gives up long before that, and it is the reason a
     * foreign job is refused outright unless there is a car or a flight to take it.
     */
    private void tickFlight() {
        BlockPos to = flightTo;
        if (to == null) {
            return;
        }
        if (--flightTicks <= 0) {
            flightTo = null;
            setNoGravity(false);
            moveTo(to.getX() + 0.5D, to.getY() + 1.0D, to.getZ() + 0.5D, getYRot(), 0.0F);
            setDeltaMovement(Vec3.ZERO);
            return;
        }
        setNoGravity(true);
        setDeltaMovement(Vec3.ZERO);
        getNavigation().stop();

        double remaining = flightTicks;
        double dx = (to.getX() + 0.5D - getX()) / remaining;
        double dz = (to.getZ() + 0.5D - getZ()) / remaining;
        // Climb while there is a long way to go, level off, then come down. Expressed against the
        // ticks left rather than against the distance covered, so a flight that starts from the
        // wrong place still lands.
        double targetY = to.getY() + 1.0D + FLIGHT_ALTITUDE * Math.min(1.0D, flightTicks / 40.0D);
        double dy = (targetY - getY()) / Math.max(1.0D, remaining * 0.5D);
        setPos(getX() + dx, getY() + dy, getZ() + dz);
        setYRot((float) (Math.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F);
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

        // Sleeping survives a save; the goal that started it does not.
        //
        // LivingEntity.readAdditionalSaveData restores Pose.SLEEPING straight from NBT, but a
        // reloaded citizen has a brand new, empty goal selector - so CitizenSleepGoal.stop() is
        // never called and nothing ever clears the pose. The work and stroll goals then walk the
        // citizen around lying down, forever, and the only thing in vanilla that undoes it is
        // LivingEntity.hurt. Which is exactly why hitting them fixed it.
        //
        // This lives in aiStep because aiStep runs whatever the goal selector is doing, so it is
        // the one place that can catch a pose whose owner has gone.
        if (isSleeping()) {
            BlockPos bed = getSleepingPos().orElse(null);
            boolean stillInBed = bed != null
                    && level().getBlockState(bed).getBlock() instanceof BedBlock
                    && blockPosition().distSqr(bed) <= 4.0D;
            // The curfew term matters more than it looks. Without it a citizen sent to bed at
            // noon is put there by the goal and thrown out again here on the very next tick,
            // forever, twenty times a second.
            if (!stillInBed || !(curfew || Shifts.sleepingHours(level()))) {
                stopSleeping();
            } else {
                // Asleep and legitimately so. Pin them: a sleeper who is still being pathed
                // somewhere slides across the floor in a sleeping pose, which is the other half
                // of what this looked like.
                getNavigation().stop();
                setDeltaMovement(Vec3.ZERO);
            }
        }

        // In the air. Moved by hand along the straight line to the far airfield, climbing for the
        // first half and descending for the second, because a citizen who simply vanished at one
        // airport and appeared at another would make the two airports pointless scenery.
        if (flightTo != null) {
            tickFlight();
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

    @Override
    public @Nullable UUID carId() {
        return carId;
    }

    /**
     * Held at "driving" for the whole journey, and put back to idle at the end of it.
     *
     * <p>This is the entire reason a citizen needs the hook at all. Their goals keep running
     * while the car carries them - deliberately, so the two places that clean a broken trip up
     * stay reachable - and without an activity that says "busy" one of those goals would decide
     * to wander off from inside a moving car.
     */
    @Override
    public void ridingChanged(boolean aboard) {
        setActivity(aboard ? ACTIVITY_DRIVING : ACTIVITY_IDLE);
    }

    /** A citizen drives their own car. */
    @Override
    public CarEntity.Livery livery() {
        return CarEntity.Livery.SALOON;
    }

    @Override
    public void setCarId(@Nullable UUID carId) {
        this.carId = carId;
    }

    public boolean mayLookForCar() {
        return driveCooldown <= 0;
    }

    public int boardingTicks() {
        return boardingTicks;
    }

    public void resetBoarding() {
        boardingTicks = 0;
    }

    /** Counted up only by Commute, and only while actually walking towards a bay. */
    public void tickBoarding(int ticks) {
        boardingTicks += ticks;
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

    public boolean workAbroad() {
        return workAbroad;
    }

    public void setWorkAbroad(boolean abroad) {
        this.workAbroad = abroad;
    }

    public boolean flying() {
        return flightTo != null;
    }

    /** Begin a flight. The citizen is carried from where it stands to the far airfield. */
    public void beginFlight(BlockPos destination, int ticks) {
        this.flightTo = destination;
        this.flightTicks = Math.max(1, ticks);
        getNavigation().stop();
    }

    public void setWorkstation(@Nullable BlockPos workstation) {
        this.workstation = workstation;
    }

    public boolean nightShift() {
        return nightShift;
    }

    /** Whether this citizen is under a curfew and should be at home whatever the hour. */
    public boolean curfew() {
        return curfew;
    }

    public void setCurfew(boolean curfew) {
        this.curfew = curfew;
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
        tag.putBoolean("curfew", curfew);
        tag.putBoolean("workAbroad", workAbroad);
        if (flightTo != null) {
            tag.putLong("flightTo", flightTo.asLong());
            tag.putInt("flightTicks", flightTicks);
        }
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
        curfew = tag.getBoolean("curfew");
        workAbroad = tag.getBoolean("workAbroad");
        flightTo = tag.contains("flightTo") ? BlockPos.of(tag.getLong("flightTo")) : null;
        flightTicks = tag.getInt("flightTicks");
        setCriminal(tag.getBoolean("criminal"));
        if (tag.contains("criminalUntil")) {
            criminalUntil = tag.getLong("criminalUntil");
        }
        setSkin(tag.getInt("skin"));
        carId = tag.hasUUID("car") ? tag.getUUID("car") : null;
    }
}
