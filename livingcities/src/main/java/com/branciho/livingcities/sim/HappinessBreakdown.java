package com.branciho.livingcities.sim;

import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Why a city's happiness is what it is.
 *
 * <p>v0.1's UI shows one number, but the model already knows the full story, and throwing that away at
 * the model boundary would mean rewriting the model later to get it back. Keeping the contributors
 * means the eventual "Housing -18%, Taxes -9%" panel is a rendering job, not a simulation change.
 *
 * <p>Scores are 0..1 per factor; the total is the weighted sum expressed in permille to match
 * {@link com.branciho.livingcities.city.CityStats}, which stores permille so that saved data and
 * packets stay exact.
 */
public record HappinessBreakdown(int totalPermille, List<Contributor> contributors) {

    public static final HappinessBreakdown NEUTRAL = new HappinessBreakdown(700, List.of());

    public HappinessBreakdown {
        contributors = List.copyOf(contributors);
    }

    /**
     * One factor's verdict.
     *
     * @param score 0..1, where 1 means this factor is entirely satisfied
     */
    public record Contributor(HappinessFactor factor, double score) {

        /** This factor's own standing, ignoring how much the city cares about it. */
        public int scorePermille() {
            return (int) Math.round(score * 1000.0D);
        }

        /** How many permille of the total this factor is currently supplying. */
        public int contributionPermille() {
            return (int) Math.round(factor.weight() * score * 1000.0D);
        }

        /**
         * How many permille this factor is <em>costing</em> relative to a perfect score. This is the
         * number a "why is my city unhappy" panel wants, because it ranks problems rather than praise.
         */
        public int shortfallPermille() {
            return (int) Math.round(factor.weight() * (1.0D - score) * 1000.0D);
        }
    }

    /** Keyed view for callers that want to look one factor up rather than iterate. */
    public Map<HappinessFactor, Contributor> asMap() {
        final Map<HappinessFactor, Contributor> map = new EnumMap<>(HappinessFactor.class);
        for (Contributor contributor : contributors) {
            map.put(contributor.factor(), contributor);
        }
        return Collections.unmodifiableMap(map);
    }

    /** The single worst factor, or null when there is nothing to complain about. */
    public @Nullable Contributor worst() {
        Contributor worst = null;
        for (Contributor contributor : contributors) {
            if (worst == null || contributor.shortfallPermille() > worst.shortfallPermille()) {
                worst = contributor;
            }
        }
        return worst != null && worst.shortfallPermille() > 0 ? worst : null;
    }
}
