package com.branciho.livingcities.scan;

import com.branciho.livingcities.LivingCities;
import com.branciho.livingcities.block.EntranceMarkerBlock;
import com.branciho.livingcities.building.Building;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * One building scan, as a state machine driven a slice at a time from the server tick.
 *
 * <p>Reading half a million blocks in one tick would stall the server for the better part of a
 * second, so the snapshot is spread across as many ticks as the per-tick budget needs. Everything up
 * to and including block profiling happens on the server thread; only the arithmetic afterwards is
 * handed to a worker.
 *
 * <p>The hand-off is the interesting part. The worker receives a {@link BuildingSnapshot} - short
 * indices, ints, and immutable records - and returns a {@link FloorPlan} of the same. It never sees
 * the level, and this class never registers a completion callback on the future, because a callback
 * would run on the worker's thread. Instead {@link BuildingScanService} polls {@link #phase()} from
 * the tick loop, which makes "results are applied on the server thread" a property of the control
 * flow rather than a comment someone has to keep honouring.
 */
public final class BuildingScanTask {

    /** Where a scan is up to. */
    public enum Phase {
        /** Copying blocks out of the world into the snapshot, budgeted per tick. */
        SNAPSHOT,
        /** Classifying each distinct block state. Small: hundreds of entries, not hundreds of thousands. */
        PROFILE,
        /** Waiting on the worker. Nothing happens on the server thread. */
        ANALYZE,
        DONE,
        FAILED
    }

    /**
     * A short index caps the palette, which is the right trade: a selection with more than 65k
     * distinct block states is not a building, and the alternative costs twice the memory on every
     * snapshot to serve a case that does not occur.
     */
    private static final int MAX_PALETTE = 65_535;

    /** Profiling one state is far dearer than reading one block, so it draws more of the budget. */
    private static final int PROFILE_BUDGET_COST = 16;

    private static final int PROFILE_ENTRIES_PER_STEP = 128;

    /** Two samples of a state have to be this far apart to say anything about position sensitivity. */
    private static final double SAMPLE_SEPARATION_SQR = 64.0D;

    /** Perimeter probes per side used to estimate the surrounding ground level. */
    private static final int GRADE_SAMPLES_PER_SIDE = 4;

    private final ServerLevel level;
    private final Building building;
    private final @Nullable UUID requesterId;
    private final ScanSettings settings;

    private final int minX;
    private final int minY;
    private final int minZ;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;

    private final int regMinX;
    private final int regMinY;
    private final int regMinZ;
    private final int regMaxX;
    private final int regMaxY;
    private final int regMaxZ;

    private final short[] cells;
    private final List<BlockState> palette = new ArrayList<>();
    private final List<BlockPos> firstSample = new ArrayList<>();
    private final List<BlockPos> secondSample = new ArrayList<>();
    /**
     * Identity-keyed on purpose: block states are canonical singletons from a registry frozen before
     * the world loads, so reference equality is both correct and much cheaper than {@code equals}.
     */
    private final Reference2IntOpenHashMap<BlockState> paletteIndex = new Reference2IntOpenHashMap<>();

    private Phase phase = Phase.SNAPSHOT;
    private String failureKey = "";

    private int columnCursor;
    private int profileCursor;
    private BlockProfile[] profiles;
    private int gradeY;
    private int unknownStates;
    private int positionSensitiveStates;

    private @Nullable LevelChunk cachedChunk;
    private int cachedChunkX = Integer.MIN_VALUE;
    private int cachedChunkZ = Integer.MIN_VALUE;

    private @Nullable CompletableFuture<FloorPlan> analysis;

    /** A door every few blocks around a tower is plenty; the cap stops a griefy build eating memory. */
    private static final int MAX_ENTRANCES = 64;

    private final List<BlockPos> entrances = new ArrayList<>();
    private @Nullable FloorPlan plan;

    BuildingScanTask(ServerLevel level, Building building, @Nullable UUID requesterId, ScanSettings settings) {
        this.level = level;
        this.building = building;
        this.requesterId = requesterId;
        this.settings = settings;

        final int worldMinY = level.getMinBuildHeight();
        final int worldMaxY = level.getMaxBuildHeight() - 1;
        final int buildingMinY = Math.max(worldMinY, building.min().getY());
        final int buildingMaxY = Math.min(worldMaxY, building.max().getY());

        // The collar around the selection is what gives the enclosure fill somewhere outdoors to
        // start from. Without it a tight selection reports the whole world as enclosed.
        this.minX = building.min().getX() - settings.marginXZ();
        this.minZ = building.min().getZ() - settings.marginXZ();
        this.minY = Math.max(worldMinY, buildingMinY - settings.marginBelow());
        final int maxX = building.max().getX() + settings.marginXZ();
        final int maxZ = building.max().getZ() + settings.marginXZ();
        final int maxY = Math.min(worldMaxY, buildingMaxY + settings.marginAbove());

        this.sizeX = maxX - minX + 1;
        this.sizeY = maxY - minY + 1;
        this.sizeZ = maxZ - minZ + 1;

        this.regMinX = building.min().getX() - minX;
        this.regMinZ = building.min().getZ() - minZ;
        this.regMinY = buildingMinY - minY;
        this.regMaxX = building.max().getX() - minX;
        this.regMaxZ = building.max().getZ() - minZ;
        this.regMaxY = buildingMaxY - minY;

        this.cells = new short[sizeX * sizeY * sizeZ];
        this.paletteIndex.defaultReturnValue(-1);
    }

    // ------------------------------------------------------------------ driving the machine

    /**
     * Advance the scan. <b>Server thread only.</b>
     *
     * @param budget how many block reads this step may spend
     * @return how much of the budget was used, so the caller can share a tick between tasks
     */
    int step(int budget) {
        return switch (phase) {
            case SNAPSHOT -> stepSnapshot(budget);
            case PROFILE -> stepProfile();
            case ANALYZE -> stepAnalyze();
            case DONE, FAILED -> 0;
        };
    }

    /** Entrance markers found inside the snapshot, in world coordinates. */
    public List<BlockPos> entrances() {
        return entrances;
    }

    private int stepSnapshot(int budget) {
        final int columns = sizeX * sizeZ;
        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int spent = 0;

        while (spent < budget && columnCursor < columns) {
            final int x = columnCursor % sizeX;
            final int z = columnCursor / sizeX;
            final int worldX = minX + x;
            final int worldZ = minZ + z;
            final int chunkX = worldX >> 4;
            final int chunkZ = worldZ >> 4;

            if (cachedChunk == null || chunkX != cachedChunkX || chunkZ != cachedChunkZ) {
                // Check before fetching: Level#getChunk would happily block the server generating
                // terrain, and a scan triggered from a UI click must never do that.
                if (!level.isLoaded(cursor.set(worldX, minY, worldZ))) {
                    fail("message.livingcities.scan_chunks_unloaded");
                    return spent;
                }
                cachedChunk = level.getChunk(chunkX, chunkZ);
                cachedChunkX = chunkX;
                cachedChunkZ = chunkZ;
            }

            final int base = BuildingSnapshot.index(x, 0, z, sizeX, sizeY);
            for (int y = 0; y < sizeY; y++) {
                cursor.set(worldX, minY + y, worldZ);
                final BlockState state = cachedChunk.getBlockState(cursor);
                int index = paletteIndex.getInt(state);
                if (index < 0) {
                    if (palette.size() >= MAX_PALETTE) {
                        fail("message.livingcities.scan_too_complex");
                        return spent;
                    }
                    index = palette.size();
                    palette.add(state);
                    firstSample.add(cursor.immutable());
                    secondSample.add(null);
                    paletteIndex.put(state, index);
                } else if (secondSample.get(index) == null
                        && firstSample.get(index).distSqr(cursor) >= SAMPLE_SEPARATION_SQR) {
                    secondSample.set(index, cursor.immutable());
                }
                cells[base + y] = (short) index;

                // Free ride on a walk we are already doing: entrance markers placed before the
                // building was registered would otherwise never be noticed.
                if (entrances.size() < MAX_ENTRANCES
                        && state.getBlock() instanceof EntranceMarkerBlock) {
                    entrances.add(cursor.immutable());
                }
            }

            spent += sizeY;
            columnCursor++;
        }

        if (columnCursor >= columns) {
            sampleGradeLevel();
            phase = Phase.PROFILE;
        }
        return spent;
    }

    /**
     * Estimate the ground level around the building so basements can be told from ground floors.
     *
     * <p>A median of perimeter probes rather than a single sample: one probe landing on a tree, a
     * lamppost or the player's own scaffolding would move the basement line by ten blocks.
     */
    private void sampleGradeLevel() {
        final int westX = minX + regMinX;
        final int eastX = minX + regMaxX;
        final int northZ = minZ + regMinZ;
        final int southZ = minZ + regMaxZ;
        final int[] samples = new int[GRADE_SAMPLES_PER_SIDE * 4];
        int count = 0;

        for (int i = 0; i < GRADE_SAMPLES_PER_SIDE; i++) {
            final double t = (i + 0.5D) / GRADE_SAMPLES_PER_SIDE;
            final int x = westX + (int) ((eastX - westX) * t);
            final int z = northZ + (int) ((southZ - northZ) * t);
            samples[count++] = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, northZ - 1);
            samples[count++] = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, southZ + 1);
            samples[count++] = level.getHeight(Heightmap.Types.WORLD_SURFACE, westX - 1, z);
            samples[count++] = level.getHeight(Heightmap.Types.WORLD_SURFACE, eastX + 1, z);
        }
        Arrays.sort(samples, 0, count);
        gradeY = count == 0 ? minY : samples[count / 2];
    }

    private int stepProfile() {
        if (profiles == null) {
            profiles = new BlockProfile[palette.size()];
        }
        final int end = Math.min(palette.size(), profileCursor + PROFILE_ENTRIES_PER_STEP);
        final int entries = end - profileCursor;

        for (; profileCursor < end; profileCursor++) {
            final BlockState state = palette.get(profileCursor);
            BlockProfile profile;
            try {
                profile = BlockProfiler.profile(level, firstSample.get(profileCursor), state);
                final BlockPos other = secondSample.get(profileCursor);
                if (other != null) {
                    // A handful of modded blocks derive collision from a BlockEntity rather than from
                    // the state. Profiling the same state twice, far apart, is how we find out.
                    final BlockProfile elsewhere = BlockProfiler.profile(level, other, state);
                    if (!profile.geometryEquals(elsewhere)) {
                        profile = profile.withPositionSensitive(true);
                        positionSensitiveStates++;
                    }
                }
            } catch (Throwable failure) {
                // Not paranoia: getCollisionShape on a badly written modded block does throw. One bad
                // block must degrade one cell's classification, never kill the scan or the server.
                LivingCities.LOGGER.warn("Living Cities: {} threw while being profiled; treating it as unknown",
                        BuiltInRegistries.BLOCK.getKey(state.getBlock()), failure);
                profile = BlockProfile.unknown();
            }
            if (profile.blockClass() == BlockClass.UNKNOWN) {
                unknownStates++;
            }
            profiles[profileCursor] = profile;
        }

        if (profileCursor >= palette.size()) {
            startAnalysis();
        }
        return entries * PROFILE_BUDGET_COST;
    }

    private void startAnalysis() {
        final BuildingSnapshot snapshot = new BuildingSnapshot(
                minX, minY, minZ,
                sizeX, sizeY, sizeZ,
                regMinX, regMinY, regMinZ,
                regMaxX, regMaxY, regMaxZ,
                cells, profiles);
        final int grade = gradeY;
        final ScanSettings config = settings;
        analysis = CompletableFuture.supplyAsync(
                () -> FloorAnalyzer.analyze(snapshot, grade, config), Util.backgroundExecutor());
        phase = Phase.ANALYZE;
    }

    private int stepAnalyze() {
        final CompletableFuture<FloorPlan> future = analysis;
        if (future == null || !future.isDone()) {
            return 0;
        }
        try {
            // join() on a completed future does not block; this is a poll, not a wait.
            plan = future.join();
            phase = Phase.DONE;
        } catch (RuntimeException e) {
            LivingCities.LOGGER.error("Living Cities: floor analysis failed for building {}", building.id(), e);
            fail("message.livingcities.scan_failed");
        }
        return 0;
    }

    private void fail(String translationKey) {
        this.failureKey = translationKey;
        this.phase = Phase.FAILED;
        this.cachedChunk = null;
    }

    /** Abandon this task; used when the service is torn down or a step threw. */
    void abort(String translationKey) {
        if (analysis != null) {
            analysis.cancel(false);
        }
        fail(translationKey);
    }

    // ------------------------------------------------------------------ accessors

    public Phase phase() {
        return phase;
    }

    public Building building() {
        return building;
    }

    public ServerLevel level() {
        return level;
    }

    public @Nullable UUID requesterId() {
        return requesterId;
    }

    public @Nullable FloorPlan plan() {
        return plan;
    }

    /** Translation key describing why the scan failed; empty while it has not. */
    public String failureKey() {
        return failureKey;
    }

    public int paletteSize() {
        return palette.size();
    }

    public int unknownStates() {
        return unknownStates;
    }

    /** Distinct states whose geometry disagreed between two sample positions. Normally zero. */
    public int positionSensitiveStates() {
        return positionSensitiveStates;
    }

    /** 0..1000, for a progress indicator. */
    public int progressPermille() {
        return switch (phase) {
            case SNAPSHOT -> {
                final int columns = sizeX * sizeZ;
                yield columns == 0 ? 0 : (int) (900L * columnCursor / columns);
            }
            case PROFILE -> 900 + (palette.isEmpty() ? 0 : 50 * profileCursor / palette.size());
            case ANALYZE -> 950;
            default -> 1000;
        };
    }
}
