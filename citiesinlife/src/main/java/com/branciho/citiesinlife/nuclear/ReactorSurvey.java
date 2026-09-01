package com.branciho.citiesinlife.nuclear;

import com.branciho.citiesinlife.block.BoilerBlock;
import com.branciho.citiesinlife.block.ControlRodBlock;
import com.branciho.citiesinlife.block.CoolingPortBlock;
import com.branciho.citiesinlife.block.FuelRodBlock;
import com.branciho.citiesinlife.block.MainMonitorBlock;
import com.branciho.citiesinlife.block.NuclearTurbineBlock;
import com.branciho.citiesinlife.block.PressurizedPipeBlock;
import com.branciho.citiesinlife.block.ReactorLeverBlock;
import com.branciho.citiesinlife.block.ReactorRodBlock;
import com.branciho.citiesinlife.block.SealingBlock;
import com.branciho.citiesinlife.block.SteamEmitterBlock;
import com.branciho.citiesinlife.block.TurbineBlock;
import com.branciho.citiesinlife.block.UraniumStorageBlock;
import com.branciho.citiesinlife.block.WindmillBlock;
import com.branciho.citiesinlife.blockentity.SteamEmitterBlockEntity;
import com.branciho.citiesinlife.city.CityData;
import com.branciho.citiesinlife.scan.StructureScanner;
import com.branciho.citiesinlife.structure.Structure;
import com.branciho.citiesinlife.structure.StructureType;
import com.branciho.citiesinlife.water.WaterGrid;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What is inside a registered Nuclear Plant, and the twenty-odd reasons it might not be one.
 *
 * <p>The coal plant's equivalent asks three questions. This asks whether ten hand-placed columns are
 * all exactly the same height, all flooded, all capped, whether the ports exist and stand in the
 * right places, whether one specific hand-drawn link was made, whether a pressurised pipe sits in
 * one specific spot, and whether two separate runs of pipework actually go where they claim. Any of
 * those failing has to produce a sentence naming the block and the coordinates — the alternative is
 * a building that silently does nothing, which for a machine this large is indistinguishable from
 * the feature being broken.
 *
 * <p>Cached and keyed exactly like {@code PlantSurvey}, and for the same reason: every reactor block
 * answers "how is the plant doing" when it is clicked, and they would otherwise each walk the same
 * box. The dimension is part of the key because two plants at the same coordinates in different
 * worlds are not the same plant.
 */
public final class ReactorSurvey {

    private static final int MAX_VOLUME = StructureScanner.MAX_SURVEY_VOLUME;

    /** Four fuel columns per turbine. Fixed by the owner and not negotiable. */
    public static final int FUEL_COLUMNS_PER_TURBINE = 4;

    /** Four control columns for the whole core, whatever the turbine count. Also fixed. */
    public static final int CONTROL_COLUMNS = 4;

    /** More than this and the box walk stops being worth doing. */
    public static final int MAX_TURBINES = 4;

    private static final int CACHE_TICKS = 20;
    private static final int MAX_CACHED = 256;

    private record CacheKey(ResourceKey<Level> dimension, long min, long max) { }

    private record Cached(long stamp, ReactorSurvey survey) { }

    private static final Map<CacheKey, Cached> CACHE = new HashMap<>();

    /**
     * One vertical run of rods.
     *
     * @param base   the lowest block of the run
     * @param height how many blocks tall it is
     * @param fuel   true for a fuel column, false for a control column
     */
    public record Column(BlockPos base, int height, boolean fuel) {

        public BlockPos top() {
            return base.above(height - 1);
        }
    }

    // ---- what was found ---------------------------------------------------

    private final List<Column> fuelColumns;
    private final List<Column> controlColumns;
    private final List<BlockPos> turbines;
    private final List<BlockPos> rodBlocks;
    private final List<BlockPos> steamEmitters;
    private final List<BlockPos> pressurizedPipes;
    private final List<BlockPos> alarms;
    private final Map<CoolingPort, BlockPos> ports;
    private final Map<ReactorLever, BlockPos> levers;
    private final @Nullable BlockPos uraniumStore;
    private final @Nullable BlockPos monitor;
    private final @Nullable BlockPos foreignMachine;
    private final int columnHeight;
    private final boolean registered;
    private final boolean submerged;
    private final @Nullable BlockPos dryRod;
    private final @Nullable BlockPos unsealedColumn;
    private final @Nullable BlockPos oddColumn;
    private final int oddColumnHeight;

    private static final ReactorSurvey NOTHING = new ReactorSurvey(
            List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
            new EnumMap<>(CoolingPort.class), new EnumMap<>(ReactorLever.class),
            null, null, null, 0, false, false, null, null, null, 0);

    private ReactorSurvey(List<Column> fuelColumns, List<Column> controlColumns,
                          List<BlockPos> turbines, List<BlockPos> rodBlocks,
                          List<BlockPos> steamEmitters, List<BlockPos> pressurizedPipes,
                          List<BlockPos> alarms,
                          Map<CoolingPort, BlockPos> ports, Map<ReactorLever, BlockPos> levers,
                          @Nullable BlockPos uraniumStore, @Nullable BlockPos monitor,
                          @Nullable BlockPos foreignMachine, int columnHeight, boolean registered,
                          boolean submerged, @Nullable BlockPos dryRod,
                          @Nullable BlockPos unsealedColumn, @Nullable BlockPos oddColumn,
                          int oddColumnHeight) {
        this.fuelColumns = fuelColumns;
        this.controlColumns = controlColumns;
        this.turbines = turbines;
        this.rodBlocks = rodBlocks;
        this.steamEmitters = steamEmitters;
        this.pressurizedPipes = pressurizedPipes;
        this.alarms = alarms;
        this.ports = ports;
        this.levers = levers;
        this.uraniumStore = uraniumStore;
        this.monitor = monitor;
        this.foreignMachine = foreignMachine;
        this.columnHeight = columnHeight;
        this.registered = registered;
        this.submerged = submerged;
        this.dryRod = dryRod;
        this.unsealedColumn = unsealedColumn;
        this.oddColumn = oddColumn;
        this.oddColumnHeight = oddColumnHeight;
    }

    public static void forgetAll() {
        CACHE.clear();
    }

    /** Survey the reactor a block at this position belongs to. */
    public static ReactorSurvey at(Level level, BlockPos pos) {
        MinecraftServer server = level.getServer();
        if (server == null) {
            return NOTHING;
        }
        Structure plant = CityData.get(server).structureAt(level.dimension(), pos);
        if (plant == null || plant.type() != StructureType.NUCLEAR_PLANT) {
            return NOTHING;
        }
        return of(level, plant.min(), plant.max());
    }

    /** Survey an arbitrary box, so a selection can be checked before it is registered. */
    public static ReactorSurvey of(Level level, BlockPos min, BlockPos max) {
        CacheKey key = new CacheKey(level.dimension(), min.asLong(), max.asLong());
        long now = level.getGameTime();
        Cached cached = CACHE.get(key);
        if (cached != null && now - cached.stamp() < CACHE_TICKS && now >= cached.stamp()) {
            return cached.survey();
        }
        ReactorSurvey survey = walk(level, min, max);
        if (CACHE.size() >= MAX_CACHED) {
            CACHE.clear();
        }
        CACHE.put(key, new Cached(now, survey));
        return survey;
    }

    private static ReactorSurvey walk(Level level, BlockPos min, BlockPos max) {
        long volume = (long) (max.getX() - min.getX() + 1)
                * (max.getY() - min.getY() + 1)
                * (max.getZ() - min.getZ() + 1);
        if (volume > MAX_VOLUME) {
            // Registered but unreadable. Reported as its own fault rather than as an empty box,
            // because "no fuel rods found" inside a reactor full of fuel rods is a lie.
            return new ReactorSurvey(List.of(), List.of(), List.of(), List.of(), List.of(),
                    List.of(), List.of(), new EnumMap<>(CoolingPort.class),
                    new EnumMap<>(ReactorLever.class), null, null, null, 0, true, false,
                    null, null, null, -1);
        }

        List<BlockPos> rods = new ArrayList<>();
        List<BlockPos> turbines = new ArrayList<>();
        List<BlockPos> emitters = new ArrayList<>();
        List<BlockPos> pressurized = new ArrayList<>();
        List<BlockPos> alarms = new ArrayList<>();
        Map<CoolingPort, BlockPos> ports = new EnumMap<>(CoolingPort.class);
        Map<ReactorLever, BlockPos> levers = new EnumMap<>(ReactorLever.class);
        BlockPos store = null;
        BlockPos monitor = null;
        BlockPos foreign = null;
        BlockPos dry = null;
        boolean submerged = true;

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = min.getY(); y <= max.getY(); y++) {
            for (int z = min.getZ(); z <= max.getZ(); z++) {
                for (int x = min.getX(); x <= max.getX(); x++) {
                    cursor.set(x, y, z);
                    BlockState state = level.getBlockState(cursor);
                    Block block = state.getBlock();

                    if (block instanceof ReactorRodBlock) {
                        rods.add(cursor.immutable());
                        if (!ReactorRodBlock.submerged(state)) {
                            submerged = false;
                            if (dry == null) {
                                dry = cursor.immutable();
                            }
                        }
                    } else if (block instanceof NuclearTurbineBlock) {
                        turbines.add(cursor.immutable());
                    } else if (block instanceof UraniumStorageBlock) {
                        if (store == null) {
                            store = cursor.immutable();
                        }
                    } else if (block instanceof CoolingPortBlock portBlock) {
                        ports.putIfAbsent(portBlock.port(), cursor.immutable());
                    } else if (block instanceof ReactorLeverBlock leverBlock) {
                        levers.putIfAbsent(leverBlock.lever(), cursor.immutable());
                    } else if (block instanceof SteamEmitterBlock) {
                        emitters.add(cursor.immutable());
                    } else if (block instanceof PressurizedPipeBlock) {
                        pressurized.add(cursor.immutable());
                    } else if (block instanceof MainMonitorBlock) {
                        if (monitor == null) {
                            monitor = cursor.immutable();
                        }
                    } else if (block instanceof com.branciho.citiesinlife.block.AlarmBlock) {
                        alarms.add(cursor.immutable());
                    } else if (foreign == null
                            && (block instanceof BoilerBlock || block instanceof WindmillBlock
                                || block instanceof TurbineBlock)) {
                        // Coal or wind machinery inside a reactor is two plants somebody forgot to
                        // draw separately. Caught here rather than left to behave unpredictably.
                        foreign = cursor.immutable();
                    }
                }
            }
        }

        // ---- rods into columns ------------------------------------------------
        List<Column> fuel = new ArrayList<>();
        List<Column> control = new ArrayList<>();
        for (BlockPos rod : rods) {
            BlockState state = level.getBlockState(rod);
            boolean isFuel = state.getBlock() instanceof FuelRodBlock;
            // A column is walked from its foot, and a foot is a rod with no rod of the same kind
            // beneath it. Splitting by kind means a stack that changes from fuel to control halfway
            // up becomes two short columns and fails the height check by name, rather than needing
            // a special rule nobody would ever read.
            if (sameKind(level, rod.below(), isFuel)) {
                continue;
            }
            int height = 1;
            while (sameKind(level, rod.above(height), isFuel)) {
                height++;
            }
            Column column = new Column(rod, height, isFuel);
            (isFuel ? fuel : control).add(column);
        }
        fuel.sort(Comparator.comparingLong(c -> c.base().asLong()));
        control.sort(Comparator.comparingLong(c -> c.base().asLong()));

        // ---- height agreement -------------------------------------------------
        List<Column> all = new ArrayList<>(fuel);
        all.addAll(control);
        int height = all.isEmpty() ? 0 : all.get(0).height();
        BlockPos odd = null;
        int oddHeight = 0;
        for (Column column : all) {
            if (column.height() != height) {
                odd = column.base();
                oddHeight = column.height();
                break;
            }
        }

        // ---- sealing ----------------------------------------------------------
        BlockPos unsealed = null;
        for (Column column : all) {
            if (!(level.getBlockState(column.top().above()).getBlock() instanceof SealingBlock)) {
                unsealed = column.base();
                break;
            }
        }

        return new ReactorSurvey(List.copyOf(fuel), List.copyOf(control), List.copyOf(turbines),
                List.copyOf(rods), List.copyOf(emitters), List.copyOf(pressurized),
                List.copyOf(alarms), ports, levers, store, monitor, foreign, height, true,
                submerged, dry, unsealed, odd, oddHeight);
    }

    private static boolean sameKind(Level level, BlockPos pos, boolean fuel) {
        Block block = level.getBlockState(pos).getBlock();
        return fuel ? block instanceof FuelRodBlock : block instanceof ControlRodBlock;
    }

    // ---- what was found ---------------------------------------------------

    public boolean registered() {
        return registered;
    }

    public List<Column> fuelColumns() {
        return fuelColumns;
    }

    public List<Column> controlColumns() {
        return controlColumns;
    }

    public List<BlockPos> turbines() {
        return turbines;
    }

    public List<BlockPos> rodBlocks() {
        return rodBlocks;
    }

    public List<BlockPos> alarms() {
        return alarms;
    }

    public List<BlockPos> pressurizedPipes() {
        return pressurizedPipes;
    }

    public Map<CoolingPort, BlockPos> ports() {
        return ports;
    }

    public Map<ReactorLever, BlockPos> levers() {
        return levers;
    }

    public @Nullable BlockPos uraniumStore() {
        return uraniumStore;
    }

    public @Nullable BlockPos monitor() {
        return monitor;
    }

    /** Every column is this tall, once the survey has agreed they all are. */
    public int columnHeight() {
        return columnHeight;
    }

    /** Height actually counted toward output. Building past the cap is allowed, just not rewarded. */
    public int effectiveHeight() {
        return Math.min(columnHeight, ReactorRodBlock.MAX_HEIGHT);
    }

    public boolean heightCapped() {
        return columnHeight > ReactorRodBlock.MAX_HEIGHT;
    }

    /** Fuel rod blocks counted toward output: four columns per turbine, capped in height. */
    public int fuelRodBlocks() {
        return FUEL_COLUMNS_PER_TURBINE * turbines.size() * effectiveHeight();
    }

    public boolean submerged() {
        return submerged;
    }

    public @Nullable BlockPos dryRod() {
        return dryRod;
    }

    /** Whether a steam emitter exists and has somewhere to vent. Blocked counts as missing. */
    public boolean steamEmitterWorking(Level level) {
        for (BlockPos emitter : steamEmitters) {
            if (SteamEmitterBlockEntity.clear(level, emitter)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasSteamEmitter() {
        return !steamEmitters.isEmpty();
    }

    public List<BlockPos> steamEmitters() {
        return steamEmitters;
    }

    // ---- is this a reactor at all -----------------------------------------

    /**
     * The first reason this is not a working reactor, or null.
     *
     * <p>Fixed order, worst first, and only the faults that mean it is not a reactor at all. The
     * running faults — a fouled port, a dry core, a broken loop — are the simulation's business,
     * because those are states a reactor is genuinely in rather than reasons it is not one.
     */
    public @Nullable ReactorFault buildFault() {
        if (!registered) {
            return ReactorFault.NOT_REGISTERED;
        }
        if (oddColumnHeight < 0) {
            return ReactorFault.BOX_TOO_LARGE;
        }
        if (foreignMachine != null) {
            return ReactorFault.MIXED_PLANT;
        }
        if (turbines.isEmpty()) {
            return ReactorFault.NO_TURBINE;
        }
        if (turbines.size() > MAX_TURBINES) {
            return ReactorFault.TOO_MANY_TURBINES;
        }
        if (fuelColumns.isEmpty()) {
            return ReactorFault.NO_FUEL_RODS;
        }
        if (fuelColumns.size() != FUEL_COLUMNS_PER_TURBINE * turbines.size()) {
            return ReactorFault.FUEL_COLUMN_COUNT;
        }
        if (controlColumns.size() != CONTROL_COLUMNS) {
            return ReactorFault.CONTROL_COLUMN_COUNT;
        }
        if (columnHeight < ReactorRodBlock.MIN_HEIGHT) {
            return ReactorFault.COLUMN_TOO_SHORT;
        }
        if (oddColumn != null) {
            return ReactorFault.COLUMNS_UNEVEN;
        }
        if (!submerged) {
            return ReactorFault.NOT_SUBMERGED;
        }
        if (unsealedColumn != null) {
            return ReactorFault.NOT_SEALED;
        }
        if (uraniumStore == null) {
            return ReactorFault.NO_URANIUM_STORE;
        }
        for (CoolingPort port : CoolingPort.values()) {
            if (!ports.containsKey(port)) {
                return ReactorFault.PORT_MISSING;
            }
        }
        for (CoolingPort port : CoolingPort.values()) {
            if (port.beside() && !ports.get(port).closerThan(uraniumStore, 1.9D)) {
                return ReactorFault.PORT_NOT_BESIDE_STORE;
            }
        }
        if (pressurizedPipes.isEmpty() || !pressurizedAgainstCooledInput()) {
            return ReactorFault.NO_PRESSURIZED_PIPE;
        }
        return null;
    }

    /** Which block a build fault is complaining about, so the message can name a position. */
    public @Nullable BlockPos faultAt(ReactorFault fault) {
        return switch (fault) {
            case MIXED_PLANT -> foreignMachine;
            case COLUMN_TOO_SHORT -> shortestColumn();
            case COLUMNS_UNEVEN -> oddColumn;
            case NOT_SUBMERGED -> dryRod;
            case NOT_SEALED -> unsealedColumn;
            case NO_PRESSURIZED_PIPE, PORT_NOT_LINKED -> ports.get(CoolingPort.INPUT_COOLED);
            case PORT_NOT_BESIDE_STORE -> uraniumStore;
            default -> null;
        };
    }

    public int oddColumnHeight() {
        return oddColumnHeight;
    }

    private @Nullable BlockPos shortestColumn() {
        Column shortest = null;
        for (Column column : fuelColumns) {
            if (shortest == null || column.height() < shortest.height()) {
                shortest = column;
            }
        }
        for (Column column : controlColumns) {
            if (shortest == null || column.height() < shortest.height()) {
                shortest = column;
            }
        }
        return shortest == null ? null : shortest.base();
    }

    /** The pressurised pipe has to be the last thing on the cooled run, touching the port. */
    private boolean pressurizedAgainstCooledInput() {
        BlockPos cooled = ports.get(CoolingPort.INPUT_COOLED);
        if (cooled == null) {
            return false;
        }
        for (BlockPos pipe : pressurizedPipes) {
            if (pipe.distManhattan(cooled) == 1) {
                return true;
            }
        }
        return false;
    }

    // ---- the loop ---------------------------------------------------------

    /**
     * Whether the cooling circuit is actually a circuit.
     *
     * <p>Four separate questions, each of which can be answered with an instruction: was the one
     * hand-drawn link made, does the cooled run go where it says, does the return run come back,
     * and is there any water in it at all.
     */
    public @Nullable ReactorFault loopFault(ServerLevel level) {
        BlockPos in = ports.get(CoolingPort.INPUT_WATER);
        BlockPos cooledOut = ports.get(CoolingPort.OUTPUT_COOLED);
        BlockPos cooledIn = ports.get(CoolingPort.INPUT_COOLED);
        BlockPos heatedOut = ports.get(CoolingPort.OUTPUT_HEATED);
        if (in == null || cooledOut == null || cooledIn == null || heatedOut == null) {
            return ReactorFault.PORT_MISSING;
        }
        WaterGrid grid = WaterGrid.get(level.getServer());
        if (!grid.linked(level.dimension(), in, cooledOut)) {
            return ReactorFault.PORT_NOT_LINKED;
        }
        if (!grid.connected(level, cooledOut, cooledIn)) {
            return ReactorFault.COOLED_RUN_BROKEN;
        }
        if (!grid.connected(level, heatedOut, in)) {
            return ReactorFault.RETURN_RUN_BROKEN;
        }
        return null;
    }
}
