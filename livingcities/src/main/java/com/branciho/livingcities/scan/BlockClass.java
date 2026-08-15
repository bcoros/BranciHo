package com.branciho.livingcities.scan;

/**
 * The intrinsic role a single {@code BlockState} plays in a structure.
 *
 * <p>This enum stays deliberately small, and deliberately says nothing about "floor" or "room".
 * Whether a cell is walkable floor is not a property of a block - it is a property of a block plus
 * what sits above it - so those judgements live in {@link BlockProfile}'s predicates and in
 * {@link FloorAnalyzer}, where a whole vertical neighbourhood is available.
 *
 * <p>Every constant here is reachable from {@code BlockState}/{@code BlockBehaviour} queries alone.
 * No block id, no {@code instanceof}, so a block from any mod lands in the right bucket without the
 * mod knowing this one exists.
 */
public enum BlockClass {

    /** Air, or a block with no collision and nothing to occlude: plants, torches, banners. */
    OPEN,

    /** Full-cube collision and opaque render. The structural workhorse: floors, walls, ceilings. */
    SOLID_SUPPORT,

    /**
     * Full-cube collision but see-through: glass, ice, tinted glass, modded windows.
     *
     * <p>Kept separate from {@link #SOLID_SUPPORT} because it seals and supports identically but
     * means something different for daylight and outlook, which later drives quality modifiers.
     */
    TRANSPARENT_SOLID,

    /** Partial collision whose top face is full at y=1.0: upper slabs, stairs. Standable. */
    TOP_SUPPORT,

    /** Partial collision, top not full: fences, panes, lower slabs, furniture, machinery. */
    PARTIAL,

    /** Collision so thin a player walks straight over it: carpets, plates, rails, buttons, snow. */
    DECOR_PASSABLE,

    /**
     * Door-like: has an {@code OPEN} property or is in the door/trapdoor/fence-gate tags.
     *
     * <p>Treated as a wall by the enclosure fill whether it is open or shut. A house with its front
     * door hanging open is still a house, and a player who leaves a door open should not watch their
     * building's population evaporate.
     */
    DOOR_LIKE,

    /** Non-empty fluid state with no solid collision of its own. Never a floor, never a wall. */
    FLUID,

    /**
     * Technical blocks that must not influence geometry at all: barriers, structure voids, light
     * blocks, plus anything a pack tags {@code livingcities:scan_excluded}. Treated as open space.
     */
    EXCLUDED,

    /**
     * A profiling call threw. Conservative fallback: blocks motion, supports nothing, seals nothing.
     *
     * <p>One badly-behaved modded block must cost one cell's accuracy, not the whole scan.
     */
    UNKNOWN
}
