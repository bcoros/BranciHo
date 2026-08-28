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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The end of a reactor, staged over thirteen seconds.
 *
 * <p>Once armed there is no reprieve, no grace period and no last-second lever. The siren before
 * arming <em>is</em> the grace period; bolting another one on the end would be a lie the alarms had
 * already told the player not to expect. That window is short and worth stating honestly: from the
 * amber alarm at 720 degrees to arming at 980 is five steps in the fastest excursion and ten in the
 * ordinary one — fifty seconds to a hundred. Long enough to reach a lever, not long enough to
 * rebuild anything.
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

    /**
     * What each melting reactor was scheduled against.
     *
     * <p>In memory only. The counts that the phase arithmetic depends on live on {@link
     * ReactorState} and are persisted; this is just the positions, which are cheap to find again
     * and only matter for the thirteen seconds the sequence runs. A restart mid-meltdown rebuilds
     * the lists from whatever is left standing and keeps the persisted schedule, so the boundaries
     * stay exactly where they were.
     */
    private static final Map<UUID, Stage> STAGES = new HashMap<>();

    private record Stage(List<BlockPos> pipes, List<BlockPos> turbines, List<BlockPos> rods,
                         BlockPos heart) {
    }

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
                // Treated exactly like a missing structure. Skipping instead left meltdownTick at
                // whatever it had reached, which NuclearSimulation.step reads as "melting" and
                // returns on - so a plant whose dimension went away was frozen out of the
                // simulation for the rest of the world's life, permanently about to explode.
                finished.add(entry.getKey());
                continue;
            }
            if (advance(level, plant, state)) {
                aftermath(server, level, plant);
                finished.add(entry.getKey());
            }
        }
        for (UUID id : finished) {
            data.forget(id);
            STAGES.remove(id);
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
        int t = state.meltdownTick++;
        // Narrowed here rather than at each call site: every blast power is a float, and
        // float * double is a double that javac will not silently narrow back.
        float scale = (float) CitiesInLifeConfig.nuclearBlastScale();

        if (t == 0) {
            state.output = 0;
            ReactorSurvey survey = ReactorSurvey.of(level, plant.min(), plant.max());
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

        // The list is captured once, and the schedule is arithmetic on counts that were frozen at
        // the same moment. Both halves matter. The first version rebuilt this list every tick from
        // a world the meltdown was busy deleting, so the boundaries walked backwards while t walked
        // forwards: a blast that took two pipes dropped the end of the pipe phase below the current
        // tick, both the "<" and the "==" tests missed, and the sequence fell through into the
        // turbine phase at a step number that its own modulo never matched again. The turbines were
        // simply never detonated. It also meant a full 150,000-block rescan and sort every server
        // tick for the length of the meltdown, in the exact window the player is meant to be
        // running away.
        Stage stage = stageFor(level, plant, state);
        int pipeTicks = Math.min(state.meltdownPipes, MAX_PIPE_BLASTS) * PIPE_INTERVAL;

        // ---- the fuse burns along the pipework ------------------------------
        if (t < FUSE + pipeTicks) {
            int step = (t - FUSE);
            if (step % PIPE_INTERVAL == 0) {
                int index = step / PIPE_INTERVAL;
                if (index < stage.pipes().size()) {
                    blast(level, stage.pipes().get(index), POWER_PIPE * scale, false);
                }
            }
            return false;
        }
        // Anything past the cap is taken away quietly rather than detonated. Indexed into the
        // captured list, because the live one has already shrunk below the cap by now and this
        // loop used to run zero times - leaving a big plant's outer pipework standing.
        if (t == FUSE + pipeTicks) {
            for (int i = MAX_PIPE_BLASTS; i < stage.pipes().size(); i++) {
                level.setBlock(stage.pipes().get(i), Blocks.AIR.defaultBlockState(),
                        Block.UPDATE_ALL);
            }
            return false;
        }

        int afterPipes = FUSE + pipeTicks + 1;
        int turbineTicks = state.meltdownTurbines * TURBINE_INTERVAL;

        // ---- the turbines go, one at a time --------------------------------
        if (t < afterPipes + turbineTicks) {
            int step = t - afterPipes;
            if (step % TURBINE_INTERVAL == 0) {
                int index = step / TURBINE_INTERVAL;
                if (index < stage.turbines().size()) {
                    blast(level, stage.turbines().get(index), POWER_TURBINE * scale, true);
                }
            }
            return false;
        }

        int coreAt = afterPipes + turbineTicks + CORE_DELAY;

        // ---- the core ------------------------------------------------------
        if (t == coreAt) {
            // The rods are removed first so the blast is not spent chewing through three hundred
            // hand-placed 6x6 blocks it would clear anyway.
            for (BlockPos at : stage.rods()) {
                level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
            for (BlockPos at : stage.rods()) {
                BlockPos lid = at.above();
                if (level.getBlockState(lid).getBlock() instanceof SealingBlock) {
                    level.setBlock(lid, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
            blast(level, stage.heart(), POWER_CORE * scale, true);
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

    /**
     * The block lists this meltdown runs against, found once and remembered.
     *
     * <p>Also the moment the two counts are frozen onto the reactor's saved state, if they were not
     * frozen already. After a restart mid-sequence the lists are rebuilt from whatever survived,
     * but the counts come back off disk, so every phase boundary lands exactly where it did before
     * the world was closed.
     */
    private static Stage stageFor(ServerLevel level, Structure plant, ReactorState state) {
        Stage known = STAGES.get(plant.id());
        if (known != null) {
            return known;
        }
        ReactorSurvey survey = ReactorSurvey.of(level, plant.min(), plant.max());
        List<BlockPos> rods = List.copyOf(survey.rodBlocks());
        Stage stage = new Stage(plumbing(level, plant, survey),
                List.copyOf(survey.turbines()), rods, centroid(rods, centre(plant)));
        if (state.meltdownPipes < 0) {
            state.meltdownPipes = stage.pipes().size();
            state.meltdownTurbines = stage.turbines().size();
        }
        STAGES.put(plant.id(), stage);
        return stage;
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
    /** Whether this one structure is mid-meltdown, so nothing can quietly unregister it. */
    public static boolean melting(MinecraftServer server, UUID structureId) {
        ReactorData data = ReactorData.get(server);
        return data.known(structureId) && data.of(structureId).melting();
    }

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
