package com.branciho.citiesinlife.blockentity;

import com.branciho.citiesinlife.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The turning of the globe, and nothing else.
 *
 * <p>Client-side only, and holds no state worth saving: a projection that came back at a different
 * angle after a reload is not a bug anybody could notice.
 */
public class HologramMapBlockEntity extends BlockEntity {

    /** Degrees a tick. Slow — this is a display, not a machine. */
    private static final float SPIN_SPEED = 0.6F;

    /** How far the globe bobs, and how fast. */
    private static final float FLOAT_SPEED = 0.045F;

    private float spin;
    private float previousSpin;
    private float bob;

    public HologramMapBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.HOLOGRAM_MAP.get(), pos, state);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state,
                                  HologramMapBlockEntity table) {
        table.previousSpin = table.spin;
        table.spin += SPIN_SPEED;
        if (table.spin >= 360.0F) {
            table.spin -= 360.0F;
            table.previousSpin -= 360.0F;
        }
        table.bob += FLOAT_SPEED;
        if (table.bob >= (float) (Math.PI * 2.0D)) {
            table.bob -= (float) (Math.PI * 2.0D);
        }
    }

    public float spin(float partialTick) {
        return previousSpin + (spin - previousSpin) * partialTick;
    }

    /** How far off its rest height the globe is right now, in blocks. */
    public float rise() {
        return (float) Math.sin(bob) * 0.045F;
    }
}
