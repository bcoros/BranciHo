package com.branciho.citiesinlife.nuclear;

import com.branciho.citiesinlife.block.ControlRodBlock;
import com.branciho.citiesinlife.block.FuelRodBlock;
import com.branciho.citiesinlife.block.ReactorLeverBlock;
import com.branciho.citiesinlife.blockentity.CoolingPortBlockEntity;
import com.branciho.citiesinlife.blockentity.SteamEmitterBlockEntity;
import com.branciho.citiesinlife.blockentity.TurbineBlockEntity;
import com.branciho.citiesinlife.blockentity.UraniumStorageBlockEntity;
import com.branciho.citiesinlife.city.City;
import com.branciho.citiesinlife.city.CityData;
import com.branciho.citiesinlife.structure.Structure;
import com.branciho.citiesinlife.structure.StructureType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * What a reactor does over ten seconds.
 *
 * <p>The whole model is three equations and a clamp, and they are written to be learnable by
 * watching rather than by reading. Move a lever and the TARGET on the monitor answers before the
 * core does, so you never discover a threshold by dying at it. The turbine dial answers on the same
 * step; HEAT takes two, because the control rods travel one notch per step and the TARGET is
 * following them rather than the switch.
 *
 * <p>Two properties are deliberate and worth stating plainly, because everything else is
 * calibration:
 *
 * <ul>
 *   <li><b>Turbine power at OFF cannot explode.</b> Not as an emergent consequence of twelve
 *       constants staying in the right relationship, but as an unconditional early return you can
 *       point at. It happens to also be true by arithmetic — the largest post-scram heat is 0.20
 *       and the smallest sink on a flooded core is 0.20, so a scrammed core targets at most 760°C
 *       against a 980°C line even with every port jammed shut — but nobody should have to check
 *       that to trust it.
 *   <li><b>Nothing moves the core faster than 60°C per step.</b> No failure of any kind: every port
 *       latched, the loop cut, the emitter gone. And the lag is a constant fraction rather than a
 *       thermal mass, so a four-turbine plant reacts exactly like a one-turbine plant. The player's
 *       window between the orange alarm and the meltdown line is a design constant, not an accident
 *       of scale, and it does not shrink as they build bigger.
 * </ul>
 */
public final class NuclearSimulation {

    // ---- thermal ----------------------------------------------------------
    public static final double AMBIENT = 40.0D;
    private static final double HEAT_SCALE = 720.0D;
    private static final double LAG = 0.25D;
    private static final double RATE_CAP = 60.0D;
    private static final double CEILING = 1400.0D;

    private static final double PROMPT_SHARE = 0.80D;
    private static final double DECAY_SHARE = 0.20D;
    private static final double DECAY_LAG = 0.25D;

    /** What being flooded is worth. The only part of the sink that does not need the loop. */
    private static final double PASSIVE_COOL = 0.20D;
    private static final double COOLER_COOL = 0.35D;
    /**
     * What an intact loop is worth at a wide-open turbine.
     *
     * <p>This number is the difference between HEAT being a risk and HEAT being a suicide switch.
     * The withdrawn-rod drive is 1.0 and HEAT_SCALE is 720, so the hottest a flawless reactor can
     * be asked to sit is 40 + 720 / (PASSIVE_COOL + LOOP_BASE). At 0.55 that was 1000 degrees -
     * above the 980 meltdown line at every dial position, unconditionally, on a plant with nothing
     * wrong with it. At 0.60 it is 940: forty degrees of headroom, well past the amber alarm and
     * the red one, survivable only with the turbine wide open and nothing else going wrong. Which
     * is what a HEAT lever next to a COOLER lever is supposed to mean.
     */
    private static final double LOOP_BASE = 0.60D;
    private static final double IDLE_DUMP = LOOP_BASE * 0.45D;

    public static final double TEMP_OVERHEAT = 720.0D;
    public static final double TEMP_CRITICAL = 850.0D;
    private static final double TEMP_MELTDOWN = 980.0D;

    /** A cold core with a dead loop will not start. A hot one cannot be saved by pretending. */
    private static final double COLD_START_LIMIT = 250.0D;

    // ---- pressure ---------------------------------------------------------
    public static final double PRESS_FLOOR_BASE = 20.0D;
    private static final double PRESS_FLOOR_GAIN = 0.16D;
    private static final double PRESS_FLOOR_KNEE = 200.0D;
    private static final double PRESS_CREEP = 2.5D;
    private static final double PRESS_GAIN = 1.5D;
    private static final double PRESS_RISE_CAP = 12.0D;
    private static final double PRESS_BLEED = 4.0D;
    private static final double PRESS_VENT = 22.0D;
    private static final double PRESS_COLD_FALL = 12.0D;
    private static final double PRESS_CEILING = 320.0D;
    private static final double VENT_PENALTY = 0.75D;

    public static final double PRESSURE_WARN = 170.0D;
    public static final double PRESSURE_CRITICAL = 220.0D;
    private static final double PRESSURE_BURST = 260.0D;

    // ---- output -----------------------------------------------------------
    /**
     * Electricity per fuel rod block before the heat fraction.
     *
     * <p>Not the per-block output: HF_CAP holds the heat fraction to half, so 30 is the arithmetic
     * ceiling and about 18 is the standard cruise. The smallest legal plant is four columns eight
     * tall — 32 blocks, a little under 600 units — against the coal turbine's 150 and a windmill's
     * 50. One building replaces four coal plants, and the largest legal one covers a city of a
     * hundred thousand. Far more powerful, as asked; not absurd.
     */
    private static final int RATING_PER_BLOCK = 60;
    private static final double HF_FLOOR_TEMP = 200.0D;
    private static final double HF_SPAN = 960.0D;
    private static final double HF_CAP = 0.50D;

    /** Nothing turns below this. Full power from 60 bar. */
    private static final double PRESSURE_GATE_LOW = 25.0D;
    private static final double PRESSURE_GATE_SPAN = 35.0D;

    // ---- fuel -------------------------------------------------------------
    private static final double BURN_UNIT = 0.75D;

    // ---- fouling ----------------------------------------------------------
    private static final int[] FOUL_RATE = new int[CoolingPort.values().length];

    static {
        // The heated side fouls fastest, so the loop dies in an order that makes physical sense.
        FOUL_RATE[CoolingPort.OUTPUT_HEATED.ordinal()] = 8;
        FOUL_RATE[CoolingPort.INPUT_COOLED.ordinal()] = 6;
        FOUL_RATE[CoolingPort.OUTPUT_COOLED.ordinal()] = 4;
        FOUL_RATE[CoolingPort.INPUT_WATER.ordinal()] = 3;
    }

    /** How much charge to hand a turbine so its rotor never stutters between steps. */
    private static final int TURBINE_CHARGE = 200;

    private NuclearSimulation() {
    }

    /** Every registered reactor, once per simulation step. Called from the city tick. */
    public static void tick(MinecraftServer server) {
        CityData data = CityData.get(server);
        ReactorData reactors = ReactorData.get(server);
        Set<UUID> live = new HashSet<>();
        for (City city : data.cities()) {
            for (Structure structure : data.structuresOf(city)) {
                if (structure.type() != StructureType.NUCLEAR_PLANT) {
                    continue;
                }
                live.add(structure.id());
                ServerLevel level = server.getLevel(structure.dimension());
                if (level == null) {
                    continue;
                }
                step(level, reactors, reactors.of(structure.id()), structure);
            }
        }
        // ReactorData's own documentation says deleting the structure drops the row. Nothing was
        // making that true: every path that removes a structure - the city tool's delete box, a
        // seizure, deleting a whole city - left the temperature, pressure, fuel and eighty seconds
        // of history behind in citiesinlife_reactors.dat for the life of the world. Swept here
        // rather than at each call site so a removal path added later cannot forget to do it.
        // Melting rows are left alone; Meltdown owns those and drops them when the sequence ends.
        reactors.forgetOrphans(live);
        reactors.setDirty();
    }

    private static void step(ServerLevel level, ReactorData data, ReactorState state,
                             Structure structure) {
        if (state.melting()) {
            // A melting reactor has left the ten-second cadence entirely.
            return;
        }

        long now = level.getGameTime();
        // Chunks were away. Nothing happened here while you were gone, and a plant that was in
        // trouble when you left comes back shut down rather than detonating in your absence.
        if (state.stamp != Long.MIN_VALUE
                && now - state.stamp > 2L * com.branciho.citiesinlife.sim.CitySimulation.INTERVAL_TICKS) {
            state.stamp = now;
            if (state.temperature >= TEMP_CRITICAL || state.pressure >= PRESSURE_CRITICAL) {
                forceOff(level, ReactorSurvey.of(level, structure.min(), structure.max()));
                state.fault = ReactorFault.SAFE_MODE_RESTART.ordinal();
            }
            return;
        }
        state.stamp = now;

        ReactorSurvey survey = ReactorSurvey.of(level, structure.min(), structure.max());
        ReactorFault build = survey.buildFault();

        // ---- 1. what the controls are set to -------------------------------
        int dial = ReactorLeverBlock.positionAt(level, survey.levers().get(ReactorLever.TURBINE));
        boolean cooler = ReactorLeverBlock.positionAt(level,
                survey.levers().get(ReactorLever.COOLER)) > 0;
        boolean heat = ReactorLeverBlock.positionAt(level,
                survey.levers().get(ReactorLever.HEAT)) > 0;
        boolean vent = ReactorLeverBlock.positionAt(level,
                survey.levers().get(ReactorLever.PRESSURE)) > 0;
        double p = dial / (double) ReactorLeverBlock.MAX_POSITION;

        // ---- 2. how healthy the loop is ------------------------------------
        // Asked of the plumbing alone, and asked even when the box has a build fault, because the
        // two questions are genuinely separate. The first version zeroed the entire sink on ANY
        // build fault, so a cruising reactor that lost one waterlogged rod block - NOT_SUBMERGED,
        // a build fault - went from a healthy 0.75 sink to exactly 0.0 with its cooling loop
        // completely intact, and climbed the full 60-degree rate cap every step from 520 to 980.
        // Eighty seconds, no way back, for a bucket. Losing the water should cost the passive
        // fifth that being flooded buys, and nothing else.
        ReactorFault loopFault = survey.loopFault(level);
        int latched = 0;
        for (BlockPos port : survey.ports().values()) {
            if (level.getBlockEntity(port) instanceof CoolingPortBlockEntity cell
                    && cell.latched()) {
                latched++;
            }
        }
        double loopHealth = loopFault != null
                ? 0.0D
                : Math.max(0.0D, 1.0D - 0.25D * latched);

        boolean emitter = survey.steamEmitterWorking(level);

        // ---- 3. drive -------------------------------------------------------
        boolean fuelled = state.fuel > 0.0D;
        boolean allowed = dial > 0 && build == null && fuelled
                && (state.temperature >= COLD_START_LIMIT || loopHealth > 0.0D);
        int target = allowed ? (heat ? ReactorState.ControlRodTarget.WITHDRAWN
                                     : ReactorState.ControlRodTarget.CRUISE)
                             : ReactorState.ControlRodTarget.SCRAMMED;
        state.insertion += Integer.signum(target - state.insertion);

        // The dial IS the trip. Turning it off zeroes prompt heat on the same step, without
        // waiting forty seconds for the rods to finish travelling.
        double prompt = allowed
                ? (ReactorState.ControlRodTarget.SCRAMMED - state.insertion)
                        / (double) ReactorState.ControlRodTarget.SCRAMMED
                : 0.0D;
        state.decay += (prompt - state.decay) * DECAY_LAG;
        double drive = PROMPT_SHARE * prompt + DECAY_SHARE * state.decay;

        // ---- 4. temperature -------------------------------------------------
        double passive = survey.submerged() ? PASSIVE_COOL : 0.0D;
        double loopDraw = loopHealth * (dial > 0 ? LOOP_BASE * (0.45D + 0.55D * p) : IDLE_DUMP);
        double sink = passive + loopDraw + loopHealth * (cooler ? COOLER_COOL : 0.0D);

        // Numerator first. A core with no drive is not producing heat, so it has nowhere to
        // climb to no matter how absent its cooling is - and the sentinel used to answer 56,000
        // degrees for that case, which walked an unbuilt, unfuelled, switched-off box up to the
        // 1400 ceiling at the rate cap and detonated it four minutes later. Nothing that is not
        // running gets a target above ambient.
        state.targetTemperature = drive <= 0.0D
                ? AMBIENT
                : (sink <= 0.0D ? CEILING : AMBIENT + HEAT_SCALE * drive / sink);
        state.previousTemperature = state.temperature;
        double delta = Mth.clamp(LAG * (state.targetTemperature - state.temperature),
                -RATE_CAP, RATE_CAP);
        state.temperature = Mth.clamp(state.temperature + delta, AMBIENT, CEILING);

        // ---- 5. pressure ----------------------------------------------------
        // One equation, and both of the owner's rules fall out of the clamp at the end of it: the
        // relief valve can slow a thermal excursion and never save one, and a cooling core drags
        // its own pressure down because the floor collapses under it.
        double floor = PRESS_FLOOR_BASE
                + PRESS_FLOOR_GAIN * Math.max(0.0D, state.temperature - PRESS_FLOOR_KNEE);
        state.previousPressure = state.pressure;
        if (drive < 0.05D && state.temperature < 200.0D) {
            state.pressure -= PRESS_COLD_FALL;
        } else {
            double rise = Math.min(PRESS_RISE_CAP, PRESS_CREEP
                    + PRESS_GAIN * Math.max(0.0D, state.temperature - 500.0D) / 100.0D);
            state.pressure += rise;
            if (emitter && loopHealth > 0.0D) {
                state.pressure -= PRESS_BLEED * loopHealth;
            }
            if (vent) {
                state.pressure -= PRESS_VENT * Math.max(0.30D, loopHealth);
            }
        }
        state.pressure = Mth.clamp(state.pressure, floor, PRESS_CEILING);

        // ---- 6. output -------------------------------------------------------
        double hf = Mth.clamp((state.temperature - HF_FLOOR_TEMP) / HF_SPAN, 0.0D, HF_CAP);
        double gate = Mth.clamp((state.pressure - PRESSURE_GATE_LOW) / PRESSURE_GATE_SPAN,
                0.0D, 1.0D);
        state.output = allowed
                ? (int) Math.round(RATING_PER_BLOCK * survey.fuelRodBlocks() * p * loopHealth
                        * (vent ? VENT_PENALTY : 1.0D) * hf * gate)
                : 0;

        // ---- 7. fouling: the emitter is the reactor's chimney -----------------
        for (CoolingPort port : CoolingPort.values()) {
            BlockPos at = survey.ports().get(port);
            if (at == null || !(level.getBlockEntity(at) instanceof CoolingPortBlockEntity cell)) {
                continue;
            }
            // The emitter clears fouling. An idle reactor does not. Clearing on any zero-output
            // step meant a player could flick the turbine dial off for a few steps and wash the
            // ports clean - one step on, one step off is a net minus two per step, so no port ever
            // latched and the chimney the whole mechanic exists to make matter was never needed.
            if (emitter) {
                cell.clear();
            } else if (state.output > 0) {
                cell.foul(FOUL_RATE[port.ordinal()]);
            }
        }
        if (emitter && state.output > 0) {
            for (BlockPos at : survey.steamEmitters()) {
                if (level.getBlockEntity(at) instanceof SteamEmitterBlockEntity plume) {
                    plume.keepEmitting(now);
                }
            }
        }

        // ---- 8. fuel ---------------------------------------------------------
        int rodBlocks = survey.fuelRodBlocks();
        state.fuel = Math.max(0.0D, state.fuel - BURN_UNIT * rodBlocks * drive);
        BlockPos store = survey.uraniumStore();
        if (store != null && rodBlocks > 0
                && level.getBlockEntity(store) instanceof UraniumStorageBlockEntity tank) {
            double capacity = (double) UraniumStorageBlockEntity.UNITS_PER_ITEM * rodBlocks;
            int want = (int) Math.min(UraniumStorageBlockEntity.TRANSFER_PER_STEP,
                    Math.max(0.0D, capacity - state.fuel));
            if (want > 0) {
                state.fuel = Math.min(capacity, state.fuel + tank.draw(want));
            }
        }
        paintRods(level, survey, state, rodBlocks);

        // ---- 9. delivery, through the machinery that already exists -----------
        int perTurbine = survey.turbines().isEmpty()
                ? 0 : state.output / survey.turbines().size();
        for (BlockPos at : survey.turbines()) {
            if (level.getBlockEntity(at) instanceof TurbineBlockEntity turbine) {
                turbine.accept(TURBINE_CHARGE, perTurbine);
            }
        }

        // ---- 10. what to say about it ----------------------------------------
        List<ReactorFault> wrong = faults(level, survey, state, build, loopFault, emitter,
                latched, dial, allowed);
        state.fault = wrong.isEmpty() ? -1 : wrong.get(0).ordinal();
        state.faultCount = Math.max(0, wrong.size() - 1);
        state.record();

        // ---- 11. the OFF guarantee, as a line of code -------------------------
        // Two conditions, and the second one is not a nicety. The dial is the owner's promise:
        // turbine power off means it will not generate electricity and it will not explode. The
        // build fault is the other half - a box that the game does not consider a reactor, with no
        // fuel in it and no output, must not be able to detonate because somebody clicked a lever
        // to see what it did. A running plant that loses a block mid-excursion is a different
        // thing, and that one still melts down, because by then it is hot and it is fuelled.
        if (dial == 0 || build != null) {
            return;
        }
        if (state.temperature >= TEMP_MELTDOWN || state.pressure >= PRESSURE_BURST) {
            state.meltdownTick = 0;
            // Cleared so the sequence measures this plant rather than inheriting a schedule from
            // one that melted down before it.
            state.meltdownPipes = -1;
            state.meltdownTurbines = -1;
        }
    }

    /**
     * Push the fuel level and rod travel out to the blocks.
     *
     * <p>One column per step, round-robin, and only when the displayed quarter actually changed.
     * A reactor is up to five hundred rod blocks; setting every one of them every ten seconds
     * would be a block update storm to show a number that moves once a minute.
     */
    private static void paintRods(ServerLevel level, ReactorSurvey survey, ReactorState state,
                                  int rodBlocks) {
        int fill = rodBlocks <= 0 ? 0 : (int) Math.round(FuelRodBlock.MAX_FILL * state.fuel
                / (UraniumStorageBlockEntity.UNITS_PER_ITEM * (double) rodBlocks));
        fill = Mth.clamp(fill, 0, FuelRodBlock.MAX_FILL);

        for (ReactorSurvey.Column column : survey.fuelColumns()) {
            for (int i = 0; i < column.height(); i++) {
                BlockPos at = column.base().above(i);
                BlockState was = level.getBlockState(at);
                if (was.hasProperty(FuelRodBlock.FILL) && was.getValue(FuelRodBlock.FILL) != fill) {
                    level.setBlock(at, was.setValue(FuelRodBlock.FILL, fill), Block.UPDATE_CLIENTS);
                }
            }
        }
        for (ReactorSurvey.Column column : survey.controlColumns()) {
            for (int i = 0; i < column.height(); i++) {
                BlockPos at = column.base().above(i);
                BlockState was = level.getBlockState(at);
                if (was.hasProperty(ControlRodBlock.INSERTION)
                        && was.getValue(ControlRodBlock.INSERTION) != state.insertion) {
                    level.setBlock(at, was.setValue(ControlRodBlock.INSERTION, state.insertion),
                            Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    /** Everything currently wrong, worst first. The head is the headline; the rest get counted. */
    private static List<ReactorFault> faults(ServerLevel level, ReactorSurvey survey,
                                             ReactorState state, ReactorFault build,
                                             ReactorFault loopFault, boolean emitter, int latched,
                                             int dial, boolean allowed) {
        List<ReactorFault> wrong = new ArrayList<>();
        if (build != null) {
            wrong.add(build);
        }
        // A dry core is louder than anything except not being a reactor: it stops the plant dead
        // and costs it the one part of its cooling that does not depend on the loop.
        if (!survey.submerged()) {
            wrong.add(ReactorFault.CORE_UNCOVERED);
        }
        // Ahead of every cause, because this is the effect and it is the one on a timer.
        if (state.temperature >= TEMP_CRITICAL) {
            wrong.add(ReactorFault.CORE_CRITICAL);
        } else if (state.temperature >= TEMP_OVERHEAT) {
            wrong.add(ReactorFault.CORE_OVERHEATING);
        }
        if (state.pressure >= PRESSURE_CRITICAL) {
            wrong.add(ReactorFault.PRESSURE_BURSTING);
        } else if (state.pressure >= PRESSURE_WARN) {
            wrong.add(ReactorFault.PRESSURE_HIGH);
        }
        if (loopFault != null && loopFault != build) {
            wrong.add(loopFault);
        }
        if (latched > 0) {
            wrong.add(ReactorFault.PORT_CLOGGED);
        }
        if (!emitter) {
            wrong.add(ReactorFault.NO_STEAM_EMITTER);
        }
        if (state.fuel <= 0.0D) {
            wrong.add(ReactorFault.FUEL_EXHAUSTED);
        }
        if (dial > 0 && !allowed && build == null && state.fuel > 0.0D) {
            wrong.add(ReactorFault.LOOP_DEAD_COLD);
        }
        if (allowed && state.pressure < 60.0D && state.output <= 0) {
            wrong.add(ReactorFault.PRESSURE_LOW);
        }
        if (survey.heightCapped()) {
            wrong.add(ReactorFault.HEIGHT_CAPPED);
        }
        if (dial == 0) {
            wrong.add(ReactorFault.OFF);
        }
        return wrong;
    }

    /** Slam the turbine dial to zero. Used when a plant comes back from unloaded chunks hot. */
    private static void forceOff(ServerLevel level, ReactorSurvey survey) {
        BlockPos dial = survey.levers().get(ReactorLever.TURBINE);
        if (dial == null) {
            return;
        }
        BlockState state = level.getBlockState(dial);
        if (state.hasProperty(ReactorLeverBlock.POSITION)
                && state.getValue(ReactorLeverBlock.POSITION) != 0) {
            level.setBlock(dial, state.setValue(ReactorLeverBlock.POSITION, 0), Block.UPDATE_ALL);
        }
    }
}
