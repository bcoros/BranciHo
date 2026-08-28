package com.branciho.citiesinlife.blockentity;

import com.branciho.citiesinlife.block.AlarmBlock;
import com.branciho.citiesinlife.city.CityData;
import com.branciho.citiesinlife.nuclear.NuclearSimulation;
import com.branciho.citiesinlife.nuclear.ReactorData;
import com.branciho.citiesinlife.nuclear.ReactorState;
import com.branciho.citiesinlife.nuclear.ReactorSurvey;
import com.branciho.citiesinlife.plant.PlantSurvey;
import com.branciho.citiesinlife.structure.Structure;
import com.branciho.citiesinlife.structure.StructureType;
import net.minecraft.server.level.ServerLevel;
import com.branciho.citiesinlife.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What the alarm is watching, and how loudly.
 *
 * <p>Nothing is stored. The answer is always "go and look at the plant", and the plant survey is
 * already cached and shared, so an alarm costs about as much as asking a boiler the same question.
 */
public class AlarmBlockEntity extends BlockEntity {

    /** How often the plant is checked. Twice a second is far faster than a turbine can change. */
    private static final int CHECK_INTERVAL = 10;

    /** How often the lens flips. Three flashes a second, which reads as urgent without strobing. */
    private static final int FLASH_INTERVAL = 6;

    /** Ticks between siren notes, and the two pitches it alternates between. */
    private static final int SIREN_INTERVAL_FIRE = 14;
    private static final int SIREN_INTERVAL_FOULED = 40;
    private static final float SIREN_HIGH = 1.7F;
    private static final float SIREN_LOW = 1.2F;

    /** Loud enough to carry across a city, which is the entire point of it. */
    private static final float SIREN_VOLUME = 2.6F;

    private int howManyBurning;
    private int howManyClogged;
    private boolean insidePlant;
    private boolean highNote;

    /**
     * What the reactor in this box is doing, if it is a reactor rather than a coal plant.
     *
     * <p>An alarm hung in a reactor hall used to be a decoration: {@code PlantSurvey.at} answers
     * only for a Power Plant, so the siren simply reported that it could not see a plant. It now
     * reads the reactor's own gauges — orange when the core is overheating, red when it is
     * critical or committed — which is the same contract it already has for coal.
     */
    private AlarmBlock.Trouble reactorLevel = AlarmBlock.Trouble.NONE;
    private boolean insideReactor;

    public AlarmBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ALARM.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, AlarmBlockEntity alarm) {
        if (level.getGameTime() % CHECK_INTERVAL == 0L) {
            alarm.survey(level, pos);
        }

        AlarmBlock.Trouble trouble = alarm.trouble();
        if (state.getValue(AlarmBlock.TROUBLE) != trouble) {
            state = state.setValue(AlarmBlock.TROUBLE, trouble);
            level.setBlock(pos, state, Block.UPDATE_ALL);
        }
        if (trouble == AlarmBlock.Trouble.NONE) {
            if (state.getValue(AlarmBlock.FLASH)) {
                level.setBlock(pos, state.setValue(AlarmBlock.FLASH, false), Block.UPDATE_ALL);
            }
            return;
        }

        if (level.getGameTime() % FLASH_INTERVAL == 0L) {
            level.setBlock(pos, state.setValue(AlarmBlock.FLASH, !state.getValue(AlarmBlock.FLASH)),
                    Block.UPDATE_ALL);
        }

        int interval = trouble == AlarmBlock.Trouble.FIRE ? SIREN_INTERVAL_FIRE : SIREN_INTERVAL_FOULED;
        if (level.getGameTime() % interval == 0L) {
            // Two alternating pitches rather than one repeated note: a single tone reads as a broken
            // block, and a rising-falling pair reads as a siren without needing a custom sound.
            alarm.highNote = !alarm.highNote;
            level.playSound(null, pos, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS,
                    SIREN_VOLUME, alarm.highNote ? SIREN_HIGH : SIREN_LOW);
        }
    }

    /**
     * Ask the plant how its turbines are.
     *
     * <p>Every turbine in the box, not just the nearest — one alarm covers a whole plant, which is
     * what makes it worth putting one up rather than standing in the shed watching.
     */
    private void survey(Level level, BlockPos pos) {
        howManyBurning = 0;
        howManyClogged = 0;
        reactorLevel = AlarmBlock.Trouble.NONE;
        insideReactor = false;

        if (level instanceof ServerLevel serverLevel) {
            Structure plant = CityData.get(serverLevel.getServer())
                    .structureAt(level.dimension(), pos);
            if (plant != null && plant.type() == StructureType.NUCLEAR_PLANT) {
                insideReactor = true;
                insidePlant = true;
                reactorLevel = reactorTrouble(serverLevel, plant);
                return;
            }
        }

        PlantSurvey plant = PlantSurvey.at(level, pos);
        insidePlant = plant.registered();
        if (!insidePlant) {
            return;
        }
        for (BlockPos turbinePos : plant.turbines()) {
            if (!level.isLoaded(turbinePos)) {
                continue;
            }
            if (level.getBlockEntity(turbinePos) instanceof TurbineBlockEntity turbine) {
                if (turbine.burning()) {
                    howManyBurning++;
                } else if (turbine.clogged()) {
                    howManyClogged++;
                }
            }
        }
    }

    private AlarmBlock.Trouble trouble() {
        if (insideReactor) {
            return reactorLevel;
        }
        if (howManyBurning > 0) {
            return AlarmBlock.Trouble.FIRE;
        }
        return howManyClogged > 0 ? AlarmBlock.Trouble.FOULED : AlarmBlock.Trouble.NONE;
    }

    /**
     * How worried to be about a reactor.
     *
     * <p>Reuses the two existing states rather than inventing new ones, so the alarm needs no new
     * blockstates, models or sounds — and so the colours already mean what a player has learned
     * they mean. Amber: go and look. Red: it is nearly too late.
     */
    private static AlarmBlock.Trouble reactorTrouble(ServerLevel level, Structure plant) {
        ReactorData data = ReactorData.get(level.getServer());
        if (!data.known(plant.id())) {
            return AlarmBlock.Trouble.NONE;
        }
        ReactorState state = data.of(plant.id());
        if (state.melting()
                || state.temperature >= NuclearSimulation.TEMP_CRITICAL
                || state.pressure >= NuclearSimulation.PRESSURE_CRITICAL) {
            return AlarmBlock.Trouble.FIRE;
        }
        if (state.temperature >= NuclearSimulation.TEMP_OVERHEAT
                || state.pressure >= NuclearSimulation.PRESSURE_WARN) {
            return AlarmBlock.Trouble.FOULED;
        }
        // A jammed cooling port is worth an amber on its own: the core is still making heat and
        // the loop that carries it away has lost a quarter of itself.
        ReactorSurvey survey = ReactorSurvey.of(level, plant.min(), plant.max());
        for (BlockPos port : survey.ports().values()) {
            if (level.getBlockEntity(port) instanceof CoolingPortBlockEntity cell
                    && cell.latched()) {
                return AlarmBlock.Trouble.FOULED;
            }
        }
        return AlarmBlock.Trouble.NONE;
    }

    /** One line saying what it can see, so a silent alarm can be told from an unwired one. */
    public Component report() {
        if (!insidePlant) {
            return Component.translatable("message.citiesinlife.alarm_no_plant");
        }
        if (howManyBurning > 0) {
            return Component.translatable("message.citiesinlife.alarm_fire", howManyBurning);
        }
        if (howManyClogged > 0) {
            return Component.translatable("message.citiesinlife.alarm_fouled", howManyClogged);
        }
        return Component.translatable("message.citiesinlife.alarm_clear");
    }
}
