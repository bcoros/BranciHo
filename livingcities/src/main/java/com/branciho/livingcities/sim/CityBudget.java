package com.branciho.livingcities.sim;

/**
 * The result of assessing one city's books for a simulation step.
 *
 * <p>Figures are <em>daily</em> rates in cents, not amounts already moved. Storing rates rather than
 * per-step amounts means the UI can show "$1,940/day" regardless of how the server has tuned
 * {@code simulationIntervalTicks}, and it keeps the proration in exactly one place
 * ({@link CitySimulation}) instead of smeared through the tax code.
 *
 * @param dailyIncomeCents     tax revenue per in-game day
 * @param dailyExpenseCents    upkeep and services per in-game day
 * @param effectiveTaxPermille revenue as a share of the assessed base, i.e. the burden the city
 *                             actually levies rather than any single configured percentage
 * @param civicCells           usable floor area given over to civic uses; produced by the same floor
 *                             walk the tax base needs, so it rides along rather than costing a second pass
 */
public record CityBudget(
        long dailyIncomeCents,
        long dailyExpenseCents,
        int effectiveTaxPermille,
        int civicCells) {

    public static final CityBudget EMPTY = new CityBudget(0L, 0L, 0, 0);

    public long netDailyCents() {
        return dailyIncomeCents - dailyExpenseCents;
    }
}
