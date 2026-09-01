package com.branciho.citiesinlife.nuclear;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

/**
 * One reactor's accumulated condition: what it cannot re-derive by looking at its own blocks.
 *
 * <p>Almost nothing lives here. The geometry, the plumbing, the lever positions and whether the
 * core is flooded are all re-read from the world every step, because the blocks themselves already
 * are the record and a second copy would only give it something to disagree with. What survives is
 * the handful of numbers that are genuinely history — how hot it has got, how hard it has been
 * driven, how much fuel is left — plus the one flag that must never be lost.
 */
public class ReactorState {

    // ---- the two gauges ---------------------------------------------------
    public double temperature = NuclearSimulation.AMBIENT;
    public double previousTemperature = NuclearSimulation.AMBIENT;
    public double targetTemperature = NuclearSimulation.AMBIENT;
    public double pressure = NuclearSimulation.PRESS_FLOOR_BASE;
    public double previousPressure = NuclearSimulation.PRESS_FLOOR_BASE;

    /**
     * How hard the core has been driven lately, smoothed.
     *
     * <p>Twenty per cent of heat production comes from this and the control rods cannot touch it,
     * which is the whole reason a shutdown is a glide rather than a switch — and the reason being
     * flooded is worth something mechanical rather than being scenery.
     */
    public double decay;

    public double fuel;

    /** 0 withdrawn to 4 fully in. Moves one notch per step, so a full stroke takes forty seconds. */
    public int insertion = ControlRodTarget.SCRAMMED;

    public int output;

    /** The first failing check, cached so the monitor, the alarm and the wrench cannot disagree. */
    public int fault = -1;
    public int faultCount;

    /**
     * Position in the meltdown sequence, in ticks, or -1 for a reactor that is not melting.
     *
     * <p>Persisted deliberately. A restart in the middle of a meltdown resumes it; reloading a
     * world is not a pardon.
     */
    public int meltdownTick = -1;

    /**
     * How many pipes and turbines the meltdown was scheduled against, captured when the fuse ends.
     *
     * <p>Persisted for the same reason {@link #meltdownTick} is, and for one more: the sequence's
     * phase boundaries are arithmetic on these two numbers, and the sequence spends its whole life
     * destroying the blocks they count. Recomputing them each tick moved the boundaries backwards
     * underneath a tick counter that only ever moves forwards, so phases were stepped over
     * entirely - on a plant with an odd pipe count the turbines were never detonated at all and
     * the fuel rods were sometimes never removed. Numbers fixed at the start cannot drift.
     */
    public int meltdownPipes = -1;
    public int meltdownTurbines = -1;

    /** Eighty seconds of trend for the monitor's two sparklines: 8 temperatures then 8 pressures. */
    public final short[] history = new short[16];

    /** Game time of the last completed step, so an unloaded reactor can be told it was away. */
    public long stamp = Long.MIN_VALUE;

    /** Named so the scram value is not a bare 4 scattered through the simulation. */
    public static final class ControlRodTarget {
        public static final int WITHDRAWN = 0;
        public static final int CRUISE = 2;
        public static final int SCRAMMED = 4;

        private ControlRodTarget() {
        }
    }

    public boolean melting() {
        return meltdownTick >= 0;
    }

    /** Push this step's samples onto the two rolling traces. */
    public void record() {
        System.arraycopy(history, 1, history, 0, 7);
        history[7] = (short) Mth.clamp((int) temperature, 0, Short.MAX_VALUE);
        System.arraycopy(history, 9, history, 8, 7);
        history[15] = (short) Mth.clamp((int) pressure, 0, Short.MAX_VALUE);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("temperature", temperature);
        tag.putDouble("previousTemperature", previousTemperature);
        tag.putDouble("targetTemperature", targetTemperature);
        tag.putDouble("pressure", pressure);
        tag.putDouble("previousPressure", previousPressure);
        tag.putDouble("decay", decay);
        tag.putDouble("fuel", fuel);
        tag.putInt("insertion", insertion);
        tag.putInt("output", output);
        tag.putInt("fault", fault);
        tag.putInt("faultCount", faultCount);
        tag.putInt("meltdownTick", meltdownTick);
        tag.putInt("meltdownPipes", meltdownPipes);
        tag.putInt("meltdownTurbines", meltdownTurbines);
        tag.putLong("stamp", stamp);
        int[] trace = new int[history.length];
        for (int i = 0; i < history.length; i++) {
            trace[i] = history[i];
        }
        tag.putIntArray("history", trace);
        return tag;
    }

    public static ReactorState load(CompoundTag tag) {
        ReactorState state = new ReactorState();
        state.temperature = tag.getDouble("temperature");
        state.previousTemperature = tag.getDouble("previousTemperature");
        state.targetTemperature = tag.getDouble("targetTemperature");
        state.pressure = tag.getDouble("pressure");
        state.previousPressure = tag.getDouble("previousPressure");
        state.decay = tag.getDouble("decay");
        state.fuel = tag.getDouble("fuel");
        state.insertion = Mth.clamp(tag.getInt("insertion"), 0, ControlRodTarget.SCRAMMED);
        state.output = tag.getInt("output");
        state.fault = tag.contains("fault") ? tag.getInt("fault") : -1;
        state.faultCount = tag.getInt("faultCount");
        state.meltdownTick = tag.contains("meltdownTick") ? tag.getInt("meltdownTick") : -1;
        state.meltdownPipes = tag.contains("meltdownPipes") ? tag.getInt("meltdownPipes") : -1;
        state.meltdownTurbines =
                tag.contains("meltdownTurbines") ? tag.getInt("meltdownTurbines") : -1;
        state.stamp = tag.contains("stamp") ? tag.getLong("stamp") : Long.MIN_VALUE;
        int[] trace = tag.getIntArray("history");
        for (int i = 0; i < state.history.length && i < trace.length; i++) {
            state.history[i] = (short) trace[i];
        }
        return state;
    }
}
