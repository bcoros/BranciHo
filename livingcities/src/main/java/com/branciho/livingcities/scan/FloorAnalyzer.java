package com.branciho.livingcities.scan;

import com.branciho.livingcities.building.FloorFlag;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Finds the storeys in a {@link BuildingSnapshot} and measures how much of each one is usable.
 *
 * <p><b>Pure.</b> This class calls nothing outside {@code java.*} and this package. It never sees a
 * level, a chunk or a block. That is what allows it to run on a worker thread while the server keeps
 * ticking, and it is also what makes it testable: feed it a hand-written snapshot and assert on the
 * floor list, no world required.
 *
 * <h2>The shape of the algorithm</h2>
 *
 * <ol>
 *   <li><b>Histogram.</b> One pass over the volume counting standable cells at each height. A
 *       building announces its own floors here: the counts spike at every storey.</li>
 *   <li><b>Candidates.</b> Plateau-aware local maxima with non-maximum suppression at a minimum
 *       floor pitch. Deliberately generous - a mezzanine is a small peak, and a strict candidate
 *       filter would lose it with no way for the player to find out why.</li>
 *   <li><b>Validation.</b> Each candidate gets an enclosure flood fill, then has to survive tests
 *       for usable area, largest connected room, headroom and ceiling coverage. This is where
 *       terrain, roofs, scaffolding and crawlspaces die.</li>
 *   <li><b>Ranges and flags.</b> Y spans, ceiling heights, roof and basement tagging.</li>
 * </ol>
 *
 * <h2>Why generous candidates and strict validation, and not the reverse</h2>
 *
 * <p>Both orderings produce roughly the same answer on a plain box. They differ completely on the
 * interesting builds. Strict candidate generation silently drops mezzanines, basements and
 * split-levels, and the player sees a floor count that is simply wrong with no diagnostic. Strict
 * validation rejects things for a reason that can be named, counted and reported.
 */
public final class FloorAnalyzer {

    /**
     * Bump when the algorithm changes in a way that would give a different answer for the same
     * blocks. Buildings scanned under an older version can then be re-queued after a mod update.
     */
    public static final int VERSION = 1;

    /** A cell needs walls in three of four directions before the fallback estimator calls it indoors. */
    private static final int RAY_MIN_SEALED_DIRECTIONS = 3;

    // 4-connected, never 8. Diagonal connectivity leaks straight through the corner where two walls
    // meet at 45 degrees, which is one of the most common shapes in player builds.
    private static final int[] NEIGHBOUR_DX = {-1, 1, 0, 0};
    private static final int[] NEIGHBOUR_DZ = {0, 0, -1, 1};

    private FloorAnalyzer() {
    }

    /**
     * @param gradeY world Y of the surrounding ground, sampled on the server thread before the
     *               hand-off; floors below it are tagged {@link FloorFlag#BASEMENT}
     */
    public static FloorPlan analyze(BuildingSnapshot snapshot, int gradeY, ScanSettings settings) {
        return new Run(snapshot, gradeY, settings).analyze();
    }

    // ------------------------------------------------------------------ the run

    /** One analysis, holding the working buffers so they are allocated once instead of per floor. */
    private static final class Run {

        private final BuildingSnapshot snap;
        private final ScanSettings cfg;
        private final int gradeY;

        private final int sizeX;
        private final int sizeY;
        private final int sizeZ;
        private final int area;
        private final int words;

        private final short[] cells;
        private final BlockProfile[] profiles;

        /** Which XZ columns lie inside the registered building, as opposed to the outdoor collar. */
        private final long[] footprint;

        private final int[] standCount;
        private final float[] standWeight;
        private final int[] coveredCount;
        /** Per-height bitsets of standable cells; {@code standBits[y * words + w]}. */
        private final long[] standBits;

        private final long[] outside;
        private final long[] visited;
        private final int[] queue;

        private int topSolidY = -1;

        Run(BuildingSnapshot snap, int gradeY, ScanSettings cfg) {
            this.snap = snap;
            this.cfg = cfg;
            this.gradeY = gradeY;
            this.sizeX = snap.sizeX();
            this.sizeY = snap.sizeY();
            this.sizeZ = snap.sizeZ();
            this.area = sizeX * sizeZ;
            this.words = (area + 63) >>> 6;
            this.cells = snap.cells();
            this.profiles = snap.profiles();
            this.footprint = new long[words];
            this.standCount = new int[sizeY];
            this.standWeight = new float[sizeY];
            this.coveredCount = new int[sizeY];
            // Bounded by volume/64 longs regardless of how the selection is shaped, so this stays
            // small even for a maximum-size skyscraper.
            this.standBits = new long[sizeY * words];
            this.outside = new long[words];
            this.visited = new long[words];
            this.queue = new int[area];
        }

        FloorPlan analyze() {
            final long started = System.nanoTime();
            if (snap.regMinX() > snap.regMaxX() || snap.regMinY() > snap.regMaxY()
                    || snap.regMinZ() > snap.regMaxZ()) {
                return FloorPlan.empty();
            }

            markFootprint();
            histogram();

            final List<Integer> accepted = new ArrayList<>();
            final Map<Integer, Integer> suppressed = new LinkedHashMap<>();
            final int candidateCount = generateCandidates(accepted, suppressed);

            final List<Storey> storeys = new ArrayList<>();
            final List<Storey> openAir = new ArrayList<>();
            int rejected = 0;
            for (int floorY : accepted) {
                Storey storey = evaluate(floorY);
                switch (storey.outcome) {
                    case ACCEPTED -> storeys.add(storey);
                    case OPEN_AIR -> openAir.add(storey);
                    case REJECTED -> rejected++;
                }
            }

            rejected += keepTopmostRoof(storeys, openAir);
            absorbSubLevels(storeys, suppressed);

            storeys.sort((a, b) -> Integer.compare(a.floorY, b.floorY));
            final List<DetectedFloor> floors = buildFloors(storeys);

            int leaky = 0;
            for (DetectedFloor floor : floors) {
                if (floor.leaky()) {
                    leaky++;
                }
            }
            int unknown = 0;
            for (BlockProfile profile : profiles) {
                if (profile.blockClass() == BlockClass.UNKNOWN) {
                    unknown++;
                }
            }

            return new FloorPlan(floors, candidateCount, rejected, leaky, unknown, profiles.length,
                    System.nanoTime() - started);
        }

        // -------------------------------------------------------------- phase A

        private void markFootprint() {
            for (int z = snap.regMinZ(); z <= snap.regMaxZ(); z++) {
                for (int x = snap.regMinX(); x <= snap.regMaxX(); x++) {
                    setBit(footprint, z * sizeX + x);
                }
            }
        }

        /**
         * Count standable cells per height.
         *
         * <p>Restricted to the registered footprint on purpose: the outdoor collar exists to seed the
         * enclosure fill, and letting the terrain around the building vote in the height histogram
         * would put a "floor" at ground level of every hillside.
         */
        private void histogram() {
            final int minBody = cfg.minBodyHeight();
            final int probe = cfg.maxCeilingProbe();
            final int yStart = Math.max(0, snap.regMinY() - 1);

            for (int z = snap.regMinZ(); z <= snap.regMaxZ(); z++) {
                for (int x = snap.regMinX(); x <= snap.regMaxX(); x++) {
                    final int base = snap.columnBase(x, z);
                    final int cell = z * sizeX + x;
                    final int wordOffset = cell >>> 6;
                    final long mask = 1L << (cell & 63);

                    for (int y = yStart; y <= snap.regMaxY(); y++) {
                        final BlockProfile support = profiles[cells[base + y] & 0xFFFF];
                        final BlockClass kind = support.blockClass();
                        if (y > topSolidY && y >= snap.regMinY()
                                && kind != BlockClass.OPEN && kind != BlockClass.EXCLUDED) {
                            topSolidY = y;
                        }
                        if (!support.supports()) {
                            continue;
                        }

                        final int floorY = support.footY(y);
                        if (floorY < snap.regMinY() || floorY > snap.regMaxY() || floorY + minBody > sizeY) {
                            continue;
                        }
                        final int word = floorY * words + wordOffset;
                        // Scanning upward means the lowest support in a column wins, so a slab sitting
                        // on stone is recorded once rather than twice.
                        if ((standBits[word] & mask) != 0L) {
                            continue;
                        }

                        boolean clear = true;
                        for (int k = 0; k < minBody; k++) {
                            if (!profiles[cells[base + floorY + k] & 0xFFFF].headClear()) {
                                clear = false;
                                break;
                            }
                        }
                        if (!clear) {
                            continue;
                        }

                        standBits[word] |= mask;
                        standCount[floorY]++;
                        standWeight[floorY] += support.surfaceWeight();

                        for (int k = minBody; k < probe && floorY + k < sizeY; k++) {
                            if (profiles[cells[base + floorY + k] & 0xFFFF].blocksMotion()) {
                                coveredCount[floorY]++;
                                break;
                            }
                        }
                    }
                }
            }
        }

        // -------------------------------------------------------------- phase B

        private int generateCandidates(List<Integer> accepted, Map<Integer, Integer> suppressed) {
            final int separation = cfg.minFloorSeparation();
            final List<Integer> candidates = new ArrayList<>();

            for (int y = snap.regMinY(); y <= snap.regMaxY(); y++) {
                if (standCount[y] < cfg.minCandidateCells()) {
                    continue;
                }
                boolean peak = true;
                for (int d = 1; d < separation; d++) {
                    // Strict below, non-strict above: a two-block-thick floor slab produces two equal
                    // heights, and without the asymmetry they suppress each other unpredictably. This
                    // way ties always resolve downward, which is also where you actually stand.
                    if (y - d >= 0 && standWeight[y - d] > standWeight[y]) {
                        peak = false;
                        break;
                    }
                    if (y + d < sizeY && standWeight[y + d] >= standWeight[y]) {
                        peak = false;
                        break;
                    }
                }
                if (peak) {
                    candidates.add(y);
                }
            }

            final List<Integer> byWeight = new ArrayList<>(candidates);
            byWeight.sort((a, b) -> Float.compare(standWeight[b], standWeight[a]));
            for (int y : byWeight) {
                int winner = -1;
                for (int already : accepted) {
                    if (Math.abs(already - y) < separation) {
                        winner = already;
                        break;
                    }
                }
                if (winner < 0) {
                    accepted.add(y);
                } else {
                    // Not discarded: a suppressed height may be the other half of a split level.
                    suppressed.put(y, winner);
                }
            }
            accepted.sort(Integer::compare);
            return candidates.size();
        }

        // -------------------------------------------------------------- phase C

        /**
         * Measure one candidate height and decide what it is.
         *
         * <p>The ladder is: clean flood fill first, because it is right almost always and costs
         * nothing; then the ray estimator, but only for a floor that is roofed and yet mostly
         * reachable from outside, which is the signature of a single missing block in a wall; then a
         * verdict of "open air" for anything with no ceiling, which becomes a roof deck rather than
         * being deleted.
         */
        private Storey evaluate(int floorY) {
            final Storey storey = new Storey(floorY);
            storey.standCells = standCount[floorY];
            if (storey.standCells <= 0) {
                storey.outcome = Outcome.REJECTED;
                return storey;
            }

            floodOutside(floorY);

            final long[] enclosed = new long[words];
            final int rowOffset = floorY * words;
            int fillCount = 0;
            for (int w = 0; w < words; w++) {
                final long value = standBits[rowOffset + w] & ~outside[w] & footprint[w];
                enclosed[w] = value;
                fillCount += Long.bitCount(value);
            }
            storey.usable = enclosed;
            storey.usableCells = fillCount;
            measure(floorY, storey);

            if (passes(storey)) {
                storey.outcome = Outcome.ACCEPTED;
                return storey;
            }

            final float coveredStand = coveredCount[floorY] / (float) storey.standCells;
            final float leakFraction = (storey.standCells - fillCount) / (float) storey.standCells;
            if (leakFraction > cfg.leakThreshold() && coveredStand > cfg.leakCoverageFloor()) {
                final long[] rayed = rayInterior(floorY);
                final int scaled = Math.round(popCount(rayed) * cfg.rayConfidence());
                // Never let the estimator lower a confident fill result; it only ever rescues one.
                if (scaled > fillCount) {
                    storey.usable = rayed;
                    storey.usableCells = scaled;
                    storey.leaky = true;
                    measure(floorY, storey);
                    if (passes(storey)) {
                        storey.outcome = Outcome.ACCEPTED;
                        return storey;
                    }
                }
            }

            storey.outcome = coveredStand < cfg.minCoverageFraction() ? Outcome.OPEN_AIR : Outcome.REJECTED;
            return storey;
        }

        private boolean passes(Storey storey) {
            return storey.usableCells >= cfg.minUsableCells()
                    && storey.largestComponent >= cfg.minRoomCells()
                    && storey.medianCeiling >= cfg.minBodyHeight()
                    && storey.coveredFraction >= cfg.minCoverageFraction();
        }

        /**
         * Flood "outside" inward from the collar around the selection.
         *
         * <p>Doors are walls here whether they are open or shut, which is the difference between a
         * house measuring correctly and a house losing its entire population because someone left the
         * front door open.
         */
        private void floodOutside(int floorY) {
            Arrays.fill(outside, 0L);
            int head = 0;
            int tail = 0;

            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++) {
                    if (snap.insideFootprint(x, z) || !fillPassable(x, floorY, z)) {
                        continue;
                    }
                    final int cell = z * sizeX + x;
                    setBit(outside, cell);
                    queue[tail++] = cell;
                }
            }

            while (head < tail) {
                final int cell = queue[head++];
                final int x = cell % sizeX;
                final int z = cell / sizeX;
                for (int d = 0; d < NEIGHBOUR_DX.length; d++) {
                    final int nx = x + NEIGHBOUR_DX[d];
                    final int nz = z + NEIGHBOUR_DZ[d];
                    if (nx < 0 || nz < 0 || nx >= sizeX || nz >= sizeZ) {
                        continue;
                    }
                    final int neighbour = nz * sizeX + nx;
                    if (getBit(outside, neighbour) || !fillPassable(nx, floorY, nz)) {
                        continue;
                    }
                    setBit(outside, neighbour);
                    queue[tail++] = neighbour;
                }
            }
        }

        private boolean fillPassable(int x, int floorY, int z) {
            final int base = snap.columnBase(x, z);
            for (int k = 0; k < cfg.minBodyHeight(); k++) {
                final int y = floorY + k;
                if (y >= sizeY) {
                    return false;
                }
                final BlockProfile profile = profiles[cells[base + y] & 0xFFFF];
                if (profile.blockClass() == BlockClass.DOOR_LIKE || !profile.headClear()) {
                    return false;
                }
            }
            return true;
        }

        /**
         * Fallback for a leaky envelope: a cell counts as indoors if it has a wall in at least three
         * of four directions and something over its head.
         *
         * <p>This measures "am I inside a roofed enclosure" without requiring the enclosure to be
         * airtight. It over-counts a large covered plaza, which is exactly why its result is
         * discounted by {@link ScanSettings#rayConfidence()} and can never beat a clean fill.
         */
        private long[] rayInterior(int floorY) {
            final long[] result = new long[words];
            final int headY = Math.min(floorY + cfg.minBodyHeight() - 1, sizeY - 1);
            final int rowOffset = floorY * words;

            for (int w = 0; w < words; w++) {
                long bits = standBits[rowOffset + w] & footprint[w];
                while (bits != 0L) {
                    final int cell = (w << 6) + Long.numberOfTrailingZeros(bits);
                    bits &= bits - 1L;
                    final int x = cell % sizeX;
                    final int z = cell / sizeX;

                    int sealed = 0;
                    for (int d = 0; d < NEIGHBOUR_DX.length; d++) {
                        if (raySeals(x, headY, z, NEIGHBOUR_DX[d], NEIGHBOUR_DZ[d])) {
                            sealed++;
                        }
                    }
                    if (sealed < RAY_MIN_SEALED_DIRECTIONS) {
                        continue;
                    }
                    if (clearHeight(x, floorY, z, cfg.maxCeilingProbe()) >= cfg.maxCeilingProbe()) {
                        continue;
                    }
                    setBit(result, cell);
                }
            }
            return result;
        }

        private boolean raySeals(int x, int y, int z, int dx, int dz) {
            int cx = x;
            int cz = z;
            for (int i = 0; i < cfg.maxRayLength(); i++) {
                cx += dx;
                cz += dz;
                if (cx < 0 || cz < 0 || cx >= sizeX || cz >= sizeZ) {
                    return false;
                }
                if (profiles[cells[snap.columnBase(cx, cz) + y] & 0xFFFF].seals()) {
                    return true;
                }
            }
            return false;
        }

        /** Largest connected room, median ceiling and ceiling coverage over the storey's usable set. */
        private void measure(int floorY, Storey storey) {
            storey.largestComponent = largestComponent(storey.usable);

            final int cap = cfg.maxCeilingProbe();
            final int[] histogram = new int[cap + 1];
            int counted = 0;
            int covered = 0;
            for (int w = 0; w < words; w++) {
                long bits = storey.usable[w];
                while (bits != 0L) {
                    final int cell = (w << 6) + Long.numberOfTrailingZeros(bits);
                    bits &= bits - 1L;
                    final int height = clearHeight(cell % sizeX, floorY, cell / sizeX, cap);
                    histogram[height]++;
                    counted++;
                    if (height < cap) {
                        covered++;
                    }
                }
            }
            storey.coveredFraction = counted == 0 ? 0.0F : covered / (float) counted;
            storey.medianCeiling = counted == 0 ? 0 : median(histogram, counted);
        }

        /**
         * Consecutive head-clear cells starting at the feet, capped.
         *
         * <p>Median clear height rather than the storey's Y span, so a room under a pitched roof
         * reports a sane ceiling instead of the distance to the ridge.
         */
        private int clearHeight(int x, int floorY, int z, int cap) {
            final int base = snap.columnBase(x, z);
            int height = 0;
            while (height < cap && floorY + height < sizeY
                    && profiles[cells[base + floorY + height] & 0xFFFF].headClear()) {
                height++;
            }
            return height;
        }

        private int largestComponent(long[] bits) {
            Arrays.fill(visited, 0L);
            int best = 0;
            for (int w = 0; w < words; w++) {
                long word = bits[w];
                while (word != 0L) {
                    final int start = (w << 6) + Long.numberOfTrailingZeros(word);
                    word &= word - 1L;
                    if (getBit(visited, start)) {
                        continue;
                    }
                    setBit(visited, start);
                    int head = 0;
                    int tail = 0;
                    queue[tail++] = start;
                    int size = 0;
                    while (head < tail) {
                        final int cell = queue[head++];
                        size++;
                        final int x = cell % sizeX;
                        final int z = cell / sizeX;
                        for (int d = 0; d < NEIGHBOUR_DX.length; d++) {
                            final int nx = x + NEIGHBOUR_DX[d];
                            final int nz = z + NEIGHBOUR_DZ[d];
                            if (nx < 0 || nz < 0 || nx >= sizeX || nz >= sizeZ) {
                                continue;
                            }
                            final int neighbour = nz * sizeX + nx;
                            if (getBit(visited, neighbour) || !getBit(bits, neighbour)) {
                                continue;
                            }
                            setBit(visited, neighbour);
                            queue[tail++] = neighbour;
                        }
                    }
                    best = Math.max(best, size);
                }
            }
            return best;
        }

        // -------------------------------------------------------------- roofs and split levels

        /**
         * Keep the highest open-air candidate as a roof deck and discard the rest.
         *
         * <p>A roof is not an error and deleting it loses information the UI wants ("Roof deck") and
         * that later features will want (helipads, rooftop gardens). It is kept with zero usable
         * cells so it can never contribute capacity, while {@code standCells} preserves the deck's
         * real area. An open terrace halfway up a tower is a different thing and is rejected.
         *
         * @return how many candidates were discarded
         */
        private int keepTopmostRoof(List<Storey> storeys, List<Storey> openAir) {
            if (openAir.isEmpty()) {
                return 0;
            }
            Storey highest = openAir.get(0);
            for (Storey candidate : openAir) {
                if (candidate.floorY > highest.floorY) {
                    highest = candidate;
                }
            }
            int highestFloor = Integer.MIN_VALUE;
            for (Storey storey : storeys) {
                highestFloor = Math.max(highestFloor, storey.floorY);
            }
            if (highest.floorY < highestFloor) {
                return openAir.size();
            }

            // Re-measure over the whole deck: the enclosure fill correctly found almost none of it
            // enclosed, which is true but useless for reporting the deck's size and headroom.
            final int rowOffset = highest.floorY * words;
            final long[] deck = new long[words];
            for (int w = 0; w < words; w++) {
                deck[w] = standBits[rowOffset + w] & footprint[w];
            }
            highest.usable = deck;
            measure(highest.floorY, highest);
            highest.usableCells = 0;
            highest.flags.add(FloorFlag.ROOF);
            storeys.add(highest);
            return openAir.size() - 1;
        }

        /**
         * Fold a suppressed height back into its winner when the two are side by side rather than
         * stacked.
         *
         * <p>A house on a hillside has one ground floor spread across two heights. Non-maximum
         * suppression correctly refuses to call them two storeys, but the lower half's area is real
         * and would otherwise vanish. Two heights of one split level are disjoint in plan; a genuine
         * mezzanine sits directly over the floor below. The overlap test is the whole distinction.
         */
        private void absorbSubLevels(List<Storey> storeys, Map<Integer, Integer> suppressed) {
            for (Map.Entry<Integer, Integer> entry : suppressed.entrySet()) {
                final int suppressedY = entry.getKey();
                if (standCount[suppressedY] < cfg.minRoomCells()) {
                    continue;
                }
                Storey host = null;
                for (Storey storey : storeys) {
                    if (storey.floorY == entry.getValue()) {
                        host = storey;
                        break;
                    }
                }
                if (host == null || host.flags.contains(FloorFlag.ROOF)) {
                    continue;
                }

                final Storey sub = evaluate(suppressedY);
                if (sub.usableCells < cfg.minRoomCells()) {
                    continue;
                }
                final int hostCells = popCount(host.usable);
                final int subCells = popCount(sub.usable);
                final int smaller = Math.min(hostCells, subCells);
                if (smaller == 0) {
                    continue;
                }
                if (popCountAnd(host.usable, sub.usable) / (float) smaller >= cfg.subLevelMaxOverlap()) {
                    continue;
                }

                orInto(host.usable, sub.usable);
                host.usableCells += sub.usableCells;
                host.standCells += sub.standCells;
                host.subLevels.add(suppressedY);
                host.flags.add(FloorFlag.SPLIT_LEVEL);
                host.leaky |= sub.leaky;
            }
        }

        // -------------------------------------------------------------- phase D

        private List<DetectedFloor> buildFloors(List<Storey> storeys) {
            final List<DetectedFloor> floors = new ArrayList<>(storeys.size());
            for (int i = 0; i < storeys.size(); i++) {
                final Storey storey = storeys.get(i);
                final int yMin = storey.floorY;
                int yMax = (i + 1 < storeys.size())
                        ? storeys.get(i + 1).floorY - 1
                        : Math.max(storey.floorY, topSolidY);
                if (yMax < yMin) {
                    yMax = yMin;
                }

                final int worldFloorY = snap.minY() + storey.floorY;
                if (worldFloorY < gradeY) {
                    storey.flags.add(FloorFlag.BASEMENT);
                }
                // EXTENSION POINT: FloorFlag.HAS_ATRIUM belongs here - tag a storey whose usable
                // region has a large hole that is enclosed on the storey below.

                final List<Integer> subLevels = new ArrayList<>(storey.subLevels.size());
                for (int sub : storey.subLevels) {
                    subLevels.add(snap.minY() + sub);
                }

                floors.add(new DetectedFloor(
                        worldFloorY,
                        snap.minY() + yMin,
                        snap.minY() + yMax,
                        storey.standCells,
                        storey.usableCells,
                        Math.max(0, Math.min(storey.medianCeiling, yMax - yMin + 1)),
                        storey.leaky,
                        subLevels,
                        storey.flags));
            }
            return floors;
        }

        // -------------------------------------------------------------- bit and stat helpers

        private static int median(int[] histogram, int total) {
            final int target = (total + 1) / 2;
            int seen = 0;
            for (int value = 0; value < histogram.length; value++) {
                seen += histogram[value];
                if (seen >= target) {
                    return value;
                }
            }
            return 0;
        }

        private static void setBit(long[] bits, int index) {
            bits[index >>> 6] |= 1L << (index & 63);
        }

        private static boolean getBit(long[] bits, int index) {
            return (bits[index >>> 6] & (1L << (index & 63))) != 0L;
        }

        private static int popCount(long[] bits) {
            int total = 0;
            for (long word : bits) {
                total += Long.bitCount(word);
            }
            return total;
        }

        private static int popCountAnd(long[] a, long[] b) {
            int total = 0;
            for (int i = 0; i < a.length; i++) {
                total += Long.bitCount(a[i] & b[i]);
            }
            return total;
        }

        private static void orInto(long[] target, long[] source) {
            for (int i = 0; i < target.length; i++) {
                target[i] |= source[i];
            }
        }
    }

    private enum Outcome {
        ACCEPTED,
        /** Standable and roomy but with nothing overhead: a roof, a terrace, a lawn. */
        OPEN_AIR,
        REJECTED
    }

    /** Mutable working state for one candidate height, in snapshot-local coordinates. */
    private static final class Storey {

        private final int floorY;
        private final List<Integer> subLevels = new ArrayList<>(2);
        private final EnumSet<FloorFlag> flags = EnumSet.noneOf(FloorFlag.class);

        private Outcome outcome = Outcome.REJECTED;
        private long[] usable;
        private int usableCells;
        private int standCells;
        private int largestComponent;
        private int medianCeiling;
        private float coveredFraction;
        private boolean leaky;

        Storey(int floorY) {
            this.floorY = floorY;
        }
    }
}
