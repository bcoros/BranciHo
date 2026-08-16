package com.branciho.citiesinlife.sim;

import com.branciho.citiesinlife.city.City;
import com.branciho.citiesinlife.city.CityData;
import com.branciho.citiesinlife.structure.Structure;
import net.minecraft.server.MinecraftServer;

/**
 * The aggregate city simulation.
 *
 * <p>Population is a <em>number</em>, not a collection of citizens. Cost scales with how many
 * structures a city has, never with how many people live in it, which is what makes a city of a
 * hundred thousand cost the same to simulate as a city of a hundred.
 */
public final class CitySimulation {

    /** How often the whole thing runs. Ten seconds is frequent enough to feel responsive. */
    public static final int INTERVAL_TICKS = 200;

    /**
     * How fast population closes the gap to what the city can support.
     *
     * <p>Deliberately gradual. Instant population would make building a tower feel like flipping a
     * switch rather than like a district filling up.
     */
    private static final double GROWTH_RATE = 0.12D;

    /** Tax per resident and per filled job, per simulation step. */
    private static final long TAX_PER_RESIDENT = 1L;
    private static final long TAX_PER_JOB = 2L;

    /** Upkeep per claimed chunk, so unlimited land grabbing has a running cost. */
    private static final long UPKEEP_PER_CHUNK = 1L;

    private CitySimulation() {
    }

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % INTERVAL_TICKS != 0) {
            return;
        }
        CityData data = CityData.get(server);
        boolean changed = false;
        for (City city : data.cities()) {
            changed |= step(data, city);
        }
        if (changed) {
            data.setDirty();
        }
    }

    private static boolean step(CityData data, City city) {
        int housing = 0;
        int jobs = 0;
        for (Structure structure : data.structuresOf(city)) {
            housing += structure.residents();
            jobs += structure.jobs();
        }

        // People need somewhere to live and some prospect of work. Housing alone draws a trickle;
        // housing plus jobs fills the housing. A tower with no employment anywhere near it does not
        // fill up, which is the first real planning decision the player makes.
        int supportable = Math.min(housing, jobs * 2 + 20);

        int population = city.population();
        int delta = (int) Math.round((supportable - population) * GROWTH_RATE);
        if (delta == 0 && population != supportable) {
            delta = supportable > population ? 1 : -1;
        }
        population = Math.max(0, population + delta);

        int employed = Math.min(population, jobs);
        city.setTotals(population, jobs, employed);

        long income = population * TAX_PER_RESIDENT + employed * TAX_PER_JOB;
        long upkeep = (long) city.claimedChunks().size() * UPKEEP_PER_CHUNK;
        city.deposit(income - upkeep);
        return true;
    }
}
