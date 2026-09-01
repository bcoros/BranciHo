package com.branciho.citiesinlife.nuclear;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * One line about the reactor you are standing in, for whichever of its blocks you happened to hit.
 *
 * <p>Every reactor block routes its right click here — rods, ports, levers, the emitter, the store,
 * the turbines, the pressurised pipe. That is the whole point of the class existing: a player who
 * has built most of a reactor and cannot see why it is dead must be able to punch any part of it
 * and be told, rather than having to finish building the monitor to discover what is missing. The
 * monitor is a nicer way to read this; it is never the only way.
 */
public final class ReactorReadout {

    private ReactorReadout() {
    }

    /** What the plant containing this position would say if asked. */
    public static Component describe(Level level, BlockPos pos) {
        ReactorSurvey survey = ReactorSurvey.at(level, pos);
        if (!survey.registered()) {
            return ReactorFault.NOT_REGISTERED.describe();
        }

        ReactorFault build = survey.buildFault();
        if (build != null) {
            return sentence(survey, build);
        }

        if (level instanceof ServerLevel serverLevel) {
            ReactorFault loop = survey.loopFault(serverLevel);
            if (loop != null) {
                return sentence(survey, loop);
            }
        }

        return Component.translatable("reactor.citiesinlife.built",
                survey.turbines().size(), survey.columnHeight(), survey.fuelRodBlocks());
    }

    /**
     * The fault, with whatever numbers or coordinates make it actionable.
     *
     * <p>The counting faults get both figures because "wrong number of fuel columns" is useless and
     * "8 of 12 required" is an instruction.
     */
    public static Component sentence(ReactorSurvey survey, ReactorFault fault) {
        return switch (fault) {
            case FUEL_COLUMN_COUNT -> fault.describe(survey.fuelColumns().size(),
                    ReactorSurvey.FUEL_COLUMNS_PER_TURBINE * survey.turbines().size());
            case CONTROL_COLUMN_COUNT -> fault.describe(survey.controlColumns().size(),
                    ReactorSurvey.CONTROL_COLUMNS);
            case TOO_MANY_TURBINES -> fault.describe(survey.turbines().size(),
                    ReactorSurvey.MAX_TURBINES);
            case COLUMN_TOO_SHORT -> fault.describe(survey.columnHeight(),
                    com.branciho.citiesinlife.block.ReactorRodBlock.MIN_HEIGHT);
            case COLUMNS_UNEVEN -> fault.describe(survey.oddColumnHeight(), survey.columnHeight());
            default -> {
                BlockPos where = survey.faultAt(fault);
                yield where != null ? fault.describe(where) : fault.describe();
            }
        };
    }
}
