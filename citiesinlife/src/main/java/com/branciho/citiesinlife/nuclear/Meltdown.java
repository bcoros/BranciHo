package com.branciho.citiesinlife.nuclear;

import com.branciho.citiesinlife.block.AlarmBlock;
import com.branciho.citiesinlife.block.CoolingPortBlock;
import com.branciho.citiesinlife.block.PressurizedPipeBlock;
import com.branciho.citiesinlife.block.ReactorRodBlock;
import com.branciho.citiesinlife.block.SealingBlock;
import com.branciho.citiesinlife.block.WaterPipeBlock;
import com.branciho.citiesinlife.city.City;
import com.branciho.citiesinlife.city.CityData;
import com.branciho.citiesinlife.config.CitiesInLifeConfig;
import com.branciho.citiesinlife.power.PowerGrid;
import com.branciho.citiesinlife.sim.CitySimulation;
import com.branciho.citiesinlife.structure.Structure;
import com.branciho.citiesinlife.structure.StructureType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The end of a reactor, staged over thirteen seconds.
 *
 * <p>Once armed there is no reprieve, no grace period and no last-second lever. The three to eleven
 * minutes of siren before arming <em>are</em> the grace period; bolting another one on the end would
 * be a lie the alarms had already told the player not to expect.
 *
 * <p>Stepped every server tick rather than every ten seconds, because at the simulation's cadence
 * this would be a twenty-minute slideshow instead of something you can run away from. Three seconds
 * pass before anything is destroyed — not mercy, legibility: you have to see what is about to
 * happen rather than being deleted mid-sentence.
 */
public final class Meltdown {

    /** Nothing breaks for this long. Long enough to look up; not long enough to fix anything. */
    private static final int FUSE = 60;

    private static final int PIPE_INTERVAL = 3;
    private static final int TURBINE_INTERVAL = 16;
    private static final int CORE_DELAY = 40;
    private static final int FINAL_DELAY = 40;
    private static final int SATELLITE_INTERVAL = 3;
    private static final int SATELLITES = 6;
    private static final int SATELLITE_RING = 10;

    /** Vanilla TNT is 4.0. A pipe is unmistakably smaller; the last blast is unmistakably not. */
    private static final float POWER_PIPE = 2.0F;
    private static final float POWER_TURBINE = 6.0F;
    private static final float POWER_CORE = 10.0F;
    private static final float POWER_FINAL = 14.0F;
    private static final float POWER_SATELLITE = 5.0F;

    /** Beyond this the pipes are removed rather than detonated, so a huge plant cannot stall a tick. */
    private static final int MAX_PIPE_BLASTS = 48;

    private Meltdown() {
    }

    /** Every melting reactor, every server tick. Iterated before the ordinary simulation. */
    public static void tick(MinecraftServer server) {
        ReactorData data = ReactorData.get(server);
        List<UUID> finished = new ArrayList<>();
        for (Map.Entry<UUID, ReactorState> entry : data.all().entrySet()) {
            ReactorState state = entry.getValue();
            if (!state.melting()) {
                continue;
            }
            Structure plant = find(server, entry.getKey());
            if (plant == null) {
                finished.add(entry.getKey());
                continue;
            }
            ServerLevel level = server.getLevel(plant.dimension());
            if (level == null) {
                continue;
            }
            if (advance(level, plant, state)) {
                aftermath(server, level, plant);
                finished.add(entry.getKey());
            }
        }
        for (UUID id : finished) {
            data.forget(id);
        }
        if (!finished.isEmpty()) {
            data.setDirty();
        }
    }

    private static Structure find(MinecraftServer server, UUID id) {
        CityData data = CityData.get(server);
        for (City city : data.cities()) {
            for (Structure structure : data.structuresOf(city)) {
                if (structure.id().equals(id)) {
                    return structure;
                }
            }
        }
        return null;
    }

    /** Advance one tick. Returns true when the sequence is over. */
    private static boolean advance(ServerLevel level, Structure plant, ReactorState state) {
        ReactorSurvey survey = ReactorSurvey.of(level, plant.min(), plant.max());
        int t = state.meltdownTick++;
        double scale = CitiesInLifeConfig.nuclearBlastScale();

        if (t == 0) {
            state.output = 0;
            // Every alarm in the box goes to its loudest setting and stays there.
            for (BlockPos at : survey.alarms()) {
                BlockState alarm = level.getBlockState(at);
                if (alarm.hasProperty(AlarmBlock.TROUBLE)) {
                    level.setBlock(at, alarm.setValue(AlarmBlock.TROUBLE, AlarmBlock.Trouble.FIRE),
                            Block.UPDATE_ALL);
                }
            }
            // The xyz overload, because there is no BlockPos form that takes a sound Holder.
            BlockPos centre = centre(plant);
            level.playSound(null, centre.getX(), centre.getY(), centre.getZ(),
                    SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 4.0F, 0.35F);
            return false;
        }
        if (t < FUSE) {
            return false;
        }

        List<BlockPos> pipes = plumbing(level, plant, survey);
        int pipeTicks = Math.min(pipes.size(), MAX_PIPE_BLASTS) * PIPE_INTERVAL;

        // ---- the fuse burns along the pipework ------------------------------
        if (t < FUSE + pipeTicks) {
            int step = (t - FUSE);
            if (step % PIPE_INTERVAL == 0) {
                int index = step / PIPE_INTERVAL;
                if (index < pipes.size()) {
                    blast(level, pipes.get(index), POWER_PIPE * scale, false);
                }
            }
            return false;
        }
        // Anything past the cap is taken away quietly rather than detonated.
        if (t == FUSE + pipeTicks) {
            for (int i = MAX_PIPE_BLASTS; i < pipes.size(); i++) {
                level.setBlock(pipes.get(i), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
            return false;
        }

        int afterPipes = FUSE + pipeTicks + 1;
        int turbineTicks = survey.turbines().size() * TURBINE_INTERVAL;

        // ---- the turbines go, one at a time --------------------------------
        if (t < afterPipes + turbineTicks) {
            int step = t - afterPipes;
            if (step % TURBINE_INTERVAL == 0) {
                int index = step / TURBINE_INTERVAL;
                if (index < survey.turbines().size()) {
                    blast(level, survey.turbines().get(index), POWER_TURBINE * scale, true);
                }
            }
            return false;
        }

        int coreAt = afterPipes + turbineTicks + CORE_DELAY;

        // ---- the core ------------------------------------------------------
        if (t == coreAt) {
            // The rods are removed first so the blast is not spent chewing through three hundred
            // hand-placed 6x6 blocks it would clear anyway.
            BlockPos heart = centroid(survey.rodBlocks(), centre(plant));
            for (BlockPos at : survey.rodBlocks()) {
                level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
            for (BlockPos at : survey.rodBlocks()) {
                BlockPos lid = at.above();
                if (level.getBlockState(lid).getBlock() instanceof SealingBlock) {
                    level.setBlock(lid, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
            blast(level, heart, POWER_CORE * scale, true);
            return false;
        }
        if (t < coreAt) {
            return false;
        }

        int finalAt = coreAt + FINAL_DELAY;
        if (t == finalAt) {
            blast(level, centre(plant), POWER_FINAL * scale, true);
            return false;
        }

        // ---- and the ring, which is what makes it read as an event rather than a sphere ----
        if (t > finalAt) {
            int step = t - finalAt;
            if (step % SATELLITE_INTERVAL == 0) {
                int index = step / SATELLITE_INTERVAL - 1;
                if (index >= 0 && index < SATELLITES) {
                    double angle = index * (Math.PI * 2.0D / SATELLITES);
                    BlockPos centre = centre(plant);
                    BlockPos at = centre.offset(
                            (int) Math.round(Math.cos(angle) * SATELLITE_RING), 0,
                            (int) Math.round(Math.sin(angle) * SATELLITE_RING));
                    blast(level, at, POWER_SATELLITE * scale, true);
                }
                return step / SATELLITE_INTERVAL - 1 >= SATELLITES - 1;
            }
        }
        return false;
    }

    /**
     * BLOCK rather than MOB, deliberately.
     *
     * <p>{@code ExplosionInteraction.MOB} is gated on the mobGriefing gamerule and silently does no
     * terrain damage when it is off. A meltdown that quietly stopped being dangerous on half of all
     * servers would be worse than one that never existed.
     */
    private static void blast(ServerLevel level, BlockPos at, float power, boolean fire) {
        level.explode(null, at.getX() + 0.5D, at.getY() + 0.5D, at.getZ() + 0.5D,
                power, fire, Level.ExplosionInteraction.BLOCK);
    }

    /** Everything on the cooling circuit, nearest the core first, so the fuse visibly burns inward. */
    private static List<BlockPos> plumbing(ServerLevel level, Structure plant,
                                           ReactorSurvey survey) {
        BlockPos heart = centroid(survey.rodBlocks(), centre(plant));
        List<BlockPos> found = new ArrayList<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = plant.min().getY(); y <= plant.max().getY(); y++) {
            for (int z = plant.min().getZ(); z <= plant.max().getZ(); z++) {
                for (int x = plant.min().getX(); x <= plant.max().getX(); x++) {
                    cursor.set(x, y, z);
                    Block block = level.getBlockState(cursor).getBlock();
                    if (block instanceof WaterPipeBlock || block instanceof PressurizedPipeBlock
                            || block instanceof CoolingPortBlock) {
                        found.add(cursor.immutable());
                    }
                }
            }
        }
        found.sort(Comparator.comparingDouble(p -> p.distSqr(heart)));
        return found;
    }

    private static BlockPos centre(Structure plant) {
        return new BlockPos(
                (plant.min().getX() + plant.max().getX()) / 2,
                (plant.min().getY() + plant.max().getY()) / 2,
                (plant.min().getZ() + plant.max().getZ()) / 2);
    }

    private static BlockPos centroid(List<BlockPos> of, BlockPos fallback) {
        if (of.isEmpty()) {
            return fallback;
        }
        long x = 0;
        long y = 0;
        long z = 0;
        for (BlockPos at : of) {
            x += at.getX();
            y += at.getY();
            z += at.getZ();
        }
        return new BlockPos((int) (x / of.size()), (int) (y / of.size()), (int) (z / of.size()));
    }

    /**
     * Tidy up after the crater.
     *
     * <p>The blackout is the second half of the punishment, so it has to land immediately rather
     * than ten seconds later. And a destroyed turbine still listed on the power grid would be a
     * phantom producer feeding a city that no longer has a power station.
     */
    private static void aftermath(MinecraftServer server, ServerLevel level, Structure plant) {
        PowerGrid grid = PowerGrid.get(server);
        ReactorSurvey survey = ReactorSurvey.of(level, plant.min(), plant.max());
        for (BlockPos at : survey.turbines()) {
            grid.removeNode(level.dimension(), at);
        }
        CityData data = CityData.get(server);
        City owner = data.city(plant.cityId());
        data.removeStructure(plant.id());
        ReactorSurvey.forgetAll();
        if (owner != null) {
            CitySimulation.refresh(data, owner);
        }
        data.setDirty();
    }

    /** Whether anything anywhere is currently melting, so the tick can bail out cheaply. */
    public static boolean anyMelting(MinecraftServer server) {
        for (ReactorState state : ReactorData.get(server).all().values()) {
            if (state.melting()) {
                return true;
            }
        }
        return false;
    }

    /** The type gate, so callers do not have to import StructureType to ask. */
    public static boolean isReactor(Structure structure) {
        return structure.type() == StructureType.NUCLEAR_PLANT;
    }
}
