package com.branciho.citiesinlife.sim;

import com.branciho.citiesinlife.city.City;
import com.branciho.citiesinlife.city.CityData;
import com.branciho.citiesinlife.blockentity.WaterStorageBlockEntity;
import com.branciho.citiesinlife.power.PowerGrid;
import com.branciho.citiesinlife.structure.Structure;
import com.branciho.citiesinlife.water.WaterGrid;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * The aggregate city simulation.
 *
 * <p>Population is a <em>number</em>, not a collection of citizens. Cost scales with how many
 * structures a city has, never with how many people live in it, which is what makes a city of a
 * hundred thousand cost the same to simulate as a city of a hundred.
 */
public final class CitySimulation {

    /** How often growth runs. Ten seconds is frequent enough to feel responsive. */
    public static final int INTERVAL_TICKS = 200;

    /**
     * How fast population closes the gap to what the city can support.
     *
     * <p>Deliberately gradual. Instant population would make registering a tower feel like flipping a
     * switch rather than like a district filling up.
     */
    private static final double GROWTH_RATE = 0.12D;

    /**
     * How much of a new building's capacity is occupied the moment it is registered.
     *
     * <p>Without this, registering a residential block and immediately opening the city panel showed
     * zero residents and looked broken — the growth tick had simply not run yet. A quarter of capacity
     * moving in straight away is enough for the number to visibly respond to what you just did, while
     * still leaving most of the filling-up to happen over time.
     */
    private static final double IMMEDIATE_OCCUPANCY = 0.25D;

    /** Tax per resident and per filled job, per growth step. */
    private static final long TAX_PER_RESIDENT = 1L;
    private static final long TAX_PER_JOB = 2L;

    /** Upkeep per claimed chunk, so unlimited land grabbing has a running cost. */
    private static final long UPKEEP_PER_CHUNK = 1L;

    /** Residents served by one unit of power, and jobs served by one unit. */
    private static final int RESIDENTS_PER_POWER = 40;
    private static final int JOBS_PER_POWER = 20;

    /**
     * Residents served by one unit of water.
     *
     * <p>Only people drink; a factory's demand is folded into the people who work in it. One starter
     * pump lifts forty units, so a single intake waters a thousand residents — enough that the first
     * pumping station is a real milestone and the second one is a real decision.
     */
    private static final int RESIDENTS_PER_WATER = 25;

    /**
     * How much of normal growth a city manages with no water at all.
     *
     * <p>Harsher than a blackout, because you can read by candlelight and you cannot drink by it.
     * Still not zero: a city should visibly stall rather than empty out over a burst pipe.
     */
    private static final double UNWATERED_GROWTH = 0.15D;

    /**
     * How much of normal growth a city manages with no power at all.
     *
     * <p>Not zero. A blackout should make a city stagnate and visibly stop growing, not empty out -
     * punishing someone's first unfinished grid by deleting their population would be miserable.
     */
    private static final double UNPOWERED_GROWTH = 0.25D;

    private CitySimulation() {
    }

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % INTERVAL_TICKS != 0) {
            return;
        }
        CityData data = CityData.get(server);
        PowerGrid grid = PowerGrid.get(server);
        WaterGrid water = WaterGrid.get(server);
        for (City city : data.cities()) {
            recalculate(data, city);
            updatePower(server, grid, city);
            updateWater(server, water, city);
            grow(city);
            collectTaxes(city);
        }
        data.setDirty();
    }

    /**
     * Bring a city's numbers up to date immediately.
     *
     * <p>Called whenever a structure is registered or removed, so the city panel reflects what the
     * player just did instead of whatever the last growth tick left behind.
     */
    public static void refresh(CityData data, City city) {
        recalculate(data, city);

        // Nudge population toward the new capacity at once, so an action has a visible consequence.
        int target = supportable(city);
        if (target > city.population()) {
            int seeded = (int) Math.ceil((target - city.population()) * IMMEDIATE_OCCUPANCY);
            city.setPopulation(city.population() + Math.max(1, seeded));
        } else if (target < city.population()) {
            city.setPopulation(target);
        }
        city.setEmployed(Math.min(city.population(), city.jobs()));
        data.setDirty();
    }

    /** Ask the grid what actually reaches this city. */
    private static void updatePower(MinecraftServer server, PowerGrid grid, City city) {
        ServerLevel level = server.getLevel(city.dimension());
        int produced = level == null ? 0 : grid.supplyFor(level, city);
        city.setPower(produced, city.powerNeeded());
    }

    /**
     * Fill the city's tanks from whatever the pumps delivered, then let the city drink.
     *
     * <p>The buffer is the reason tanks exist. Without one, closing a valve would show up as the
     * population stalling on the very same tick, which reads as a bug rather than as a consequence.
     * With one, the tanks visibly run down first and there is time to notice and go and look.
     */
    private static void updateWater(MinecraftServer server, WaterGrid grid, City city) {
        int demand = (int) Math.ceil(city.population() / (double) RESIDENTS_PER_WATER);
        ServerLevel level = server.getLevel(city.dimension());
        if (level == null) {
            city.setWater(0, demand);
            return;
        }

        LongArrayList tanks = grid.storagesFor(level, city);
        if (tanks.isEmpty()) {
            city.setWater(0, demand);
            return;
        }

        int incoming = grid.supplyFor(level, city);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (long node : tanks) {
            if (incoming <= 0) {
                break;
            }
            cursor.set(BlockPos.getX(node), BlockPos.getY(node), BlockPos.getZ(node));
            if (level.getBlockEntity(cursor) instanceof WaterStorageBlockEntity tank) {
                incoming -= tank.fill(incoming);
            }
        }

        int drawn = 0;
        for (long node : tanks) {
            if (drawn >= demand) {
                break;
            }
            cursor.set(BlockPos.getX(node), BlockPos.getY(node), BlockPos.getZ(node));
            if (level.getBlockEntity(cursor) instanceof WaterStorageBlockEntity tank) {
                drawn += tank.drain(demand - drawn);
            }
        }
        city.setWater(drawn, demand);
    }

    private static int demandFor(int housing, int jobs) {
        return (int) Math.ceil(housing / (double) RESIDENTS_PER_POWER)
                + (int) Math.ceil(jobs / (double) JOBS_PER_POWER);
    }

    /** Recompute what the city's buildings offer. Pure capacity, no growth. */
    private static void recalculate(CityData data, City city) {
        int housing = 0;
        int jobs = 0;
        for (Structure structure : data.structuresOf(city)) {
            housing += structure.residents();
            jobs += structure.jobs();
        }
        city.setCapacity(housing, jobs);
        city.setPower(city.powerProduced(), demandFor(housing, jobs));
        city.setPopulation(Math.min(city.population(), housing));
        city.setEmployed(Math.min(city.population(), jobs));
    }

    /**
     * What this city can currently support.
     *
     * <p>People need somewhere to live <em>and</em> some prospect of work. Housing alone draws a
     * trickle; housing plus jobs fills the housing. A tower with no employment anywhere near it does
     * not fill up, which is the first real planning decision the player makes.
     */
    private static int supportable(City city) {
        return Math.min(city.housing(), city.jobs() * 2 + 20);
    }

    private static void grow(City city) {
        int target = supportable(city);
        int population = city.population();

        // A short grid slows a city down rather than stopping it dead, and so does a dry one.
        double rate = GROWTH_RATE;
        if (city.powerNeeded() > 0 && city.powerProduced() < city.powerNeeded()) {
            double covered = city.powerProduced() / (double) city.powerNeeded();
            rate *= UNPOWERED_GROWTH + (1.0D - UNPOWERED_GROWTH) * covered;
        }
        if (city.waterNeeded() > 0 && city.waterSupplied() < city.waterNeeded()) {
            double covered = city.waterSupplied() / (double) city.waterNeeded();
            rate *= UNWATERED_GROWTH + (1.0D - UNWATERED_GROWTH) * covered;
        }

        int delta = (int) Math.round((target - population) * rate);
        if (delta == 0 && population != target) {
            delta = target > population ? 1 : -1;
        }
        city.setPopulation(Math.max(0, population + delta));
        city.setEmployed(Math.min(city.population(), city.jobs()));
    }

    private static void collectTaxes(City city) {
        long income = (long) city.population() * TAX_PER_RESIDENT + (long) city.employed() * TAX_PER_JOB;
        long upkeep = (long) city.claimedChunks().size() * UPKEEP_PER_CHUNK;
        city.deposit(income - upkeep);
    }
}
