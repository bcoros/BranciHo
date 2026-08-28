package com.branciho.citiesinlife.nuclear;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Every reason a reactor is not doing what its owner expected, and the exact words for each.
 *
 * <p>This enum is the feature's answer to its own worst failure mode. A nuclear plant is ten
 * hand-placed columns, four ports, a pressurised pipe, a closed loop of pipework and five controls,
 * and there is no version of "it just doesn't work" that a player can act on. So every check has a
 * name, an order, and a sentence naming the block and the coordinates.
 *
 * <p>Split into two kinds, and the distinction runs through the whole simulation:
 *
 * <ul>
 *   <li>A <b>build fault</b> means this is not a reactor. It refuses to run at all, drives the
 *       control rods fully in, and is what the Planner Wand checks before it will register the box.
 *   <li>A <b>running fault</b> means it is a reactor and it is in trouble. It keeps making heat,
 *       which is precisely what makes these the dangerous ones.
 * </ul>
 *
 * <p>The severity ordering is the check order. First failure wins the headline; the monitor counts
 * the rest.
 */
public enum ReactorFault {

    // ---- build faults: not a reactor, will not run -------------------------

    NOT_REGISTERED("not_registered", Kind.BUILD),
    BOX_TOO_LARGE("box_too_large", Kind.BUILD),
    MIXED_PLANT("mixed_plant", Kind.BUILD),
    NO_TURBINE("no_turbine", Kind.BUILD),
    TOO_MANY_TURBINES("too_many_turbines", Kind.BUILD),
    NO_FUEL_RODS("no_fuel_rods", Kind.BUILD),
    FUEL_COLUMN_COUNT("fuel_column_count", Kind.BUILD),
    CONTROL_COLUMN_COUNT("control_column_count", Kind.BUILD),
    COLUMN_TOO_SHORT("column_too_short", Kind.BUILD),
    COLUMNS_UNEVEN("columns_uneven", Kind.BUILD),
    NOT_SUBMERGED("not_submerged", Kind.BUILD),
    NOT_SEALED("not_sealed", Kind.BUILD),
    NO_URANIUM_STORE("no_uranium_store", Kind.BUILD),
    PORT_MISSING("port_missing", Kind.BUILD),
    PORT_NOT_LINKED("port_not_linked", Kind.BUILD),
    PORT_NOT_BESIDE_STORE("port_not_beside_store", Kind.BUILD),
    NO_PRESSURIZED_PIPE("no_pressurized_pipe", Kind.BUILD),

    // ---- running faults: it is a reactor, and it is still making heat ------

    /**
     * The one state a shutdown does not save you from.
     *
     * <p>Full submersion is worth a fifth of the core's entire heat sink, and it is the only part
     * of that sink which does not depend on the cooling loop. Drain the pool and a scrammed core
     * can still climb. This must be the loudest line the monitor ever prints.
     */
    CORE_UNCOVERED("core_uncovered", Kind.RUNNING),
    COOLED_RUN_BROKEN("cooled_run_broken", Kind.RUNNING),
    RETURN_RUN_BROKEN("return_run_broken", Kind.RUNNING),
    NO_FRESH_WATER("no_fresh_water", Kind.RUNNING),
    PORT_CLOGGED("port_clogged", Kind.RUNNING),
    NO_STEAM_EMITTER("no_steam_emitter", Kind.RUNNING),
    PIPE_LEAKING("pipe_leaking", Kind.RUNNING),
    LOOP_DEAD_COLD("loop_dead_cold", Kind.RUNNING),
    PRESSURE_LOW("pressure_low", Kind.RUNNING),
    FUEL_EXHAUSTED("fuel_exhausted", Kind.RUNNING),
    SAFE_MODE_RESTART("safe_mode_restart", Kind.RUNNING),

    /** Not a fault at all, and says so. The plant is off because somebody turned it off. */
    OFF("off", Kind.NOTICE),

    /** Built taller than the model rewards. Worth saying so rather than quietly ignoring blocks. */
    HEIGHT_CAPPED("height_capped", Kind.NOTICE);

    /** What a fault means for whether the core runs. */
    public enum Kind { BUILD, RUNNING, NOTICE }

    private final String id;
    private final Kind kind;

    ReactorFault(String id, Kind kind) {
        this.id = id;
        this.kind = kind;
    }

    public String id() {
        return id;
    }

    public Kind kind() {
        return kind;
    }

    /** Whether this refusal means the core will not run at all. */
    public boolean stopsTheCore() {
        return kind == Kind.BUILD;
    }

    public Component describe() {
        return Component.translatable("reactor.citiesinlife." + id);
    }

    /** The same message with a position in it, for the faults that can name the offending block. */
    public Component describe(@Nullable BlockPos where) {
        if (where == null) {
            return describe();
        }
        return Component.translatable("reactor.citiesinlife." + id + ".at",
                where.getX(), where.getY(), where.getZ());
    }

    /** The same message with two numbers in it, for the counting faults. */
    public Component describe(int found, int wanted) {
        return Component.translatable("reactor.citiesinlife." + id + ".n", found, wanted);
    }
}
