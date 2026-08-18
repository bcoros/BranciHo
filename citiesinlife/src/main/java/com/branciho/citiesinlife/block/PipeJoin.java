package com.branciho.citiesinlife.block;

import net.minecraft.util.StringRepresentable;

/**
 * What a pipe does with the block on one of its sides.
 *
 * <p>Three states rather than two booleans on purpose. A pipe either carries water through that
 * face, or it braces itself against whatever is there, or the side is open — and those are mutually
 * exclusive, so spending one property on each of them would multiply the block's state count by four
 * per side instead of by three, for a combination that can never happen.
 *
 * <p>The bracing is the reason this exists at all. A pipe is six pixels through and sits in the
 * middle of its block, so a run laid along the ground floats five pixels above it and a run bolted
 * to a ceiling hangs off nothing. A leg closes that gap on whichever side the wall, floor or ceiling
 * happens to be.
 */
public enum PipeJoin implements StringRepresentable {

    /** Nothing there worth touching: open air, or a block with no solid face to bolt onto. */
    NONE("none"),

    /** Another piece of plumbing. Water passes and the pipe grows an arm to meet it. */
    PIPE("pipe"),

    /** Something solid that is not plumbing. The pipe puts a leg down onto it. */
    LEG("leg");

    private final String id;

    PipeJoin(String id) {
        this.id = id;
    }

    public boolean carriesWater() {
        return this == PIPE;
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
