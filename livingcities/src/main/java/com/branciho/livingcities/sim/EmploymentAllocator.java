package com.branciho.livingcities.sim;

import com.branciho.livingcities.building.Building;

import java.util.List;
import java.util.function.ObjIntConsumer;

/**
 * Spreads a city-wide headcount across its buildings in proportion to their capacity.
 *
 * <p>Citizens are not objects, so there is nothing to loop over per person. The work here is
 * O(buildings) and independent of population: a city of fifty thousand costs exactly as much to
 * allocate as a city of fifty, which is the whole point of the virtual-population design.
 *
 * <p>The distribution uses an error-accumulating (Bresenham) division rather than
 * {@code floor(capacity * total / capacityTotal)} per building. Plain flooring loses up to one person
 * per building, so a city with 300 half-empty buildings would quietly fail to seat 150 workers every
 * single step and the aggregate would never agree with the sum of its parts. The accumulator is exact:
 * because the untruncated shares sum to {@code total}, the accumulated remainders are an exact multiple
 * of {@code capacityTotal}, so the carries recover precisely the truncated amount and nothing is lost.
 */
public final class EmploymentAllocator {

    private EmploymentAllocator() {
    }

    /**
     * Assign {@code total} occupants across {@code buildings} proportionally to {@code capacity},
     * then return how many were actually seated.
     *
     * <p>Handles both directions of imbalance without a special case: when demand exceeds capacity the
     * assignment saturates at {@code capacityTotal} (excess people are homeless or unemployed and show
     * up as such in {@link CitySnapshot}), and when capacity exceeds demand every building simply ends
     * up partly filled.
     *
     * @param capacity per-building capacity, parallel to {@code buildings}; the caller already computed
     *                 these to aggregate the city totals, so they are passed in rather than recomputed
     *                 (each {@code housingCapacity()} call walks every floor of the building)
     * @param setter   the mutator to apply, e.g. {@code Building::setWorkers}
     */
    public static int distribute(List<Building> buildings, int[] capacity, int total, ObjIntConsumer<Building> setter) {
        final int count = buildings.size();

        long capacityTotal = 0L;
        for (int i = 0; i < count; i++) {
            capacityTotal += Math.max(0, capacity[i]);
        }

        if (capacityTotal <= 0L || total <= 0) {
            // Nowhere to put anyone, or nobody to place: clear every building so stale occupancy from
            // a previous step cannot survive a demolition or a population collapse.
            for (int i = 0; i < count; i++) {
                setter.accept(buildings.get(i), 0);
            }
            return 0;
        }

        // Never seat more people than there is room for; the surplus is reported, not hidden.
        final long assign = Math.min(total, capacityTotal);

        long carry = 0L;
        long assigned = 0L;
        for (int i = 0; i < count; i++) {
            final long room = Math.max(0, capacity[i]);
            // room <= Integer.MAX_VALUE and assign <= Integer.MAX_VALUE, so this product is at most
            // 2^62 and cannot overflow regardless of how many buildings the city has.
            final long share = room * assign;
            long whole = share / capacityTotal;
            carry += share % capacityTotal;
            if (carry >= capacityTotal) {
                carry -= capacityTotal;
                whole++;
            }
            setter.accept(buildings.get(i), (int) whole);
            assigned += whole;
        }
        return (int) assigned;
    }
}
