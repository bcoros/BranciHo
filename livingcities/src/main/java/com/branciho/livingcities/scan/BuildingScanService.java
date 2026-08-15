package com.branciho.livingcities.scan;

import com.branciho.livingcities.LivingCities;
import com.branciho.livingcities.building.Building;
import com.branciho.livingcities.building.Floor;
import com.branciho.livingcities.city.CityRegistry;
import com.branciho.livingcities.config.LivingCitiesConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * The entry point for scanning buildings. Everything else in this package is an implementation
 * detail of what happens between {@link #enqueue} and the floors turning up on the {@link Building}.
 *
 * <p><b>Server thread only.</b> Every method here must be called from the server thread, and the
 * service enforces that by construction: the worker's result is <em>polled</em> from {@link #tick},
 * never delivered by a callback, so no code path exists that could mutate a building, send a packet
 * or mark save data dirty from anywhere else.
 *
 * <p>Two jobs run at once and no more. A scan is a burst of memory and main-thread time, and letting
 * a player with a fast pickaxe queue thirty concurrent scans is a far worse outcome than making the
 * thirtieth wait a few seconds.
 *
 * <h2>Wiring this up</h2>
 *
 * <pre>{@code
 * // game bus, ServerTickEvent.Post
 * BuildingScanService.get(event.getServer()).tick(event.getServer());
 *
 * // wherever a building is registered or a rescan is requested
 * BuildingScanService.get(server).enqueue(level, building, player);
 * }</pre>
 *
 * <p>Nothing else needs doing. On {@code ServerStoppingEvent}, call {@link #shutdown(MinecraftServer)}
 * so in-flight jobs are dropped rather than left holding a snapshot.
 */
public final class BuildingScanService {

    /** How many scans may be in flight. Deliberately small; see the class docs. */
    private static final int MAX_ACTIVE = 2;

    /** Beyond this the queue is refusing work rather than pretending it will get to it. */
    private static final int MAX_QUEUED = 32;

    private static final Map<MinecraftServer, BuildingScanService> INSTANCES = new WeakHashMap<>();

    private final Deque<BuildingScanTask> pending = new ArrayDeque<>();
    private final List<BuildingScanTask> active = new ArrayList<>();
    /** Building ids already queued or running, so a rescan spam-click cannot queue the same job twice. */
    private final Set<UUID> tracked = new HashSet<>();

    private ScanListener listener = BuildingScanService::markRegistryDirty;
    private ScanSettings settings = ScanSettings.DEFAULTS;

    private BuildingScanService() {
    }

    /**
     * The scan service for this server. Call on the server thread.
     *
     * <p>Keyed weakly so a server that has stopped - integrated server on a world quit, for instance -
     * does not keep its scan queue alive for the rest of the JVM's life.
     */
    public static BuildingScanService get(MinecraftServer server) {
        return INSTANCES.computeIfAbsent(server, ignored -> new BuildingScanService());
    }

    /**
     * Notified on the server thread once a scan has been applied to its building.
     *
     * <p>This exists so persistence and networking stay outside the scanner. The default marks the
     * {@link CityRegistry} dirty, because a scan that is not saved is a scan the player has to run
     * again after every restart; replace it if the integration layer needs to sync clients too.
     */
    @FunctionalInterface
    public interface ScanListener {
        void onScanComplete(ServerLevel level, Building building, FloorPlan plan);
    }

    public void setListener(@Nullable ScanListener listener) {
        this.listener = listener == null ? (level, building, plan) -> { } : listener;
    }

    /** Override the detection tunables, e.g. from a command for debugging a stubborn build. */
    public void setSettings(ScanSettings settings) {
        this.settings = settings;
    }

    public ScanSettings settings() {
        return settings;
    }

    // ------------------------------------------------------------------ public API

    /**
     * Queue {@code building} for scanning.
     *
     * <p>Every rejection reason is checked here rather than inside the task, so a bad request costs
     * microseconds instead of a snapshot allocation. A request that arrived from a client has already
     * been validated for permission by the packet handler; what is validated here is whether the
     * <em>server</em> can do the work at all.
     *
     * @param requester optional player to report the result to; the scan runs either way
     * @return true if the scan was queued
     */
    public boolean enqueue(ServerLevel level, Building building, @Nullable ServerPlayer requester) {
        if (tracked.contains(building.id())) {
            return false;
        }
        if (pending.size() + active.size() >= MAX_QUEUED) {
            deny(requester, "message.livingcities.scan_busy");
            return false;
        }

        final long volume = building.volume();
        if (volume <= 0L) {
            deny(requester, "message.livingcities.scan_empty");
            return false;
        }
        if (volume > LivingCitiesConfig.SERVER.maxSelectionVolume.get().longValue()) {
            deny(requester, "message.livingcities.scan_too_large");
            return false;
        }
        if (building.sizeY() > LivingCitiesConfig.SERVER.maxSelectionHeight.get()) {
            deny(requester, "message.livingcities.scan_too_tall");
            return false;
        }
        if (!chunksLoaded(level, building)) {
            deny(requester, "message.livingcities.scan_chunks_unloaded");
            return false;
        }

        pending.add(new BuildingScanTask(level, building,
                requester == null ? null : requester.getUUID(), settings));
        tracked.add(building.id());
        return true;
    }

    /** Drive every in-flight scan by one tick's worth of work. Call from {@code ServerTickEvent.Post}. */
    public void tick(MinecraftServer server) {
        while (active.size() < MAX_ACTIVE && !pending.isEmpty()) {
            active.add(pending.poll());
        }
        if (active.isEmpty()) {
            return;
        }

        // The configured budget is the whole server's allowance, shared out, so two concurrent scans
        // cost the same tick time as one.
        final int budget = Math.max(1, LivingCitiesConfig.SERVER.scanBlocksPerTick.get() / active.size());

        for (Iterator<BuildingScanTask> iterator = active.iterator(); iterator.hasNext(); ) {
            final BuildingScanTask task = iterator.next();
            try {
                task.step(budget);
            } catch (RuntimeException e) {
                LivingCities.LOGGER.error("Living Cities: scan of building {} threw",
                        task.building().id(), e);
                task.abort("message.livingcities.scan_failed");
            }

            switch (task.phase()) {
                case DONE -> {
                    complete(server, task);
                    tracked.remove(task.building().id());
                    iterator.remove();
                }
                case FAILED -> {
                    report(server, task.requesterId(), Component.translatable(task.failureKey())
                            .withStyle(ChatFormatting.RED));
                    tracked.remove(task.building().id());
                    iterator.remove();
                }
                default -> {
                    // still working
                }
            }
        }
    }

    /** Scans queued or running, for status displays and for a command to say "busy, try later". */
    public int queueSize() {
        return pending.size() + active.size();
    }

    /** Drop a queued or running scan, e.g. because its building was deleted. */
    public boolean cancel(UUID buildingId) {
        if (!tracked.remove(buildingId)) {
            return false;
        }
        pending.removeIf(task -> task.building().id().equals(buildingId));
        for (Iterator<BuildingScanTask> iterator = active.iterator(); iterator.hasNext(); ) {
            final BuildingScanTask task = iterator.next();
            if (task.building().id().equals(buildingId)) {
                task.abort("message.livingcities.scan_cancelled");
                iterator.remove();
            }
        }
        return true;
    }

    /** Drop everything. Call from {@code ServerStoppingEvent}. */
    public static void shutdown(MinecraftServer server) {
        final BuildingScanService service = INSTANCES.remove(server);
        if (service == null) {
            return;
        }
        for (BuildingScanTask task : service.active) {
            task.abort("message.livingcities.scan_cancelled");
        }
        service.active.clear();
        service.pending.clear();
        service.tracked.clear();
    }

    // ------------------------------------------------------------------ commit

    private void complete(MinecraftServer server, BuildingScanTask task) {
        final Building building = task.building();
        final FloorPlan plan = task.plan();
        if (plan == null) {
            return;
        }
        // The building may have been unregistered while the scan ran; committing to an orphan would
        // resurrect deleted data on the next save.
        if (CityRegistry.get(server).building(building.id()) == null) {
            LivingCities.LOGGER.debug("Living Cities: discarding scan for building {}, no longer registered",
                    building.id());
            return;
        }

        // Snapshot the old floors before replacing them: replaceFloors mutates the live list, and
        // inheritZoning needs the previous zoning to carry a mixed-use layout across a rescan.
        final List<Floor> previous = new ArrayList<>(building.floors());

        final List<Floor> detected = new ArrayList<>(plan.floors().size());
        for (DetectedFloor floor : plan.floors()) {
            detected.add(toFloor(floor));
        }

        building.replaceFloors(detected);
        building.inheritZoning(previous);

        // Entrances are re-derived rather than merged: a marker the player has since broken must not
        // survive as a doorway NPCs keep walking to. The snapshot is padded past the registered
        // bounds, so markers just outside the building are filtered out here.
        building.entrances().clear();
        for (BlockPos entrance : task.entrances()) {
            if (building.contains(entrance)) {
                building.addEntrance(entrance);
            }
        }

        building.markScanned(task.level().getGameTime());

        listener.onScanComplete(task.level(), building, plan);
        announce(server, task, plan);

        LivingCities.LOGGER.debug(
                "Living Cities: scanned {} - {} floors from {} candidates ({} rejected), "
                        + "{} palette entries, {} unknown, {} ms",
                building.name(), plan.floors().size(), plan.candidateCount(), plan.rejectedCount(),
                plan.paletteSize(), plan.unknownStates(), plan.analysisMillis());
    }

    private static Floor toFloor(DetectedFloor detected) {
        final Floor floor = new Floor(
                detected.floorY(),
                detected.yMin(),
                detected.yMax(),
                detected.usableCells(),
                detected.standCells(),
                detected.ceilingHeight());
        floor.flags().addAll(detected.flags());
        return floor;
    }

    private void announce(MinecraftServer server, BuildingScanTask task, FloorPlan plan) {
        final UUID requesterId = task.requesterId();
        if (requesterId == null) {
            return;
        }
        final Building building = task.building();

        if (plan.floors().isEmpty()) {
            report(server, requesterId, Component.translatable("message.livingcities.scan_no_floors",
                    building.name()).withStyle(ChatFormatting.YELLOW));
            return;
        }
        report(server, requesterId, Component.translatable("message.livingcities.scan_complete",
                building.name(), plan.floors().size(), plan.totalUsableCells())
                .withStyle(ChatFormatting.GREEN));

        // Surfaced rather than hidden: a leaky floor almost always means one missing block in a wall,
        // and a player who is told can fix it in seconds. A silently wrong number they cannot.
        if (plan.leakyFloorCount() > 0) {
            report(server, requesterId, Component.translatable("message.livingcities.scan_leaky",
                    plan.leakyFloorCount()).withStyle(ChatFormatting.YELLOW));
        }
        if (plan.unknownStates() > 0) {
            report(server, requesterId, Component.translatable("message.livingcities.scan_unknown_blocks",
                    plan.unknownStates()).withStyle(ChatFormatting.YELLOW));
        }
    }

    // ------------------------------------------------------------------ helpers

    /**
     * The default listener. {@code setDirty} is inherited from {@code SavedData} and is public, but
     * routing it through the listener keeps persistence a decision the integration layer owns rather
     * than something the scanner does behind its back.
     */
    private static void markRegistryDirty(ServerLevel level, Building building, FloorPlan plan) {
        final MinecraftServer server = level.getServer();
        if (server != null) {
            CityRegistry.get(server).setDirty();
        }
    }

    private boolean chunksLoaded(ServerLevel level, Building building) {
        final int margin = settings.marginXZ();
        final int minChunkX = (building.min().getX() - margin) >> 4;
        final int maxChunkX = (building.max().getX() + margin) >> 4;
        final int minChunkZ = (building.min().getZ() - margin) >> 4;
        final int maxChunkZ = (building.max().getZ() + margin) >> 4;
        final BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        final int probeY = building.min().getY();

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (!level.isLoaded(probe.set(chunkX << 4, probeY, chunkZ << 4))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void deny(@Nullable ServerPlayer player, String translationKey) {
        if (player != null) {
            player.displayClientMessage(
                    Component.translatable(translationKey).withStyle(ChatFormatting.RED), true);
        }
    }

    /**
     * Re-resolve the requester by id rather than holding the {@code ServerPlayer}: a scan outlives a
     * disconnect, and a stale player reference is both a leak and a crash waiting for a null check
     * nobody wrote.
     */
    private static void report(MinecraftServer server, @Nullable UUID requesterId, Component message) {
        if (requesterId == null) {
            return;
        }
        final ServerPlayer player = server.getPlayerList().getPlayer(requesterId);
        if (player != null) {
            player.displayClientMessage(message, false);
        }
    }
}
