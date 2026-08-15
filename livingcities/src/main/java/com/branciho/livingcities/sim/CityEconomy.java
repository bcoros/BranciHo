package com.branciho.livingcities.sim;

import com.branciho.livingcities.building.Building;
import com.branciho.livingcities.building.Floor;
import com.branciho.livingcities.building.ZoneUse;
import com.branciho.livingcities.building.ZoneUse.TaxCategory;
import com.branciho.livingcities.city.City;
import com.branciho.livingcities.config.LivingCitiesConfig;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Turns a city's occupied floor area into a daily budget.
 *
 * <p>Two deliberate choices shape this model.
 *
 * <p><b>Tax is levied on an assessed base, not invented per building.</b> Each floor contributes an
 * activity base (what the people inside it earn or turn over) plus a property base (what the space
 * itself is worth), and the configured percentage for that floor's {@link TaxCategory} is applied to
 * the sum. That is why a mixed-use tower is taxed correctly without any special handling: the split
 * lives on the floors, which is where the player assigned it.
 *
 * <p><b>Empty space is a liability.</b> The activity base scales with actual occupancy and the property
 * base only partly so, while upkeep is charged on every usable cell whether or not anyone is in it.
 * A speculative empty skyscraper therefore costs the treasury money, which is the pressure that makes
 * zoning decisions matter instead of "build the biggest box you can".
 *
 * <p>All figures are daily rates in cents; {@link CitySimulation} decides how much of a day a step is.
 */
public final class CityEconomy {

    // --- assessed value of activity, per day, in cents -----------------------------------------
    // These are the "what does a person or a job generate" dials. They are internal constants rather
    // than config values because the server-facing dial is the tax percentage: an admin tunes how much
    // of the economy the city takes, not how big the economy is.

    /** Assessed household income per housed resident per day. */
    private static final long ASSESSED_CENTS_PER_RESIDENT = 20_00L;
    private static final long ASSESSED_CENTS_PER_COMMERCIAL_JOB = 30_00L;
    private static final long ASSESSED_CENTS_PER_OFFICE_JOB = 40_00L;
    private static final long ASSESSED_CENTS_PER_INDUSTRIAL_JOB = 35_00L;

    /** Assessed property value of one usable floor cell per day, at full occupancy. */
    private static final double ASSESSED_CENTS_PER_CELL = 5.0D;

    /**
     * Share of property value still assessed when a floor is completely empty. Property tax is a tax
     * on the building, not on its tenants, so a vacant unit is not free - but a landlord with no
     * tenants is not assessed at full value either.
     */
    private static final double VACANT_PROPERTY_FLOOR = 0.25D;

    // --- upkeep, per day, in cents --------------------------------------------------------------

    /** Roads, lighting and administration for a claimed chunk, occupied or not. */
    private static final long UPKEEP_CENTS_PER_CHUNK = 5_00L;

    /** Fixed administrative cost of having a building on the books at all. */
    private static final long UPKEEP_CENTS_PER_BUILDING = 8_00L;

    /** Maintenance charged on every usable cell, which is what makes vacancy expensive. */
    private static final long UPKEEP_CENTS_PER_USABLE_CELL = 1L;

    /** Per-capita services: schooling, sanitation, everything that scales with people not with area. */
    private static final long SERVICES_CENTS_PER_CITIZEN = 40L;

    /**
     * Floor uses that count as civic provision for the happiness model. Chosen by what the space does
     * for residents, not by whether it is taxable - a park earns nothing and is still why people stay.
     */
    private static final Set<ZoneUse> CIVIC_USES = EnumSet.of(
            ZoneUse.GOVERNMENT, ZoneUse.PUBLIC_SERVICE, ZoneUse.PARK, ZoneUse.ENTERTAINMENT);

    private CityEconomy() {
    }

    /**
     * Assess the city's daily books.
     *
     * <p>Must be called <em>after</em> occupancy has been allocated for this step, because the tax base
     * reads {@link Building#residents()} and {@link Building#workers()}.
     *
     * @param housingCap per-building housing capacity, parallel to {@code buildings}
     * @param jobCap     per-building job capacity, parallel to {@code buildings}
     * @param population the population the step is simulating, for per-capita service cost
     */
    public static CityBudget assess(City city, List<Building> buildings, int[] housingCap, int[] jobCap, int population) {
        final int count = buildings.size();
        if (count == 0) {
            // No buildings still means territory to maintain and citizens to serve.
            return new CityBudget(0L, baseExpenses(city, 0, 0L, population), 0, 0);
        }

        final long[] baseByCategory = new long[TaxCategory.values().length];
        long usableCells = 0L;
        long civicCells = 0L;

        for (int i = 0; i < count; i++) {
            final Building building = buildings.get(i);

            // Occupancy is a building-level figure; individual floors do not track their own tenants,
            // so each floor is assumed to be filled to the same degree as the building it is part of.
            final double housingFill = housingCap[i] <= 0 ? 0.0D : (double) building.residents() / housingCap[i];
            final double jobFill = jobCap[i] <= 0 ? 0.0D : (double) building.workers() / jobCap[i];

            for (Floor floor : building.floors()) {
                final int cells = floor.usableCells();
                usableCells += cells;

                final ZoneUse use = floor.use();
                if (CIVIC_USES.contains(use)) {
                    civicCells += cells;
                }

                final TaxCategory category = use.taxCategory();
                if (category == TaxCategory.NONE) {
                    continue;
                }

                final boolean housing = use.providesHousing();
                final double fill = housing ? housingFill : jobFill;

                final long activity = housing
                        ? Math.round(floor.residents() * housingFill) * ASSESSED_CENTS_PER_RESIDENT
                        : Math.round(floor.jobs() * jobFill) * assessedPerJob(category);

                final long property = Math.round(cells * ASSESSED_CENTS_PER_CELL
                        * (VACANT_PROPERTY_FLOOR + (1.0D - VACANT_PROPERTY_FLOOR) * fill));

                baseByCategory[category.ordinal()] += activity + property;
            }
        }

        long income = 0L;
        long assessedBase = 0L;
        for (TaxCategory category : TaxCategory.values()) {
            final long base = baseByCategory[category.ordinal()];
            if (base <= 0L) {
                continue;
            }
            assessedBase += base;
            income += base * taxPercent(category) / 100L;
        }

        // The burden residents actually feel is revenue over base, not any one configured percentage:
        // an industrial town levying 7% on everything that matters is a low-tax town even if its
        // residential slider reads 30% and applies to almost nothing.
        final int effectiveTaxPermille = assessedBase <= 0L
                ? 0
                : (int) Math.min(1000L, income * 1000L / assessedBase);

        final long expenses = baseExpenses(city, count, usableCells, population);
        return new CityBudget(income, expenses, effectiveTaxPermille, (int) Math.min(civicCells, Integer.MAX_VALUE));
    }

    private static long baseExpenses(City city, int buildingCount, long usableCells, int population) {
        long expenses = (long) city.claimCount() * UPKEEP_CENTS_PER_CHUNK;
        expenses += (long) buildingCount * UPKEEP_CENTS_PER_BUILDING;
        expenses += usableCells * UPKEEP_CENTS_PER_USABLE_CELL;
        expenses += (long) population * SERVICES_CENTS_PER_CITIZEN;
        return expenses;
    }

    private static long assessedPerJob(TaxCategory category) {
        return switch (category) {
            case COMMERCIAL -> ASSESSED_CENTS_PER_COMMERCIAL_JOB;
            case OFFICE -> ASSESSED_CENTS_PER_OFFICE_JOB;
            case INDUSTRIAL -> ASSESSED_CENTS_PER_INDUSTRIAL_JOB;
            // A residential floor's jobs (there are none) and untaxed floors contribute nothing here.
            case RESIDENTIAL, NONE -> 0L;
        };
    }

    private static int taxPercent(TaxCategory category) {
        return switch (category) {
            case RESIDENTIAL -> LivingCitiesConfig.SERVER.residentialTaxPercent.get();
            case COMMERCIAL -> LivingCitiesConfig.SERVER.commercialTaxPercent.get();
            case OFFICE -> LivingCitiesConfig.SERVER.officeTaxPercent.get();
            case INDUSTRIAL -> LivingCitiesConfig.SERVER.industrialTaxPercent.get();
            case NONE -> 0;
        };
    }
}
