package com.branciho.citiesinlife.power;

/**
 * A power block that is several blocks tall but one node on the network.
 *
 * <p>Exists so the wire renderer can lift a line to the top of a mast without knowing which mast it
 * is. Before there were two of them the renderer simply named the wooden one; a second tall mast
 * turned that into a bug where every grid pylon's cables ran along the ground.
 */
public interface MastBlock {

    /** How many block positions this mast occupies, counting its foot. */
    int mastHeight();
}
