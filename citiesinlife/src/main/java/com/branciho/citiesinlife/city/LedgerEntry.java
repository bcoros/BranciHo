package com.branciho.citiesinlife.city;

import net.minecraft.network.chat.Component;

/**
 * One line in a city's own history.
 *
 * <p>A city has, until now, had no memory of itself: you could found it, fight a war, lose a
 * district to a warhead and rebuild, and nothing anywhere would say so. The ledger is the city
 * remembering. It is written by the same code that does the thing — declaring a war writes the war
 * line — so it cannot drift away from what actually happened.
 *
 * <p>The text is stored as a translation key plus one free-text detail rather than as a finished
 * sentence, so a ledger written on an English client still reads correctly on a German one. The
 * detail is deliberately plain text and not a UUID: a line about a city that has since been razed
 * has to stay readable after that city no longer exists to be looked up.
 *
 * @param at     the game time the thing happened, on the same clock as war starts and training
 * @param key    the suffix of {@code ledger.citiesinlife.*}
 * @param detail the one thing worth naming — a city, a building, a number — or empty
 */
public record LedgerEntry(long at, String key, String detail) {

    /** Long enough for a city name and a building name together, short enough to stay on one row. */
    public static final int MAX_DETAIL = 48;

    /** Guards against a key long enough to bloat the save file or overflow the packet. */
    public static final int MAX_KEY = 32;

    public LedgerEntry {
        key = clip(key, MAX_KEY);
        detail = clip(detail, MAX_DETAIL);
    }

    /**
     * The finished line, assembled on whichever side is about to show it.
     *
     * <p>A missing key renders as the raw translation string rather than throwing, which is the
     * behaviour every other screen in the mod already has and is easier to spot than a blank row.
     */
    public Component describe() {
        return Component.translatable("ledger.citiesinlife." + key, detail);
    }

    private static String clip(String value, int limit) {
        if (value == null) {
            return "";
        }
        return value.length() <= limit ? value : value.substring(0, limit);
    }
}
