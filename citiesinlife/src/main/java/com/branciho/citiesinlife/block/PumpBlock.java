package com.branciho.citiesinlife.block;

import com.branciho.citiesinlife.water.WaterRole;

/**
 * A relay pump: the middle of a run.
 *
 * <p>It does nothing except carry the station further than one link would reach, which is exactly
 * why it exists — a river four hundred blocks from the city should be a project, not a single click.
 */
public class PumpBlock extends AbstractPumpBlock {

    /** How far one relay can throw a link to another. */
    public static final int RELAY_RANGE = 64;

    public PumpBlock(Properties properties) {
        super(properties);
    }

    @Override
    public WaterRole waterRole() {
        return WaterRole.RELAY;
    }

    @Override
    public int linkRange() {
        return RELAY_RANGE;
    }
}
