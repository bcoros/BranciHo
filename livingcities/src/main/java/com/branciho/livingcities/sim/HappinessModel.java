package com.branciho.livingcities.sim;

import com.branciho.livingcities.city.CityStats;

import java.util.ArrayList;
import java.util.List;

/**
 * A small weighted model of civic mood.
 *
 * <p>Every factor produces a score in 0..1, and the total is their weighted sum. The weights live on
 * {@link HappinessFactor} and sum to exactly 1, so a perfect city scores 1000 permille and a city that
 * fails at everything scores 0 - no normalisation step, nothing to drift out of sync when a factor is
 * added.
 *
 * <h2>Why the denominators are capacity-relative</h2>
 *
 * <p>Every ratio here is measured against <em>capacity</em> (how much housing and job space exists),
 * never against current population. That looks like a stylistic choice and is not: it is what makes the
 * population feedback loop provably stable.
 *
 * <p>Population growth chases a target that is a multiple of happiness, and happiness depends on
 * population - a closed loop. {@link PopulationModel} proves that loop converges by bounding how much
 * the target can move per extra resident. With a capacity denominator, a resident who cannot be housed
 * moves the housing score by exactly {@code 1 / housingCapacity}, and since the target is
 * {@code housingCapacity x desirability}, that resident moves the target by a bounded constant
 * independent of city size. With a population denominator the slope would blow up for small
 * populations, and a village of three could oscillate. See {@link PopulationModel} for the full
 * derivation and the constant it relies on.
 *
 * <h2>Why there is no smoothing here</h2>
 *
 * <p>It is tempting to damp happiness toward its raw value the way population is damped. Deliberately
 * not done: adding a second lag inside a negative feedback loop turns a first-order system into a
 * second-order one, and a second-order loop can ring even when both lags are individually stable. The
 * inputs to this model (population, employment) are already damped, so happiness moves smoothly in
 * practice without a second filter that would cost the convergence guarantee.
 */
public final class HappinessModel {

    /**
     * Effective tax burden, in permille, at which the tax score reaches zero. Anything at or above this
     * is read as confiscatory; below it the score falls off linearly.
     */
    private static final int PUNITIVE_TAX_PERMILLE = 250;

    /**
     * Civic floor area (parks, government, entertainment, public services) wanted per unit of housing
     * capacity for a full services score. Expressed against capacity rather than population so a city
     * is judged on whether it planned for the people it can hold.
     */
    private static final double CIVIC_CELLS_PER_HOUSING_UNIT = 1.5D;

    /** Days of expenses the treasury should cover for a full finances score. */
    private static final int RESERVE_TARGET_DAYS = 5;

    private HappinessModel() {
    }

    /**
     * Score a city from the state a simulation step just produced.
     *
     * <p>Reads {@link CitySnapshot#happinessPermille()} not at all - the model is memoryless, so a
     * reloaded world converges to the same figure as one that has been running for a week.
     */
    public static HappinessBreakdown evaluate(CitySnapshot snapshot) {
        final List<HappinessBreakdown.Contributor> contributors = new ArrayList<>(HappinessFactor.values().length);

        contributors.add(score(HappinessFactor.HOUSING, housingScore(snapshot)));
        contributors.add(score(HappinessFactor.EMPLOYMENT, employmentScore(snapshot)));
        contributors.add(score(HappinessFactor.TAXES, taxScore(snapshot)));
        contributors.add(score(HappinessFactor.SERVICES, servicesScore(snapshot)));
        contributors.add(score(HappinessFactor.FINANCES, financeScore(snapshot)));

        double total = 0.0D;
        for (HappinessBreakdown.Contributor contributor : contributors) {
            total += contributor.factor().weight() * contributor.score();
        }
        return new HappinessBreakdown((int) Math.round(clamp01(total) * 1000.0D), contributors);
    }

    private static HappinessBreakdown.Contributor score(HappinessFactor factor, double value) {
        return new HappinessBreakdown.Contributor(factor, clamp01(value));
    }

    /** Full marks while everyone is housed; falls off with the homeless share of housing capacity. */
    private static double housingScore(CitySnapshot snapshot) {
        final int homeless = snapshot.homeless();
        if (homeless <= 0) {
            return 1.0D;
        }
        // max(1, ...) covers the degenerate "people but no housing at all" case, which scores zero
        // rather than dividing by zero.
        return 1.0D - clamp01((double) homeless / Math.max(1, snapshot.housingCapacity()));
    }

    /** Falls off with the share of the city's <em>potential</em> workforce left without a job. */
    private static double employmentScore(CitySnapshot snapshot) {
        final int unemployed = snapshot.unemployed();
        if (unemployed <= 0) {
            return 1.0D;
        }
        final double potentialWorkforce =
                Math.max(1.0D, snapshot.housingCapacity() * CityStats.WORKFORCE_PARTICIPATION);
        return 1.0D - clamp01(unemployed / potentialWorkforce);
    }

    private static double taxScore(CitySnapshot snapshot) {
        return 1.0D - clamp01((double) snapshot.effectiveTaxPermille() / PUNITIVE_TAX_PERMILLE);
    }

    private static double servicesScore(CitySnapshot snapshot) {
        final double wanted = snapshot.housingCapacity() * CIVIC_CELLS_PER_HOUSING_UNIT;
        if (wanted <= 0.0D) {
            // Nothing to serve yet; an empty plot is not an unhappy one.
            return 1.0D;
        }
        return clamp01(snapshot.civicCells() / wanted);
    }

    private static double financeScore(CitySnapshot snapshot) {
        if (snapshot.unpaidExpenses()) {
            // Bills the city could not pay are the unambiguous bottom of this scale.
            return 0.0D;
        }
        final long dailyExpense = snapshot.dailyExpenseCents();
        if (dailyExpense <= 0L) {
            return 1.0D;
        }
        final double reserveDays = (double) snapshot.treasuryCents() / dailyExpense;
        return clamp01(reserveDays / RESERVE_TARGET_DAYS);
    }

    private static double clamp01(double value) {
        if (Double.isNaN(value)) {
            return 0.0D;
        }
        return value < 0.0D ? 0.0D : Math.min(value, 1.0D);
    }
}
