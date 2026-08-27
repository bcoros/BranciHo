package com.branciho.citiesinlife.nuclear;

import net.minecraft.network.chat.Component;

/**
 * Which of the four ports on the cooling loop a block is.
 *
 * <p>Four separate blocks in the creative menu, one class behind them. They differ only in where
 * they belong in the circuit and what the reactor expects to find on the other side of them, and
 * four copies of the same file differing by a noun is how those four quietly drift apart.
 *
 * <p>The loop, in the order it is built:
 *
 * <pre>
 *   pipes (fresh water) -&gt; INPUT_WATER -[pipe tool link]-&gt; OUTPUT_COOLED
 *        -&gt; pipes -&gt; INPUT_COOLED (beside the uranium store) -&gt; core
 *        -&gt; OUTPUT_HEATED (beside the uranium store) -&gt; pipes -&gt; INPUT_WATER
 * </pre>
 */
public enum CoolingPort {

    /** Where water enters, both fresh from the mains and hot round the loop. */
    INPUT_WATER("input_water", 0x4F86C6),

    /** Chilled water leaves here. Fed by the tool link from the water input. */
    OUTPUT_COOLED("output_cooled", 0x6FD3E8),

    /** Cold water arrives at the core. Must stand beside the uranium store. */
    INPUT_COOLED("input_cooled", 0x2E6FA8),

    /** Heat leaves the core here. Must also stand beside the uranium store. */
    OUTPUT_HEATED("output_heated", 0xD9603A);

    private final String id;
    private final int colour;

    CoolingPort(String id, int colour) {
        this.id = id;
        this.colour = colour;
    }

    public String id() {
        return id;
    }

    /** The stripe on the block, so which port is which is readable across a reactor hall. */
    public int colour() {
        return colour;
    }

    /** Whether this port has to be built touching the uranium store. */
    public boolean beside() {
        return this == INPUT_COOLED || this == OUTPUT_HEATED;
    }

    public Component displayName() {
        return Component.translatable("block.citiesinlife." + id + "_port");
    }
}
