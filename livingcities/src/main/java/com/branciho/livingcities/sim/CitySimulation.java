package com.branciho.livingcities.sim;

import com.branciho.livingcities.building.Building;
import com.branciho.livingcities.city.City;
import com.branciho.livingcities.city.CityRegistry;
import com.branciho.livingcities.city.CityStats;
import com.branciho.livingcities.config.LivingCitiesConfig;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The aggregate city simulation: population, employment, money and mood as numbers.
 *
 * <p>A city of fifty thousand people is fifty thousand as an integer, not fifty thousand entities.
 * Everything in this package is O(buildings) at worst and completely independent of population, which
 * is what makes a large city cost the same to simulate as a small one.
 *
 * <h2>Staggering</h2>
 *
 * <p>{@link #tick} is called every server tick but almost never does work. Each city is assigned a
 * stable slot in a window of {@code simulationIntervalTicks} derived from its UUID, and runs only on
 * the tick matching its slot. That spreads the cost evenly instead of putting every city on the same
 * multiple-of-100 tick, where a hundred-city server would produce a visible stutter five times a second
 * and idle in between.
 *
 * <p>The slot assignment is cached as a bucket list, so the per-tick cost is a single list lookup rather
 * than a scan over every city. The cache is rebuilt when the city count or the configured interval
 * changes, when the registry itself is replaced (world reload), and periodically as a safety net for
 * a simultaneous add and remove that leaves the count unchanged.
 *
 * <h2>Step ordering</h2>
 *
 * <p>Within a city the order is fixed and matters: capacity, then occupancy, then economy, then
 * happiness, then growth. Occupancy needs capacity; the tax base needs occupancy; happiness needs the
 * books; and growth needs happiness. Population is therefore the last thing to move, and every other
 * figure in a step describes the population the step began with.
 *
 * <p>Nothing here touches a {@code Level}, a {@code BlockState} or an entity. The simulation runs for
 * territory that is entirely unloaded, which is the point - a city does not stop existing because
 * nobody is standing in it.
 */
public final class CitySimulation {

    /** Length of a Minecraft day. Daily rates from {@link CityEconomy} are prorated against this. */
    public static final int TICKS_PER_DAY = 24_000;

    /** Upper bound on {@code simulationIntervalTicks} from the config, used to size overflow guards. */
    private static final int MAX_INTERVAL_TICKS = 2_000;

    /**
     * Largest daily cash flow we will carry into the proration arithmetic. Chosen so that
     * {@code flow * intervalTicks} cannot overflow a long at any permitted interval.
     */
    private static final long MAX_DAILY_FLOW_CENTS = Long.MAX_VALUE / (MAX_INTERVAL_TICKS * 2L);

    /** Rebuild the stagger schedule at least this often regardless of what looks unchanged. */
    private static final int SCHEDULE_REFRESH_TICKS = 20 * 60 * 5;

    /** Fibonacci hashing constant; mixes UUID bits so slot assignment is even, not just random-ish. */
    private static final long UUID_MIX = 0x9E3779B97F4A7C15L;

    private static final Map<UUID, CitySimState> STATE = new HashMap<>();

    // --- cached stagger schedule: slot -> cities due on that slot ---
    private static WeakReference<CityRegistry> scheduleOwner = new WeakReference<>(null);
    private static List<List<UUID>> scheduleBuckets = List.of();
    private static int scheduleInterval;
    private static int scheduleCityCount = -1;
    private static long scheduleBuiltAt = Long.MIN_VALUE;

    private CitySimulation() {
    }

    /**
     * Called once per server tick by the integration layer. Server thread only.
     *
     * @param gameTime a monotonically increasing tick counter; the overworld's game time is the natural
     *                 source, but any steadily advancing value works since it is only used for slotting
     *                 and for notification cooldowns
     */
    public static void tick(MinecraftServer server, CityRegistry registry, long gameTime) {
        final int interval = LivingCitiesConfig.SERVER.simulationIntervalTicks.get();
        if (interval <= 0) {
            return;
        }

        final List<List<UUID>> buckets = schedule(registry, interval, gameTime);
        final List<UUID> due = buckets.get((int) Math.floorMod(gameTime, (long) interval));
        if (due.isEmpty()) {
            return;
        }

        boolean changed = false;
        for (int i = 0; i < due.size(); i++) {
            final City city = registry.byId(due.get(i));
            if (city == null) {
                // Removed since the schedule was built; the next rebuild will drop it.
                continue;
            }
            stepCity(server, registry, city, interval, gameTime);
            changed = true;
        }
        if (changed) {
            // Population, treasury and per-building occupancy all live in the saved data. Forgetting
            // this is a silent data-loss bug that only shows up after a restart.
            registry.setDirty();
        }
    }

    /**
     * Run one full simulation step for a single city, ignoring the stagger schedule.
     *
     * <p>Exposed for commands and tests that want an immediate result ("/city simulate"). Callers are
     * responsible for {@link CityRegistry#setDirty()}.
     *
     * @param deltaTicks how much simulated time this step represents; pass the configured interval for
     *                   a normal step
     */
    public static void stepCity(MinecraftServer server, CityRegistry registry,
                                City city, int deltaTicks, long gameTime) {
        final CityStats stats = city.stats();
        final CitySimState state = STATE.computeIfAbsent(city.id(), key -> new CitySimState());
        final List<Building> buildings = registry.buildingsOf(city);
        final int count = buildings.size();

        // --- 1. capacity -----------------------------------------------------------------------
        // Capacities are cached in arrays because every one of them walks the building's floor list,
        // and the allocator and the tax pass both need them again.
        final int[] housingCap = new int[count];
        final int[] jobCap = new int[count];
        long housingTotal = 0L;
        long jobTotal = 0L;
        for (int i = 0; i < count; i++) {
            final Building building = buildings.get(i);
            housingCap[i] = building.housingCapacity();
            jobCap[i] = building.jobCapacity();
            housingTotal += housingCap[i];
            jobTotal += jobCap[i];
        }
        final int housingCapacity = (int) Math.min(housingTotal, Integer.MAX_VALUE);
        final int jobCapacity = (int) Math.min(jobTotal, Integer.MAX_VALUE);
        stats.setHousingCapacity(housingCapacity);
        stats.setJobs(jobCapacity);

        // --- 2. occupancy ----------------------------------------------------------------------
        // Population is read before growth is applied, so every figure below describes one consistent
        // moment. People who do not fit stay in the total and surface as homeless rather than being
        // quietly deleted, because a demolished apartment block should be a visible crisis.
        final int population = stats.population();
        final int workforce = stats.workforce();
        final int housed = EmploymentAllocator.distribute(buildings, housingCap, population, Building::setResidents);
        final int employed = EmploymentAllocator.distribute(buildings, jobCap, workforce, Building::setWorkers);
        stats.setEmployed(employed);

        // --- 3. economy ------------------------------------------------------------------------
        final CityBudget budget = CityEconomy.assess(city, buildings, housingCap, jobCap, population);
        stats.setDailyIncomeCents(budget.dailyIncomeCents());
        stats.setDailyExpenseCents(budget.dailyExpenseCents());
        final boolean unpaid = settle(city, state, budget, deltaTicks);

        // --- 4. happiness ----------------------------------------------------------------------
        final int powerPermille = utilitySatisfactionPermille(buildings, true);
        final int waterPermille = utilitySatisfactionPermille(buildings, false);
        stats.setPowerSatisfactionPermille(powerPermille);
        stats.setWaterSatisfactionPermille(waterPermille);

        final CitySnapshot provisional = new CitySnapshot(
                city.id(), population, housingCapacity, housed, jobCapacity, employed, workforce,
                budget.civicCells(), city.claimCount(), count,
                city.treasuryCents(), budget.dailyIncomeCents(), budget.dailyExpenseCents(),
                budget.effectiveTaxPermille(), stats.happinessPermille(), powerPermille, waterPermille, unpaid);

        final HappinessBreakdown happiness = HappinessModel.evaluate(provisional);
        stats.setHappinessPermille(happiness.totalPermille());

        // --- 5. growth -------------------------------------------------------------------------
        PopulationModel.step(stats, housingCapacity, jobCapacity, budget.effectiveTaxPermille());

        // --- 6. publish ------------------------------------------------------------------------
        final CitySnapshot snapshot = provisional.withHappiness(happiness.totalPermille());
        state.happiness = happiness;
        state.snapshot = snapshot;
        CityNotifications.evaluate(server, city, snapshot, gameTime);
    }

    /**
     * Move this step's share of the daily net flow through the treasury.
     *
     * <p>The daily rate is prorated by {@code deltaTicks / TICKS_PER_DAY}, which for the default
     * 100-tick interval is 1/240 of a day - often only a handful of cents. Truncating that would round
     * most small cities' entire economy to zero, so the sub-cent remainder is carried in
     * {@link CitySimState#cashCarryCents} exactly the way population carries its fractional part.
     * The carry is transient; a restart loses at most one cent's worth of accrual.
     *
     * @return true if the city could not cover its bills in full
     */
    /**
     * How well a utility is serving the city, weighted by demand.
     *
     * <p>Weighted rather than averaged per building: a tower drawing a megawatt going dark matters far
     * more than a shed, and a plain average would let a hundred served sheds hide it.
     *
     * <p>A city drawing nothing is fully satisfied. Otherwise a brand new city would open unhappy
     * about a utility it has not built anything to need yet.
     */
    private static int utilitySatisfactionPermille(List<Building> buildings, boolean power) {
        boolean required = power
                ? LivingCitiesConfig.SERVER.powerRequired.get()
                : LivingCitiesConfig.SERVER.waterRequired.get();
        if (!required) {
            return 1000;
        }
        long demand = 0L;
        double served = 0.0D;
        for (Building building : buildings) {
            int buildingDemand = power ? building.powerDemandKw() : building.waterDemand();
            if (buildingDemand <= 0) {
                continue;
            }
            demand += buildingDemand;
            served += buildingDemand
                    * (double) (power ? building.powerSatisfaction() : building.waterSatisfaction());
        }
        if (demand <= 0L) {
            return 1000;
        }
        return (int) Math.round(Math.clamp(served / demand, 0.0D, 1.0D) * 1000.0D);
    }

    private static boolean settle(City city, CitySimState state, CityBudget budget, int deltaTicks) {
        final long netDaily = Math.clamp(budget.netDailyCents(), -MAX_DAILY_FLOW_CENTS, MAX_DAILY_FLOW_CENTS);

        // floorDiv/floorMod rather than / and % so that a deficit rounds consistently downward and the
        // carry stays non-negative; plain % would flip sign with the numerator and leak money.
        final long accrued = netDaily * deltaTicks + state.cashCarryCents;
        final long applied = Math.floorDiv(accrued, TICKS_PER_DAY);
        state.cashCarryCents = Math.floorMod(accrued, TICKS_PER_DAY);

        if (applied > 0L) {
            city.deposit(applied);
            return false;
        }
        if (applied == 0L) {
            return false;
        }

        final long owed = -applied;
        if (city.withdraw(owed)) {
            return false;
        }
        // Not enough in the treasury: take what there is rather than refusing the whole payment, so a
        // struggling city drains toward zero instead of hovering with a frozen balance and no signal.
        final long available = city.treasuryCents();
        if (available > 0L) {
            city.withdraw(available);
        }
        return true;
    }

    // ------------------------------------------------------------------ queries

    /** The most recent completed step for a city, or null if it has not been simulated this session. */
    public static @Nullable CitySnapshot snapshotOf(UUID cityId) {
        final CitySimState state = STATE.get(cityId);
        return state == null ? null : state.snapshot;
    }

    /** The most recent happiness breakdown for a city, for a UI that wants to explain the number. */
    public static @Nullable HappinessBreakdown happinessOf(UUID cityId) {
        final CitySimState state = STATE.get(cityId);
        return state == null ? null : state.happiness;
    }

    /** Drop all transient simulation state. Call when a server stops. */
    public static void reset() {
        STATE.clear();
        CityNotifications.reset();
        scheduleOwner = new WeakReference<>(null);
        scheduleBuckets = List.of();
        scheduleCityCount = -1;
        scheduleBuiltAt = Long.MIN_VALUE;
    }

    // ------------------------------------------------------------------ stagger schedule

    private static List<List<UUID>> schedule(CityRegistry registry, int interval, long gameTime) {
        final int cityCount = registry.cities().size();
        final boolean fresh = scheduleOwner.get() == registry
                && scheduleInterval == interval
                && scheduleCityCount == cityCount
                && gameTime >= scheduleBuiltAt
                && gameTime - scheduleBuiltAt < SCHEDULE_REFRESH_TICKS;
        if (fresh) {
            return scheduleBuckets;
        }

        final List<List<UUID>> next = new ArrayList<>(interval);
        for (int i = 0; i < interval; i++) {
            next.add(null);
        }

        final Set<UUID> live = new HashSet<>(Math.max(4, cityCount));
        for (City city : registry.cities()) {
            final UUID id = city.id();
            live.add(id);
            final int slot = slotOf(id, interval);
            List<UUID> bucket = next.get(slot);
            if (bucket == null) {
                bucket = new ArrayList<>(2);
                next.set(slot, bucket);
            }
            bucket.add(id);
        }
        for (int i = 0; i < interval; i++) {
            if (next.get(i) == null) {
                // List.of() is a shared immutable instance, so empty slots cost nothing.
                next.set(i, List.of());
            }
        }

        // A rebuild is the natural moment to forget cities that have been disbanded; nothing else in
        // this package is notified when one disappears.
        STATE.keySet().retainAll(live);
        CityNotifications.retainOnly(live);

        scheduleBuckets = next;
        scheduleOwner = new WeakReference<>(registry);
        scheduleInterval = interval;
        scheduleCityCount = cityCount;
        scheduleBuiltAt = gameTime;
        return next;
    }

    /**
     * A city's stable slot in the stagger window.
     *
     * <p>Stable across restarts because it is derived only from the UUID, so a city keeps its slot and
     * the spread does not reshuffle every time the schedule is rebuilt. The multiply-xorshift mix is
     * there because taking a UUID modulo a small window can clump when the low bits are not uniform -
     * for version-1 or externally supplied UUIDs, which we do not control.
     */
    private static int slotOf(UUID cityId, int interval) {
        long bits = cityId.getMostSignificantBits() ^ cityId.getLeastSignificantBits();
        bits *= UUID_MIX;
        bits ^= bits >>> 32;
        return (int) Math.floorMod(bits, (long) interval);
    }

    /** Transient per-city simulation state; nothing here is worth persisting. */
    private static final class CitySimState {

        /** Sub-cent cash accrual carried between steps. */
        private long cashCarryCents;

        private @Nullable CitySnapshot snapshot;

        private @Nullable HappinessBreakdown happiness;
    }
}
