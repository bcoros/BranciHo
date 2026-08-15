package com.branciho.livingcities.building;

/** Descriptive markers the scanner attaches to a floor, used for capacity modifiers and UI labels. */
public enum FloorFlag {

    /** Open to the sky: a roof deck or terrace. Kept in the floor list but scores no capacity. */
    ROOF,

    /** Below the surrounding grade level. Poor for housing, fine for storage and industry. */
    BASEMENT,

    /** One logical floor spread across two heights, e.g. a house built into a hillside. */
    SPLIT_LEVEL,

    /** Has a sizeable opening down to the floor below. Cosmetic; drives the "atrium" label. */
    HAS_ATRIUM,

    /** Created or edited by the player rather than detected, so a rescan must not silently discard it. */
    MANUAL
}
