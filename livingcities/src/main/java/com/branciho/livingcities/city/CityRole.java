package com.branciho.livingcities.city;

/**
 * Membership roles within a city, ordered from most to least privileged.
 *
 * <p>Permissions are derived from rank rather than stored per player, which keeps the save data small
 * and makes "can this player do X" a single comparison on the server.
 */
public enum CityRole {

    /** Founder/owner. Can do everything including disbanding the city. */
    MAYOR(100),
    /** Trusted co-owner. Everything except disbanding and transferring ownership. */
    ADMINISTRATOR(80),
    /** Manages utilities and industry. */
    ENGINEER(60),
    /** May register and edit buildings. */
    BUILDER(40),
    /** Belongs to the city but cannot change it. */
    CITIZEN(10);

    private final int rank;

    CityRole(int rank) {
        this.rank = rank;
    }

    public int rank() {
        return rank;
    }

    public boolean atLeast(CityRole required) {
        return this.rank >= required.rank;
    }

    public static CityRole byName(String name, CityRole fallback) {
        for (CityRole role : values()) {
            if (role.name().equalsIgnoreCase(name)) {
                return role;
            }
        }
        return fallback;
    }
}
