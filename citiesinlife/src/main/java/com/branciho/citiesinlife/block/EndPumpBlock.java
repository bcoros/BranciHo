package com.branciho.citiesinlife.block;

import com.branciho.citiesinlife.water.WaterRole;

/**
 * The end pump: the one place the invisible pumping run becomes real pipe.
 *
 * <p>Everything upstream of it is links drawn with a tool, because nobody wants to lay pipe across a
 * riverbank. Everything downstream is blocks you place, because routing water through a city is the
 * part worth doing by hand. This block is the seam, and it is the only block allowed to be one.
 */
public class EndPumpBlock extends AbstractPumpBlock {

    public EndPumpBlock(Properties properties) {
        super(properties);
    }

    @Override
    public WaterRole waterRole() {
        return WaterRole.OUTLET;
    }

    @Override
    public int linkRange() {
        return 24;
    }
}
