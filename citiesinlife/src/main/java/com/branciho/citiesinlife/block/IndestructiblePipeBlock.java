package com.branciho.citiesinlife.block;

/**
 * A pipe that never splits.
 *
 * <p>The ordinary pipe is the one you build a city out of, and it will let you down eventually —
 * that is the point of it, and of the wrench. This is the one you put where being let down would be
 * unacceptable: under a road you have paved over, through a wall, anywhere you would rather not have
 * to go looking with a wrench in the rain.
 */
public class IndestructiblePipeBlock extends WaterPipeBlock {

    public IndestructiblePipeBlock(Properties properties) {
        super(properties);
    }

    @Override
    public boolean canLeak() {
        return false;
    }
}
