package com.branciho.livingcities.sim;

import java.util.UUID;

/**
 * An immutable picture of one city at the instant a simulation step finished with it.
 *
 * <p>Every downstream consumer - happiness, notifications, and eventually the UI - reads this instead
 * of reaching back into {@link com.branciho.livingcities.city.City} and
 * {@link com.branciho.livingcities.city.CityStats}. That matters because those objects keep mutating
 * as later cities are stepped, so a warning raised from live state could describe numbers that no
 * longer belong together. A snapshot is internally consistent by construction.
 *
 * <p>{@link #population()} is the population the step's economy and occupancy figures were computed
 * <em>from</em>, i.e. before this step's growth was applied. Growth lands on {@code CityStats} and is
 * therefore one step ahead of this record. Deriving homelessness or unemployment from the post-growth
 * number would misreport both, because nobody has been housed or employed at that figure yet.
 */
public record CitySnapshot(
        UUID cityId,
        int population,
        int housingCapacity,
        int housed,
        int jobs,
        int employed,
        int workforce,
        int civicCells,
        int claimedChunks,
        int buildings,
        long treasuryCents,
        long dailyIncomeCents,
        long dailyExpenseCents,
        int effectiveTaxPermille,
        int happinessPermille,
        int powerSatisfactionPermille,
        int waterSatisfactionPermille,
        boolean unpaidExpenses) {

    /** Residents with nowhere to live. Non-zero means housing capacity is the binding constraint. */
    public int homeless() {
        return Math.max(0, population - housed);
    }

    /** Jobs nobody turned up for. Non-zero means the city needs more housing, not more offices. */
    public int unfilledJobs() {
        return Math.max(0, jobs - employed);
    }

    /** Working-age residents without a job. Non-zero means the city needs employers. */
    public int unemployed() {
        return Math.max(0, workforce - employed);
    }

    public long netDailyCents() {
        return dailyIncomeCents - dailyExpenseCents;
    }

    /**
     * Copy carrying a freshly computed happiness figure.
     *
     * <p>Happiness is derived from everything else in this record, so the record is built first with
     * the previous step's value and corrected here rather than threading a dozen loose parameters
     * into {@link HappinessModel}.
     */
    public CitySnapshot withHappiness(int newHappinessPermille) {
        return new CitySnapshot(cityId, population, housingCapacity, housed, jobs, employed, workforce,
                civicCells, claimedChunks, buildings, treasuryCents, dailyIncomeCents, dailyExpenseCents,
                effectiveTaxPermille, newHappinessPermille, powerSatisfactionPermille,
                waterSatisfactionPermille, unpaidExpenses);
    }
}
