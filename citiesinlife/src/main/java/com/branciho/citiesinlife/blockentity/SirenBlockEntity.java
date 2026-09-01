package com.branciho.citiesinlife.blockentity;

import com.branciho.citiesinlife.block.SirenBlock;
import com.branciho.citiesinlife.city.City;
import com.branciho.citiesinlife.city.Diplomacy;
import com.branciho.citiesinlife.city.Sirens;
import com.branciho.citiesinlife.registry.ModBlockEntities;
import com.branciho.citiesinlife.sound.MachineSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A siren, deciding for itself whether it should be sounding.
 *
 * <p>It used to be told. A director walked the city's registered building boxes looking for sirens
 * to switch on, which meant a siren mounted anywhere a siren actually belongs — a pole, a rooftop,
 * the top of a wall — was never found and never made a sound in its life. Asking is both correct
 * and cheaper: one territory lookup a second against a cached answer, instead of a sweep over every
 * box in the city.
 *
 * <p>Nothing here is saved. Whether a siren is wailing is not history, it is the state of the world
 * right now, and it is re-derived within a second of the block loading.
 */
public class SirenBlockEntity extends BlockEntity {

    /** How often it asks. A second late to a siren is not late. */
    private static final int CHECK_INTERVAL = 20;

    /**
     * How often the wail is re-sounded, in ticks.
     *
     * <p>A little under the length of the horn itself, so the note runs continuously rather than
     * leaving gaps of silence that make it read as a machine repeating instead of a siren
     * sounding.
     */
    private static final int WAIL_INTERVAL = 110;

    /** Degrees per tick the horn cluster turns. Slow enough to read as heavy machinery. */
    private static final float SPIN_SPEED = 5.0F;

    /** How fast it coasts back down once the all-clear comes. */
    private static final float SPIN_DECAY = 0.15F;

    /** Counts down to the next survey. Starts at zero so a freshly loaded siren asks at once. */
    private int untilCheck;

    /** Client-side only: the horn cluster's angle, and where it was last tick, for interpolation. */
    private float spin;
    private float previousSpin;
    private float speed;

    public SirenBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SIREN.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  SirenBlockEntity siren) {
        if (--siren.untilCheck > 0) {
            return;
        }
        siren.untilCheck = CHECK_INTERVAL;
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        City own = Diplomacy.owner(server.getServer(), server.dimension(), pos);
        boolean up = own != null && Sirens.wailing(server.getServer(), own);
        if (state.getValue(SirenBlock.WAILING) != up) {
            level.setBlock(pos, state.setValue(SirenBlock.WAILING, up), Block.UPDATE_CLIENTS);
        }
    }

    /**
     * The turning, and the noise.
     *
     * <p>Both client side. The sound is played locally rather than broadcast so it answers to the
     * machine-volume setting like every other sound the mod makes — a warning nobody can turn down
     * is a warning that gets the mod uninstalled.
     */
    public static void clientTick(Level level, BlockPos pos, BlockState state,
                                  SirenBlockEntity siren) {
        boolean up = SirenBlock.wailing(state);
        siren.previousSpin = siren.spin;
        if (up) {
            siren.speed = SPIN_SPEED;
        } else if (siren.speed > 0.0F) {
            // Coasting down rather than stopping dead. A siren head this size has weight, and the
            // slow spin-down is most of what sells it as a machine rather than a prop.
            siren.speed = Math.max(0.0F, siren.speed - SPIN_DECAY);
        }
        siren.spin += siren.speed;
        if (siren.spin >= 360.0F) {
            siren.spin -= 360.0F;
            siren.previousSpin -= 360.0F;
        }
        if (!up) {
            return;
        }
        // Staggered by position, so a row of sirens along a wall sounds like a row of sirens rather
        // than one very loud siren. The offset is stable for a given pole, so each keeps its place
        // in the round for as long as it stands there.
        long offset = Math.floorMod(pos.asLong(), WAIL_INTERVAL);
        if (Math.floorMod(level.getGameTime() - offset, WAIL_INTERVAL) == 0L) {
            MachineSounds.airRaid(level, pos, level.getRandom());
        }
    }

    /** The horn cluster's angle at this exact moment, for the renderer. */
    public float spin(float partialTick) {
        return previousSpin + (spin - previousSpin) * partialTick;
    }
}
