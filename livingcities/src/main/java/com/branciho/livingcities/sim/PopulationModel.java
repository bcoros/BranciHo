package com.branciho.livingcities.sim;

import com.branciho.livingcities.city.CityStats;
import com.branciho.livingcities.config.LivingCitiesConfig;

/**
 * Moves a city's population toward the size its buildings, jobs, mood and taxes can sustain.
 *
 * <h2>The target</h2>
 *
 * <p>Housing capacity is a hard ceiling - nobody lives in a city with no homes - so the target is
 * capacity scaled by a <em>desirability</em> in 0..1:
 *
 * <pre>
 *   desirability = BASE + W_HAPPY x happiness + W_JOBS x jobCoverage - taxPenalty   (clamped to 0..1)
 *   target       = housingCapacity x desirability
 * </pre>
 *
 * <p>{@code BASE + W_HAPPY + W_JOBS} is exactly 1, so a delightful, fully employed, low-tax city fills
 * its housing completely and nothing else can push it past capacity. {@code BASE} is non-zero because
 * even a miserable town keeps some residents; without it a single bad step would empty a city entirely.
 *
 * <p>Taxes appear here <em>as well as</em> inside happiness, and that is intentional rather than double
 * counting a mistake. Happiness is how the people already here feel; the tax penalty is a migration
 * signal - cost of living is something outsiders weigh before moving, whether or not current residents
 * have got used to it.
 *
 * <h2>The damping, and why it is safe</h2>
 *
 * <p>Each step applies
 *
 * <pre>
 *   x_next = x + r x (target(x) - x)      where r = LivingCitiesConfig.SERVER.growthRate
 * </pre>
 *
 * <p>which is the explicit Euler discretisation of {@code dx/dt = r(T - x)}, exponential relaxation.
 * Nothing here is a random walk or an unbounded percentage; population always heads for a finite target.
 *
 * <p><b>If the target were constant</b>, the step map is {@code f(x) = (1-r)x + rT}, an affine
 * contraction with factor {@code |1-r|}. The config clamps {@code r} to {@code [0.001, 0.5]}, so
 * {@code 1-r} lies in {@code [0.5, 0.999]}: strictly positive and strictly below one, meaning monotone
 * convergence with no overshoot at any permitted setting. Oscillation would need {@code r > 1};
 * divergence would need {@code r >= 2}. Neither is reachable.
 *
 * <p><b>The target is not constant</b> - it depends on happiness, which depends on population, so the
 * real map is {@code f(x) = (1-r)x + r x T(x)} and stability needs {@code |f'(x)| < 1}, i.e.
 * {@code |(1-r) + r x T'(x)| < 1}. Bound {@code T'}:
 *
 * <ol>
 *   <li>{@code T = housingCapacity x desirability}, and population enters desirability only through the
 *       happiness term, weighted {@link #HAPPINESS_WEIGHT} (0.50).</li>
 *   <li>Within happiness, only HOUSING (0.25), EMPLOYMENT (0.20) and FINANCES (0.15) respond to
 *       population at all - 0.60 of the total weight. Taxes and services do not.</li>
 *   <li>{@link HappinessModel} deliberately measures those factors against <em>capacity</em>, so each
 *       one's score changes by at most {@code 1/housingCapacity} per extra resident. (FINANCES is
 *       bounded conservatively at that same unit slope; its true slope is far smaller, which only makes
 *       the result safer.)</li>
 * </ol>
 *
 * <p>Therefore {@code |T'(x)| <= 0.50 x 0.60 x housingCapacity x (1/housingCapacity) = 0.30}, and
 * critically that bound is independent of city size. Every one of those terms is negative - more people
 * can only make the city more crowded, not more attractive - so {@code T'(x)} lies in {@code [-0.30, 0]}
 * and
 *
 * <pre>
 *   f'(x) is in [1 - 1.30r, 1 - r]
 * </pre>
 *
 * <p>Convergence needs {@code f' > -1}, i.e. {@code r < 2/1.30 = 1.538}. Strictly monotone approach
 * (no overshoot, hence no oscillation at all) needs {@code f' >= 0}, i.e. {@code r <= 1/1.30 = 0.769}.
 * The config's hard ceiling is <b>0.5</b>, comfortably inside the monotone region with about 35% margin,
 * and its default of 0.08 gives {@code f' = 0.896} - a smooth ~10% approach per step. The config's own
 * warning about 0.3 is more conservative than this derivation requires, which is the right direction to
 * err.
 *
 * <p>The 0.30 figure is load-bearing. If a future happiness factor is added that responds to population,
 * the weight sum in step 2 grows and this margin shrinks; the safe ceiling is {@code 1/(1 + 0.5 x W)}
 * where {@code W} is the total weight of population-sensitive factors.
 */
public final class PopulationModel {

    /** Share of housing that stays occupied regardless of how bad things get. */
    private static final double BASE_DESIRABILITY = 0.20D;

    /** How much of the remaining desirability civic mood accounts for. */
    private static final double HAPPINESS_WEIGHT = 0.50D;

    /** How much of the remaining desirability the availability of work accounts for. */
    private static final double JOBS_WEIGHT = 0.30D;

    /** Tax burden below which nobody is deterred from moving in. */
    private static final int NEUTRAL_TAX_PERMILLE = 100;

    /** Desirability lost at a 100% effective tax burden. */
    private static final double TAX_SENSITIVITY = 0.60D;

    private PopulationModel() {
    }

    /**
     * Advance {@code stats}' population one simulation step.
     *
     * <p>Reads the happiness that was just written for this step, so call it after
     * {@link HappinessModel}.
     */
    public static void step(CityStats stats, int housingCapacity, int jobCapacity, int effectiveTaxPermille) {
        final double target = target(stats.happinessPermille(), housingCapacity, jobCapacity, effectiveTaxPermille);
        final double rate = LivingCitiesConfig.SERVER.growthRate.get();

        // The fractional part is carried between steps rather than truncated. Without it, a city whose
        // target is eight people above its population would compute +0.64 people per step, floor that
        // to zero, and never grow at all - the classic "my village is stuck at 3" bug.
        final double current = stats.population() + stats.populationRemainder();
        double next = current + rate * (target - current);
        if (next < 0.0D || Double.isNaN(next)) {
            next = 0.0D;
        }
        next = Math.min(next, Integer.MAX_VALUE);

        final int whole = (int) Math.floor(next);
        stats.setPopulation(whole);
        stats.setPopulationRemainder(next - whole);
    }

    /**
     * The population this city can sustain right now. Exposed so a UI or a {@code /city info} command
     * can show "growing toward 1,240" without duplicating the formula.
     */
    public static double target(int happinessPermille, int housingCapacity, int jobCapacity, int effectiveTaxPermille) {
        if (housingCapacity <= 0) {
            return 0.0D;
        }

        final double happiness = clamp01(happinessPermille / 1000.0D);

        // Job coverage is measured against the workforce the city would have at full occupancy, so it
        // answers "is there work for the people this city can hold" rather than "for the people in it
        // today". Using today's population would make an empty city look fully employed and invite a
        // growth spurt into jobs that do not exist.
        final double potentialWorkforce = housingCapacity * CityStats.WORKFORCE_PARTICIPATION;
        final double jobCoverage = potentialWorkforce <= 0.0D ? 0.0D : clamp01(jobCapacity / potentialWorkforce);

        final double taxPenalty = TAX_SENSITIVITY
                * Math.max(0.0D, (effectiveTaxPermille - NEUTRAL_TAX_PERMILLE) / 1000.0D);

        final double desirability = clamp01(
                BASE_DESIRABILITY + HAPPINESS_WEIGHT * happiness + JOBS_WEIGHT * jobCoverage - taxPenalty);

        return housingCapacity * desirability;
    }

    private static double clamp01(double value) {
        if (Double.isNaN(value)) {
            return 0.0D;
        }
        return value < 0.0D ? 0.0D : Math.min(value, 1.0D);
    }
}
