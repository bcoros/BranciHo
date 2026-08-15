package com.branciho.livingcities.power;

/**
 * What a block does in an electrical grid.
 *
 * <p>Kept as a role on the block rather than a list of block ids so the grid walker never needs to
 * know which blocks exist. Adding a new generator later means implementing {@link PowerComponent},
 * not editing the network code.
 */
public enum PowerRole {

    /** Carries power between adjacent blocks only. Cheap, short range. */
    CONDUCTOR,

    /**
     * Carries power like a conductor and additionally links to other poles at a distance, which is
     * what makes long transmission runs possible without placing a block every metre.
     */
    PYLON,

    /** Feeds power into the grid. */
    GENERATOR,

    /** Distributes power to every registered building within its radius. */
    SUBSTATION
}
