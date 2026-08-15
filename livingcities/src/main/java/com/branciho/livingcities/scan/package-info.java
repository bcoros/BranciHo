/**
 * Building scanning: turning an arbitrary player-built volume into a list of floors with a usable
 * area figure attached to each one.
 *
 * <h2>Why this package is split the way it is</h2>
 *
 * <p>The expensive Minecraft calls here are {@code O(distinct BlockStates)}, not {@code O(blocks)}.
 * A 64x128x64 selection holds half a million cells but only a few hundred distinct states. So the
 * work is separated into two levels:
 *
 * <ul>
 *   <li><b>Intrinsic classification</b> ({@link com.branciho.livingcities.scan.BlockProfiler}) asks
 *       Minecraft about a {@code BlockState} once per distinct state, at a real position where that
 *       state actually occurs, and boils the answer down to a
 *       {@link com.branciho.livingcities.scan.BlockProfile} of primitives.</li>
 *   <li><b>Cell predicates</b> (the methods on {@code BlockProfile}) and the whole floor-detection
 *       algorithm ({@link com.branciho.livingcities.scan.FloorAnalyzer}) are pure integer and float
 *       arithmetic over arrays. They never call into Minecraft at all.</li>
 * </ul>
 *
 * <h2>Threading contract</h2>
 *
 * <p>Read this before touching anything in here.
 *
 * <ul>
 *   <li>{@link com.branciho.livingcities.scan.BlockProfiler} and
 *       {@link com.branciho.livingcities.scan.BuildingScanTask} touch {@code ServerLevel},
 *       {@code LevelChunk} and {@code BlockState} geometry methods. They are <b>server-thread
 *       only</b>. Never call them from anywhere else.</li>
 *   <li>{@link com.branciho.livingcities.scan.BuildingSnapshot} deliberately contains no Minecraft
 *       object of any kind - only {@code short[]}, {@code int}s and an array of the immutable
 *       {@code BlockProfile} record. That is the entire reason it exists: it is the hand-off value
 *       that can safely cross a thread boundary. Do not add a {@code BlockState},
 *       {@code BlockPos}, {@code Level} or {@code BlockEntity} field to it. Holding a
 *       {@code BlockState} reference off-thread would actually be harmless (they are frozen
 *       registry singletons), but calling a method on one that takes a {@code BlockGetter} is not,
 *       and the only way to make that mistake impossible is to not have the reference.</li>
 *   <li>{@link com.branciho.livingcities.scan.FloorAnalyzer} runs on a background worker. It
 *       receives a snapshot, some ints and a {@link com.branciho.livingcities.scan.ScanSettings},
 *       and returns a {@link com.branciho.livingcities.scan.FloorPlan} of plain values. It must
 *       stay free of imports from {@code net.minecraft.world.level} and
 *       {@code net.minecraft.server}. Because it is pure, it is also directly unit-testable against
 *       synthetic snapshots with no world involved.</li>
 *   <li>The result is applied by {@link com.branciho.livingcities.scan.BuildingScanService}, which
 *       <b>polls</b> the worker's future from the server tick rather than registering a completion
 *       callback. A callback would run on the worker thread; polling means every mutation of
 *       {@code Building}, every packet and every {@code setDirty()} provably happens on the server
 *       thread.</li>
 * </ul>
 *
 * <h2>Block agnosticism</h2>
 *
 * <p>There is not one vanilla block id anywhere in this package. Classification is derived from
 * {@code BlockState} and {@code BlockBehaviour} queries plus the vanilla door tags, which modded
 * blocks join automatically. Two datapack tags,
 * {@code livingcities:scan_excluded} and {@code livingcities:seals_building}, exist as an escape
 * hatch so a modpack author can correct a pathological block without a code change.
 */
package com.branciho.livingcities.scan;
