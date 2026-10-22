package com.branciho.citiesinlife.blockentity;

import com.branciho.citiesinlife.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A cooling port's fouling.
 *
 * <p>Four separate counters across the loop rather than one number on the reactor, so the loop dies
 * a port at a time — visibly, with a name on the monitor and somewhere specific to point the
 * wrench. A single "cooling: 40%" would be a number with nothing to do about it.
 *
 * <p>Fouling is <em>not</em> in the block state, deliberately. {@code joinsAutomatically} is handed
 * a {@code BlockGetter} and is called during placement when neighbouring states do not exist yet,
 * so it can only answer from the state it is given — which means anything affecting whether a pipe
 * attaches must live in the state. A clogged port still conducts; it just carries less. Keeping
 * fouling out of the state is what stops a clog silently rearranging the pipework.
 */
public class CoolingPortBlockEntity extends BlockEntity {

    /** At this much fouling the port latches shut and only the Repair Tool will open it. */
    public static final int CLOG_LIMIT = 100;

    /** How fast a port recovers once the steam emitter is doing its job. */
    public static final int CLOG_DECAY = 10;

    private int clog;

    public CoolingPortBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.COOLING_PORT.get(), pos, state);
    }

    public int clog() {
        return clog;
    }

    public boolean latched() {
        return clog >= CLOG_LIMIT;
    }

    /** Foul this port. Once latched it stays latched however much more is thrown at it. */
    public void foul(int amount) {
        if (latched()) {
            return;
        }
        clog = Mth.clamp(clog + amount, 0, CLOG_LIMIT);
        setChanged();
    }

    /** Let a port that is not yet latched recover. */
    public void clear() {
        if (latched() || clog == 0) {
            return;
        }
        clog = Math.max(0, clog - CLOG_DECAY);
        setChanged();
    }

    /**
     * What the Repair Tool does. Returns false when there was nothing wrong, so the wrench can say
     * so rather than pretending it fixed something — exactly as it already does for a turbine.
     */
    public boolean repair() {
        if (clog == 0) {
            return false;
        }
        clog = 0;
        setChanged();
        return true;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        clog = Mth.clamp(tag.getInt("clog"), 0, CLOG_LIMIT);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("clog", clog);
    }
}
