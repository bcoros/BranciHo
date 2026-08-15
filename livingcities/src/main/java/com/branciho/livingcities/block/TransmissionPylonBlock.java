package com.branciho.livingcities.block;

import com.branciho.livingcities.utility.UtilityComponent;
import com.branciho.livingcities.utility.UtilityKind;
import com.branciho.livingcities.utility.UtilityRole;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;

/**
 * A transmission pylon: conducts like a cable and additionally links to other pylons within range.
 *
 * <p>This is what makes a power line to a distant plant practical. Linking by proximity rather than
 * by a traced wire is a deliberate abstraction - a real hanging cable would need an entity or a block
 * every metre, and the whole point of a pylon is to span ground the player has not built on.
 */
public class TransmissionPylonBlock extends Block implements UtilityComponent {

    public static final MapCodec<TransmissionPylonBlock> CODEC = simpleCodec(TransmissionPylonBlock::new);

    /** Blocks between pylons. Roughly a chunk, so a line across a valley is a handful of them. */
    public static final int LINK_RANGE = 16;

    public TransmissionPylonBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public UtilityKind utilityKind() {
        return UtilityKind.POWER;
    }

    @Override
    public UtilityRole utilityRole() {
        return UtilityRole.PYLON;
    }

    @Override
    public int linkRange() {
        return LINK_RANGE;
    }
}
