package com.branciho.citiesinlife.sim;

import com.branciho.citiesinlife.city.City;
import com.branciho.citiesinlife.city.CityData;
import com.branciho.citiesinlife.blockentity.SewageCollectorBlockEntity;
import com.branciho.citiesinlife.blockentity.WaterStorageBlockEntity;
import com.branciho.citiesinlife.power.PowerGrid;
import com.branciho.citiesinlife.structure.Structure;
import com.branciho.citiesinlife.structure.StructureType;
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

    /**
     * What a city draws before a single resident moves in.
     *
     * <p>Street lighting and the city hall itself. Without a floor, a village of three houses asks
     * for two units of power and one solar panel covers it for the rest of the game - the utility
     * systems simply do not exist until the city is large, which is the wrong way round for the
     * point at which a player is learning them.
     */
    private static final int BASE_POWER = 4;
    private static final int BASE_WATER = 3;

    /**
     * Residents served by one unit of power, and jobs served by one unit.
     *
     * <p>Roughly twice what they were before this pass. The shape was never the problem - power has
     * always been charged against how much building there is, measured block by block - the numbers
     * were simply too small to ever require a second generator.
     */
    private static final int RESIDENTS_PER_POWER = 20;
    private static final int JOBS_PER_POWER = 10;

    /**
     * Residents and jobs served by one unit of water.
     *
     * <p>Charged against what the buildings <em>hold</em>, not against who has moved in yet — which
     * is how power has always worked and is the only reason these two numbers now mean the same
     * kind of thing. Billing water by current population made a tower with room for a thousand
     * people ask for four units on the day it was built, and since a small city tends to have about
     * as many people as it has buildings, it read as one unit per building regardless of size. A
     * building's plumbing is sized for the building.
     *
     * <p>Workplaces drink too, so jobs count as well — less per head, because a shop uses less water
     * per person than a home does.
     */
    private static final int RESIDENTS_PER_WATER = 25;
    private static final int JOBS_PER_WATER = 50;

    /**
     * How much rubbish a city makes per growth step, before its size is taken into account.
     *
     * <p>Refuse is the one utility that runs backwards. Everything else is a supply that has to keep
     * up with a demand; this is a demand that has to be kept <em>down</em>, and a city that ignores
     * it does not stop — it just stops being somewhere anybody wants to move to.
     */
    private static final int REFUSE_BASE = 2;
    private static final int RESIDENTS_PER_REFUSE = 150;

    /** How much of normal growth a city manages while it is knee deep in its own rubbish. */
    private static final double BURIED_GROWTH = 0.4D;

    /**
     * How much park it takes to house one more person than the buildings alone would.
     *
     * <p>Parks do not create housing; they make the housing that exists somewhere people want to
     * be. Twenty square metres a head is a generous rate, and deliberately so — a park is expensive
     * ground to give up and should be visibly worth it.
     */
    private static final int PARK_AREA_PER_RESIDENT = 20;

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

    /**
     * How much rubbish a unit of untreated sewage is worth per step.
     *
     * <p>Deliberately gentle. A city with no sewers at all should drift into a rubbish problem over
     * a session, not be buried by one within a minute of somebody plumbing in their first tap.
     */
    private static final double SEWAGE_TO_REFUSE = 0.15D;

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
            updateSewage(server, water, city);
            makeRubbish(city);
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
        int demand = waterFor(city.housing(), city.jobs());
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

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        // Fill each run of plumbing from its own supply. Pooling the city's water and pouring it
        // into whichever tank happened to come first meant a tank behind a shut valve filled from a
        // pump it was not connected to, while the tank the valve had opened stayed empty.
        for (WaterGrid.Delivery delivery : grid.deliveriesFor(level, city)) {
            int share = delivery.supply() / delivery.tanks().size();
            int remainder = delivery.supply() % delivery.tanks().size();
            for (long node : delivery.tanks()) {
                int pour = share + (remainder-- > 0 ? 1 : 0);
                if (pour <= 0) {
                    continue;
                }
                cursor.set(BlockPos.getX(node), BlockPos.getY(node), BlockPos.getZ(node));
                if (level.getBlockEntity(cursor) instanceof WaterStorageBlockEntity tank) {
                    tank.fill(pour);
                }
            }
        }

        // The city drinks from every tank it owns, wherever they are - a tank that is full is a tank
        // that can be drawn from, however it got that way.
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

    /**
     * Work out what the city's sewers managed this step.
     *
     * <p>Production is simply the water the city actually drank. That is not a simplification for
     * its own sake - it is the one figure a player can already see and already controls, so a city
     * that doubles its water use knows exactly why its sewage doubled too.
     *
     * <p>A collector only counts if it can reach an outfall outside every city's borders. One that
     * cannot is standing there plumbed into a dead end, and saying so when it is clicked is the only
     * way that is ever diagnosable.
     */
    private static void updateSewage(MinecraftServer server, WaterGrid grid, City city) {
        int produced = city.waterSupplied();
        ServerLevel level = server.getLevel(city.dimension());
        if (level == null) {
            city.setSewage(0, produced);
            return;
        }

        CityData data = CityData.get(server);
        LongArrayList collectors = grid.collectorsFor(level, city);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        int handled = 0;
        for (long node : collectors) {
            cursor.set(BlockPos.getX(node), BlockPos.getY(node), BlockPos.getZ(node));
            if (!(level.getBlockEntity(cursor) instanceof SewageCollectorBlockEntity collector)) {
                continue;
            }
            boolean connected = !grid.outfallsFrom(level, data, cursor.immutable()).isEmpty();
            // Told even when it is doing nothing, because "nothing" is the interesting case and the
            // block has no other way to find out.
            int through = 0;
            if (connected) {
                through = Math.min(collector.capacity(), Math.max(0, produced - handled));
                handled += through;
            }
            collector.report(through, connected);
        }
        city.setSewage(handled, produced);

        // Untreated sewage piles up as rubbish. Reusing refuse rather than inventing a second
        // unhappiness meter: it is the same complaint, it already has a tolerance scaled to the
        // population, and the player already knows what to do about a rising rubbish figure.
        int untreated = city.sewageUntreated();
        if (untreated > 0) {
            city.addRefuse((int) Math.ceil(untreated * SEWAGE_TO_REFUSE));
        }
    }

    private static int demandFor(int housing, int jobs) {
        return BASE_POWER
                + (int) Math.ceil(housing / (double) RESIDENTS_PER_POWER)
                + (int) Math.ceil(jobs / (double) JOBS_PER_POWER);
    }

    private static int waterFor(int housing, int jobs) {
        return BASE_WATER
                + (int) Math.ceil(housing / (double) RESIDENTS_PER_WATER)
                + (int) Math.ceil(jobs / (double) JOBS_PER_WATER);
    }

    /**
     * Rubbish piles up on its own.
     *
     * <p>Nothing in the mod removes it except a bin man, which is the whole argument for building a
     * depot: every other service answers something that might never happen, and this one answers
     * something that is happening right now in every city that has anybody in it.
     */
    private static void makeRubbish(City city) {
        city.addRefuse(REFUSE_BASE + city.population() / RESIDENTS_PER_REFUSE);
    }

    /** Recompute what the city's buildings offer. Pure capacity, no growth. */
    private static void recalculate(CityData data, City city) {
        int housing = 0;
        int jobs = 0;
        int parks = 0;
        for (Structure structure : data.structuresOf(city)) {
            housing += structure.residents();
            jobs += structure.jobs();
            if (structure.type() == StructureType.PARK) {
                parks += structure.footprint();
            }
        }
        city.setParkArea(parks);
        city.setCapacity(housing, jobs);
        city.setPower(city.powerProduced(), demandFor(housing, jobs));
        // Water demand is set here too, not only on the water tick, so registering a tower shows its
        // thirst on the panel straight away rather than ten seconds later.
        city.setWater(city.waterSupplied(), waterFor(housing, jobs));
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
        // Parks are the one thing that makes a city worth living in beyond the arithmetic of beds
        // and desks, so they lift the ceiling rather than adding housing that does not exist.
        int drawn = city.jobs() * 2 + 20 + city.parkArea() / PARK_AREA_PER_RESIDENT;
        return Math.min(city.housing(), drawn);
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
        if (city.refuse() > city.refuseTolerance()) {
            rate *= BURIED_GROWTH;
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
