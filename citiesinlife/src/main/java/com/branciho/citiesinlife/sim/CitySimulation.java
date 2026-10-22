package com.branciho.citiesinlife.sim;

import com.branciho.citiesinlife.city.City;
import com.branciho.citiesinlife.city.Demolition;
import com.branciho.citiesinlife.city.CityData;
import com.branciho.citiesinlife.blockentity.SewageCollectorBlockEntity;
import com.branciho.citiesinlife.blockentity.WaterStorageBlockEntity;
import com.branciho.citiesinlife.nuclear.NuclearSimulation;
import com.branciho.citiesinlife.power.PowerGrid;
import com.branciho.citiesinlife.structure.Structure;
import com.branciho.citiesinlife.structure.StructureType;
import com.branciho.citiesinlife.water.WaterGrid;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

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

    /**
     * How much of a city dies per step when every tap in it is running sewage.
     *
     * <p>The only thing in this simulation that drives population down on its own. Everything else
     * is a brake on growth, deliberately - punishing an unfinished grid by deleting people would be
     * miserable. This one is different because it is not something you fail to build, it is
     * something you have to actively plumb wrong, the end pipe has been running brown since the
     * moment you did it, and there is no way to do it by accident. Six per cent a step empties a
     * city in about seven minutes: fast enough to be a disaster, slow enough to notice and fix.
     */
    private static final double POISONED_DEATH_RATE = 0.06D;

    private CitySimulation() {
    }

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % INTERVAL_TICKS != 0) {
            return;
        }
        CityData data = CityData.get(server);
        PowerGrid grid = PowerGrid.get(server);
        WaterGrid water = WaterGrid.get(server);
        // Reactors first, so the generation a plant produces this step is standing on the grid
        // before updatePower asks a city how much power it has. Running them after the loop - as
        // this did, under a comment claiming the opposite - meant a plant that had just come up
        // read as producing nothing for one whole ten-second step.
        NuclearSimulation.tick(server);
        // Two passes with the trade between them, and the split is not cosmetic. A sale needs both
        // cities' own figures to already exist, and growth needs the imports to already have landed
        // - so what a city makes is worked out for everybody, then the deals are settled, and only
        // then does anybody grow on the strength of what they ended up with.
        for (City city : data.cities()) {
            city.clearImports();
            recalculate(data, city);
            updatePower(server, grid, city);
            updateWater(server, water, city);
            updateSewage(server, water, city);
        }
        UtilityTrade.run(server, data);
        for (City city : data.cities()) {
            makeRubbish(city);
            collect(data, city);
            grow(city);
            upkeep(server, data, city);
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

    /**
     * What the editor's Boost adds to one kind of output across this whole city.
     *
     * <p>Summed rather than taken from one building, so three boosted plants add up the way three
     * real plants would. Costs a walk of the city's structure list, which every other figure on the
     * panel already pays for.
     */
    private static int boostOf(CityData data, City city, java.util.function.Predicate<Structure> which) {
        int total = 0;
        for (Structure structure : data.structuresOf(city)) {
            if (structure.boost() > 0 && which.test(structure)) {
                total += structure.boost();
            }
        }
        return total;
    }

    /** Ask the grid what actually reaches this city, then add whatever the editor promised. */
    private static void updatePower(MinecraftServer server, PowerGrid grid, City city) {
        ServerLevel level = server.getLevel(city.dimension());
        int produced = level == null ? 0 : grid.supplyFor(level, city);
        produced += boostOf(CityData.get(server), city,
                structure -> structure.type().isPlant());
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
        // The city hall's Boost, which is where water lives because water has no box of its own.
        // Added in every branch below rather than at the end: two of them return early, and a
        // boost that quietly stops working when you take your last tank out would be a mystery.
        int boosted = boostOf(CityData.get(server), city,
                structure -> structure.type() == StructureType.CITY_CORE);
        ServerLevel level = server.getLevel(city.dimension());
        if (level == null) {
            city.setWater(boosted, demand);
            return;
        }

        LongArrayList tanks = grid.storagesFor(level, city);
        if (tanks.isEmpty()) {
            city.setWater(boosted, demand);
            city.setWaterTainted(0);
            return;
        }

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        // Fill each run of plumbing from its own supply. Pooling the city's water and pouring it
        // into whichever tank happened to come first meant a tank behind a shut valve filled from a
        // pump it was not connected to, while the tank the valve had opened stayed empty.
        int tainted = 0;
        for (WaterGrid.Delivery delivery : grid.deliveriesFor(level, city)) {
            // A run with a sewage collector on it fills its tanks with the city's own sewage. The
            // water system has always allowed this to be plumbed - a pipe is a pipe, and there is
            // no such thing as a sewage pipe in this mod - and this is what it costs.
            if (delivery.sewage()) {
                tainted += delivery.tanks().size();
            }
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
        city.setWater(drawn + boosted, demand);
        // By share of the tanks, not as a yes or no: a city with four pumping stations and one
        // crossed connection is a different problem from one drinking nothing but sewage.
        int was = city.waterTainted();
        city.setWaterTainted(Math.round(100.0F * tainted / tanks.size()));
        // Only on the way in, and only once. The city panel and the end pipe both say so from then
        // on; repeating it every ten seconds would be the game shouting at somebody who already
        // knows, right while they are trying to find the pipe.
        if (was == 0 && city.waterTainted() > 0) {
            ServerPlayer owner = server.getPlayerList().getPlayer(city.owner());
            if (owner != null) {
                owner.sendSystemMessage(Component.translatable(
                        "message.citiesinlife.water_tainted", city.name()));
            }
        }
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
        CityData data = CityData.get(server);
        // The same city hall figure that lifted the water. It has to be the same one: production
        // here IS what the city drank, so boosting the supply without boosting the sewers would
        // manufacture untreated sewage out of nothing and pile it up as rubbish.
        int boosted = boostOf(data, city, structure -> structure.type() == StructureType.CITY_CORE);
        ServerLevel level = server.getLevel(city.dimension());
        if (level == null) {
            city.setSewage(Math.min(boosted, produced), produced);
            return;
        }

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
        city.setSewage(Math.min(handled + boosted, produced), produced);

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

    /**
     * How much of a building comes back per growth step.
     *
     * <p>A fortieth of what it is worth, so any building — hut or tower — comes back from nothing
     * in about seven minutes of peace. It was a two-hundredth, which worked out at one point every
     * ten seconds on a small house: technically healing, and completely invisible to anybody
     * watching the bar, which is the same thing as not healing at all.
     */
    private static final int REPAIR_SHARE = 40;

    /**
     * How many uncounted buildings are surveyed per city per step.
     *
     * <p>Every building registered before health existed has never had its material counted, and
     * counting one is a walk over its whole box. Two per city per ten seconds gets a mature city
     * done in a few minutes without ever costing a visible tick.
     */
    private static final int RECOUNTS_PER_STEP = 2;

    /**
     * Buildings mend themselves while nobody is knocking them down, and buildings nobody has ever
     * counted get counted.
     *
     * <p>The mending is not free healing so much as the alternative to bookkeeping: without it,
     * damage is permanent until the box is deleted and redrawn, and a city that has ever been
     * shelled carries the scars for the rest of the save with no way to clear them.
     *
     * <p>The counting is the migration. A building from before this version has no idea what it is
     * made of, and until it does it has no honest health — so this walks a couple of them per step
     * until the whole city is accounted for. Only ones whose ground is loaded: a survey of blocks
     * that are not in memory is a survey of nothing.
     */
    private static void upkeep(MinecraftServer server, CityData data, City city) {
        ServerLevel level = server.getLevel(city.dimension());
        int budget = RECOUNTS_PER_STEP;
        for (Structure structure : data.structuresOf(city)) {
            if (budget > 0 && !structure.massKnown() && level != null
                    && structure.dimension().equals(level.dimension())
                    && level.isLoaded(structure.min())) {
                Demolition.recount(level, structure);
                budget--;
                data.setDirty();
            }
            if (structure.health() < structure.maxHealth()) {
                structure.heal(Math.max(1, structure.maxHealth() / REPAIR_SHARE));
                data.setDirty();
            }
        }
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

    /**
     * What a boosted depot clears on its own, every step.
     *
     * <p>Rubbish is the one utility that works backwards — it is a pile that has to be taken away
     * rather than a supply that has to arrive — so a depot's Boost <em>removes</em> where a plant's
     * adds. It works with no bin lorry standing in it, which is the whole point of a number you
     * type: the lorries are the simulation doing it properly, and this is the override.
     */
    private static void collect(CityData data, City city) {
        int cleared = boostOf(data, city,
                structure -> structure.type() == StructureType.GARBAGE_DEPOT);
        if (cleared > 0) {
            city.addRefuse(-cleared);
        }
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

        // Sewage in the mains kills people, and it overrides everything above it: a city drinking
        // its own sewage does not grow slowly, it buries somebody every ten seconds until the
        // plumbing is fixed. Nothing else in this simulation can drive the population down on its
        // own, which is exactly why this one is allowed to.
        if (city.waterTainted() > 0) {
            double dose = city.waterTainted() / 100.0D;
            int deaths = (int) Math.ceil(population * POISONED_DEATH_RATE * dose);
            delta = -Math.max(1, deaths);
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
