package com.branciho.livingcities.npc;

/**
 * The aggregate day/night curve: what a city's street life looks like at a given time of day.
 *
 * <p>Two things come out of it. {@link #densityMultiplier()} says how much of the street's peak crowd
 * is actually out there right now, and the four role weights say who they are. Together they are the
 * difference between "eight people at midday" and "one person at four in the morning" on the exact
 * same block.
 *
 * <h2>Why a keyframe table and not a formula</h2>
 *
 * <p>A sine wave over the day gives one hump. Real street activity has three - a morning commute, a
 * midday peak and a heavier evening commute - separated by troughs, and the role mix inverts between
 * them. A table of hourly anchors with linear interpolation between them reproduces that in a form
 * that can be read, argued with and tuned one row at a time, and it is trivially convertible to a
 * datapack later. A closed-form curve that did the same thing would be unreadable.
 *
 * <h2>Time base</h2>
 *
 * <p>Keyed on Minecraft day time, where 0 is 06:00 and every 1,000 ticks is one wall-clock hour, so
 * noon is 6,000 and midnight is 18,000. Values outside 0-23,999 are wrapped, so {@code getDayTime()}
 * can be passed in raw.
 *
 * <p>This class deliberately imports nothing from Minecraft. It is pure arithmetic over a table,
 * which keeps it unit-testable without a world and lets the same instance later drive routing
 * decisions that have no business touching a {@code Level}.
 *
 * @param densityMultiplier fraction of the street's peak crowd that is out at this hour, 0-1
 * @param commuterWeight    unnormalised share of {@link CitizenRole#COMMUTER}
 * @param workerWeight      unnormalised share of {@link CitizenRole#WORKER}
 * @param shopperWeight     unnormalised share of {@link CitizenRole#SHOPPER}
 * @param idlerWeight       unnormalised share of {@link CitizenRole#IDLER}
 */
public record CitizenActivity(double densityMultiplier,
                              double commuterWeight,
                              double workerWeight,
                              double shopperWeight,
                              double idlerWeight) {

    /** Length of a Minecraft day in ticks. */
    public static final int TICKS_PER_DAY = 24_000;

    /** Ticks per wall-clock hour, which is what the keyframe times below are multiples of. */
    private static final int TICKS_PER_HOUR = 1_000;

    /** Day time of 00:00. Minecraft day time 0 is dawn, so midnight sits three quarters through. */
    private static final int MIDNIGHT = 18 * TICKS_PER_HOUR;

    /**
     * Keyframe times in day ticks, strictly ascending, first entry 0. The segment after the last
     * entry wraps around to the first.
     *
     * <p>Kept parallel to {@link #KEY_STATES} rather than folded into a record array so that the
     * table below reads as a timetable: one row per anchor hour, in order.
     */
    private static final int[] KEY_TIMES = {
            0,                          // 06:00 dawn
            1 * TICKS_PER_HOUR,         // 07:00
            2 * TICKS_PER_HOUR,         // 08:00  morning commute peak
            4 * TICKS_PER_HOUR,         // 10:00
            6 * TICKS_PER_HOUR,         // 12:00  midday peak
            8 * TICKS_PER_HOUR,         // 14:00
            10 * TICKS_PER_HOUR,        // 16:00
            11 * TICKS_PER_HOUR + 500,  // 17:30  evening commute peak
            13 * TICKS_PER_HOUR,        // 19:00  dusk
            15 * TICKS_PER_HOUR,        // 21:00
            MIDNIGHT,                   // 00:00
            22 * TICKS_PER_HOUR,        // 04:00  trough
    };

    /**
     * The curve itself: density, then commuter/worker/shopper/idler weights.
     *
     * <p>Density is expressed as a fraction of the street's peak, so 1.0 occurs exactly once (midday)
     * and every other hour is a discount on it. The two commute peaks are close behind at 0.95 but
     * are almost entirely commuters, which is what makes them feel different rather than just busier.
     */
    private static final CitizenActivity[] KEY_STATES = {
            new CitizenActivity(0.40D, 0.50D, 0.25D, 0.05D, 0.20D), // 06:00 first movers
            new CitizenActivity(0.80D, 0.70D, 0.18D, 0.04D, 0.08D), // 07:00
            new CitizenActivity(0.95D, 0.72D, 0.18D, 0.04D, 0.06D), // 08:00 rush
            new CitizenActivity(0.65D, 0.18D, 0.55D, 0.15D, 0.12D), // 10:00 settled at work
            new CitizenActivity(1.00D, 0.12D, 0.38D, 0.36D, 0.14D), // 12:00 lunch crowd
            new CitizenActivity(0.72D, 0.12D, 0.55D, 0.20D, 0.13D), // 14:00
            new CitizenActivity(0.80D, 0.25D, 0.42D, 0.20D, 0.13D), // 16:00 early leavers
            new CitizenActivity(0.95D, 0.62D, 0.12D, 0.16D, 0.10D), // 17:30 rush
            new CitizenActivity(0.70D, 0.30D, 0.10D, 0.38D, 0.22D), // 19:00 evening out
            new CitizenActivity(0.38D, 0.18D, 0.10D, 0.32D, 0.40D), // 21:00 thinning
            new CitizenActivity(0.12D, 0.08D, 0.12D, 0.10D, 0.70D), // 00:00
            new CitizenActivity(0.07D, 0.10D, 0.20D, 0.04D, 0.66D), // 04:00 quietest hour
    };

    /**
     * The activity curve at a given day time.
     *
     * @param dayTime {@code Level#getDayTime()}; wrapped, so the raw monotonic value is fine
     */
    public static CitizenActivity at(long dayTime) {
        final int time = (int) Math.floorMod(dayTime, (long) TICKS_PER_DAY);

        // Default to the last segment, which is the one that wraps past midnight back to dawn.
        int index = KEY_TIMES.length - 1;
        for (int i = 0; i < KEY_TIMES.length - 1; i++) {
            if (time < KEY_TIMES[i + 1]) {
                index = i;
                break;
            }
        }

        final int start = KEY_TIMES[index];
        final int end = index + 1 < KEY_TIMES.length ? KEY_TIMES[index + 1] : KEY_TIMES[0] + TICKS_PER_DAY;
        final double progress = (double) (time - start) / (double) (end - start);
        return KEY_STATES[index].blend(KEY_STATES[(index + 1) % KEY_STATES.length], progress);
    }

    /** The share of the crowd in this role, normalised to sum to 1 across all roles. */
    public double weight(CitizenRole role) {
        final double total = commuterWeight + workerWeight + shopperWeight + idlerWeight;
        if (total <= 0.0D) {
            // A table row that zeroed every weight would otherwise divide by zero; an empty street is
            // an idler's street.
            return role == CitizenRole.IDLER ? 1.0D : 0.0D;
        }
        return rawWeight(role) / total;
    }

    /**
     * Pick a role from the mix.
     *
     * @param roll a uniform value in [0, 1); taking the roll rather than a {@code RandomSource} keeps
     *             this class free of Minecraft and makes the distribution testable with fixed inputs
     */
    public CitizenRole roleAt(double roll) {
        double remaining = Math.clamp(roll, 0.0D, 1.0D);
        for (CitizenRole role : CitizenRole.values()) {
            remaining -= weight(role);
            if (remaining <= 0.0D) {
                return role;
            }
        }
        // Only reachable through floating point slack at exactly 1.0.
        return CitizenRole.IDLER;
    }

    private double rawWeight(CitizenRole role) {
        return switch (role) {
            case COMMUTER -> commuterWeight;
            case WORKER -> workerWeight;
            case SHOPPER -> shopperWeight;
            case IDLER -> idlerWeight;
        };
    }

    private CitizenActivity blend(CitizenActivity other, double progress) {
        return new CitizenActivity(
                mix(densityMultiplier, other.densityMultiplier, progress),
                mix(commuterWeight, other.commuterWeight, progress),
                mix(workerWeight, other.workerWeight, progress),
                mix(shopperWeight, other.shopperWeight, progress),
                mix(idlerWeight, other.idlerWeight, progress));
    }

    private static double mix(double from, double to, double progress) {
        return from + (to - from) * progress;
    }
}
