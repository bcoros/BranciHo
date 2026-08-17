package com.branciho.citiesinlife.entity.ai;

import net.minecraft.world.level.Level;

/**
 * When people are at work.
 *
 * <p>Two shifts, because the player asked for shops that can open late and there is no sensible way
 * to do that with one. An office is day only — nobody is at a desk at three in the morning — while a
 * till can be staffed by somebody on either, which is what lets a shop with two workers stay open
 * around the clock.
 *
 * <p>The hours deliberately do not fill the whole day. A citizen that went straight from its bed to
 * its desk and back would never once be seen on the street, and the street is the point.
 */
public final class Shifts {

    private static final long DAY_START = 1_000L;
    private static final long DAY_END = 11_500L;

    private static final long NIGHT_START = 13_500L;
    private static final long NIGHT_END = 23_000L;

    private Shifts() {
    }

    public static long timeOfDay(Level level) {
        return level.getDayTime() % 24_000L;
    }

    public static boolean onShift(Level level, boolean nightShift) {
        long time = timeOfDay(level);
        return nightShift
                ? time >= NIGHT_START && time < NIGHT_END
                : time >= DAY_START && time < DAY_END;
    }

    /** Bedtime for anybody who is not working it. */
    public static boolean sleepingHours(Level level) {
        long time = timeOfDay(level);
        return time >= NIGHT_START || time < DAY_START;
    }
}
