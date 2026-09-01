package com.branciho.citiesinlife.missile;

import com.branciho.citiesinlife.block.AlarmBlock;
import com.branciho.citiesinlife.block.MissileBlock;
import com.branciho.citiesinlife.block.SealingBlock;
import com.branciho.citiesinlife.block.SirenBlock;
import com.branciho.citiesinlife.scan.StructureScanner;
import com.branciho.citiesinlife.structure.Structure;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * What is standing inside a silo box, right now.
 *
 * <p>Walked fresh rather than remembered, the way the reactor and the power plant are, and for the
 * same reason: the box is the truth and the blocks in it are the player's to change at any moment.
 * A silo that cached its missiles would go on offering to fire one somebody had already mined.
 *
 * <p>Everything except the missiles is optional. A silo needs one rocket; the roof, the alarm and
 * the siren are things you add because you want a roof, an alarm and a siren.
 */
public final class SiloSurvey {

    private final Map<MissileKind, List<BlockPos>> missiles = new EnumMap<>(MissileKind.class);
    private final List<BlockPos> seals = new ArrayList<>();
    private final List<BlockPos> alarms = new ArrayList<>();
    private final List<BlockPos> sirens = new ArrayList<>();
    private final boolean tooLarge;

    private SiloSurvey(boolean tooLarge) {
        this.tooLarge = tooLarge;
        for (MissileKind kind : MissileKind.values()) {
            missiles.put(kind, new ArrayList<>());
        }
    }

    /**
     * Look inside a silo.
     *
     * <p>Bounded by the same volume limit the reactor survey answers to. A silo is re-read on a
     * timer, so its box size is a running cost rather than a one-off, and somebody drawing a box
     * round half a continent must not be able to make it one.
     */
    public static SiloSurvey of(Level level, BlockPos min, BlockPos max) {
        long span = (long) (max.getX() - min.getX() + 1)
                * (max.getY() - min.getY() + 1)
                * (max.getZ() - min.getZ() + 1);
        if (span > StructureScanner.MAX_SURVEY_VOLUME) {
            return new SiloSurvey(true);
        }
        SiloSurvey survey = new SiloSurvey(false);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    cursor.set(x, y, z);
                    survey.record(level.getBlockState(cursor), cursor.immutable());
                }
            }
        }
        return survey;
    }

    /** The same, for a registered structure. */
    public static SiloSurvey of(Level level, Structure silo) {
        return of(level, silo.min(), silo.max());
    }

    private void record(BlockState state, BlockPos at) {
        MissileKind kind = MissileBlock.kindAt(state);
        if (kind != null) {
            missiles.get(kind).add(at);
            return;
        }
        if (state.getBlock() instanceof SealingBlock) {
            seals.add(at);
        } else if (state.getBlock() instanceof AlarmBlock) {
            alarms.add(at);
        } else if (state.getBlock() instanceof SirenBlock) {
            sirens.add(at);
        }
    }

    /** A box nobody should be allowed to re-read every few seconds. */
    public boolean tooLarge() {
        return tooLarge;
    }

    public List<BlockPos> missiles(MissileKind kind) {
        return missiles.get(kind);
    }

    /** The next one of this kind to leave, or null if there are none left. */
    public @Nullable BlockPos next(MissileKind kind) {
        List<BlockPos> stock = missiles.get(kind);
        return stock.isEmpty() ? null : stock.get(0);
    }

    public int count(MissileKind kind) {
        return missiles.get(kind).size();
    }

    /** Everything that can be fired at somebody else. */
    public int armed() {
        int total = 0;
        for (MissileKind kind : MissileKind.values()) {
            if (kind.offensive()) {
                total += count(kind);
            }
        }
        return total;
    }

    public List<BlockPos> seals() {
        return seals;
    }

    public List<BlockPos> alarms() {
        return alarms;
    }

    public List<BlockPos> sirens() {
        return sirens;
    }

    /**
     * Whether the way out is clear.
     *
     * <p>A silo with no roof is always clear — the rocket simply flies off an open pad, which is
     * what a silo without sealing blocks is. Otherwise every panel has to be all the way back: one
     * closed lid over an open shaft is a rocket into a ceiling.
     */
    public boolean openToTheSky(Level level) {
        for (BlockPos at : seals) {
            if (!SealingBlock.open(level.getBlockState(at))) {
                return false;
            }
        }
        return true;
    }
}
