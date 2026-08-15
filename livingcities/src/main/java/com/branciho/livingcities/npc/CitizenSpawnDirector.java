package com.branciho.livingcities.npc;

import com.branciho.livingcities.building.Building;
import com.branciho.livingcities.city.City;
import com.branciho.livingcities.city.CityRegistry;
import com.branciho.livingcities.config.LivingCitiesConfig;
import com.branciho.livingcities.entity.CitizenEntity;
import com.branciho.livingcities.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Decides how many citizens are visible where, and puts them there.
 *
 * <p>This is the only entry point the integration layer needs; everything else in the package is an
 * implementation detail of what happens between {@link #tick} and a person appearing on a street.
 *
 * <h2>What a pass does</h2>
 *
 * <ol>
 *   <li><b>Cull.</b> Forget citizens the game has already removed, and discard any that are further
 *       than {@value #DESPAWN_RADIUS} blocks from every player.</li>
 *   <li><b>Cap.</b> If the world is over {@code maxPhysicalNpcs}, discard the ones furthest from any
 *       player first - they are the ones nobody is looking at.</li>
 *   <li><b>Budget.</b> For each online player, work out the local crowd target from
 *       {@link CrowdBudget}, smooth it, and spawn or despawn toward it.</li>
 * </ol>
 *
 * <h2>Why the entity list is tracked rather than queried</h2>
 *
 * <p>The director keeps its own list of every citizen it has spawned, keyed by dimension, and prunes
 * entries whose entity reports {@code isRemoved()}. That is authoritative rather than optimistic:
 * this class is the only thing that spawns citizens, and {@code CitizenEntity} is registered
 * {@code noSave()}, so none can ever arrive from disk. Querying the world instead would mean an
 * entity search per player per pass to answer a question we already know the answer to, and - worse -
 * would silently miss any citizen standing in a chunk that has stopped ticking, which is precisely
 * the population we most want to clean up.
 *
 * <p>The one thing this does not manage is a citizen conjured by hand with {@code /summon}. Those are
 * left alone rather than adopted, on the grounds that somebody who summons one is debugging.
 *
 * <h2>Hysteresis</h2>
 *
 * <p>The raw budget moves every time a cloud of workers clocks off or a player takes a step, and
 * chasing it directly would spawn and despawn several entities a second. Two mechanisms stop that.
 * The target is first low-pass filtered ({@value #TARGET_SMOOTHING} of the way to the new value per
 * pass), so it drifts rather than jumps; then it is applied through a dead band of
 * {@value #HYSTERESIS_BAND} in each direction, so nothing happens at all until the crowd is
 * genuinely wrong. A large jump - a player teleporting from a suburb into downtown - snaps instead of
 * drifting, because there the delay would be the artefact.
 *
 * <h2>Threading</h2>
 *
 * <p><b>Server thread only.</b> Call {@link #tick} from {@code ServerTickEvent.Post} and
 * {@link #reset} from both {@code ServerStartingEvent} and {@code ServerStoppingEvent}.
 */
public final class CitizenSpawnDirector {

    /** Ticks between passes. Two seconds: fast enough to follow a walking player, cheap enough to ignore. */
    private static final int PASS_INTERVAL_TICKS = 40;

    /** Radius around a player that the budget describes, and inside which citizens are staged. */
    private static final int CROWD_RADIUS = 48;
    private static final double CROWD_RADIUS_SQ = (double) CROWD_RADIUS * CROWD_RADIUS;

    /**
     * Beyond this, a citizen is gone regardless of any budget. Comfortably larger than
     * {@link #CROWD_RADIUS} so that walking a few blocks does not delete the crowd behind you.
     */
    private static final int DESPAWN_RADIUS = 72;
    private static final double DESPAWN_RADIUS_SQ = (double) DESPAWN_RADIUS * DESPAWN_RADIUS;

    /** Nobody materialises inside this radius; people appearing out of thin air is the thing to avoid. */
    private static final int MIN_SPAWN_DISTANCE = 14;
    private static final double MIN_SPAWN_DISTANCE_SQ = (double) MIN_SPAWN_DISTANCE * MIN_SPAWN_DISTANCE;

    /** Chunk radius searched for the city the player is standing in or next to. */
    private static final int CITY_SEARCH_CHUNK_RADIUS = 2;

    /** Chunk radius searched for buildings. Covers {@link #CROWD_RADIUS} with a chunk to spare. */
    private static final int BUILDING_CHUNK_RADIUS = 4;

    /** Cost guard for a player standing in an extremely dense downtown. */
    private static final int MAX_NEARBY_BUILDINGS = 48;

    /** Rate limits, so a budget that jumps still fills in over a few seconds rather than in one frame. */
    private static final int MAX_SPAWNS_PER_PASS = 4;
    private static final int MAX_DESPAWNS_PER_PASS = 6;

    /** Dead band around the smoothed target, in citizens. See the class docs. */
    private static final int HYSTERESIS_BAND = 3;

    /** Fraction of the way the smoothed target moves toward the raw target each pass. */
    private static final double TARGET_SMOOTHING = 0.25D;

    /** A change this large is a change of place, not of mood, so the smoothing is skipped. */
    private static final double TARGET_SNAP_DELTA = 12.0D;

    /** One viewer never needs more than this many, however large the global cap is. */
    private static final int PER_VIEWER_HARD_CAP = 48;

    /** Floor on a viewer's share of the global cap, so a busy server does not thin every street to nothing. */
    private static final int MIN_VIEWER_SHARE = 6;

    /** How far the pedestrian-network seam may pull a spawn position once a graph exists. */
    private static final int NODE_SNAP_RADIUS = 6;

    /** Spawned citizens by dimension. See the class docs for why this is tracked rather than queried. */
    private static final Map<ResourceKey<Level>, List<CitizenEntity>> TRACKED = new HashMap<>();

    /** Per-player smoothing state, keyed by player id so a reconnect does not inherit a stale target. */
    private static final Map<UUID, ViewerState> VIEWERS = new HashMap<>();

    private static PedestrianNetwork network = PedestrianNetwork.EMPTY;

    private static long lastPassAt = Long.MIN_VALUE;

    private CitizenSpawnDirector() {
    }

    /**
     * Drive the physical NPC layer. Call once per server tick; almost every call returns immediately.
     *
     * @param registry the live registry for this server - never cache it, it is replaced on reload
     */
    public static void tick(MinecraftServer server, CityRegistry registry) {
        final long gameTime = server.overworld().getGameTime();
        if (gameTime == lastPassAt || Math.floorMod(gameTime, PASS_INTERVAL_TICKS) != 0) {
            return;
        }
        lastPassAt = gameTime;

        final int globalCap = LivingCitiesConfig.SERVER.maxPhysicalNpcs.get();
        final double densityMultiplier = LivingCitiesConfig.SERVER.npcDensityMultiplier.get();

        final List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (globalCap <= 0 || densityMultiplier <= 0.0D || players.isEmpty()) {
            // Physical NPCs turned off, or nobody to see them. Either way the crowd should not linger:
            // an admin lowering the cap to zero expects the streets to empty, not to stay as they were.
            discardEverything();
            return;
        }

        int live = cullAndCount(server, globalCap);

        final int viewerCap = Math.min(PER_VIEWER_HARD_CAP,
                Math.min(globalCap, Math.max(MIN_VIEWER_SHARE, globalCap / players.size())));

        for (int i = 0; i < players.size(); i++) {
            live = serveViewer(registry, players.get(i), densityMultiplier, viewerCap, globalCap, live);
        }

        forgetOfflineViewers(players);
    }

    /**
     * Drop every scrap of static state, discarding any citizens still standing.
     *
     * <p>Called from both the server-start and the server-stop hooks. Both matter. In single player
     * the JVM outlives the integrated server, so state left behind by one world would otherwise be
     * inherited by the next - stale smoothing targets, and worse, entity references belonging to a
     * {@code ServerLevel} that no longer exists. Resetting on start as well as stop means an unclean
     * shutdown cannot leave that behind either.
     */
    public static void reset() {
        discardEverything();
        VIEWERS.clear();
        network = PedestrianNetwork.EMPTY;
        lastPassAt = Long.MIN_VALUE;
    }

    /**
     * Install the pedestrian node graph once one exists. See {@link PedestrianNetwork} - in v0.1 the
     * only implementation is {@link PedestrianNetwork#EMPTY} and this never needs calling.
     *
     * @param pedestrianNetwork the graph, or null to go back to the empty one
     */
    public static void setPedestrianNetwork(@Nullable PedestrianNetwork pedestrianNetwork) {
        network = pedestrianNetwork == null ? PedestrianNetwork.EMPTY : pedestrianNetwork;
    }

    /** Citizens currently spawned across every dimension. For commands and debug overlays. */
    public static int spawnedCount() {
        int total = 0;
        for (List<CitizenEntity> pool : TRACKED.values()) {
            total += pool.size();
        }
        return total;
    }

    // ------------------------------------------------------------------ cull and cap

    /**
     * Forget removed citizens, discard the ones nobody is near, enforce the global cap, and return
     * how many are left alive.
     */
    private static int cullAndCount(MinecraftServer server, int globalCap) {
        // Collected across all dimensions so the cap can be enforced globally in one sorted pass.
        final List<Viewed> live = new ArrayList<>();

        final Iterator<Map.Entry<ResourceKey<Level>, List<CitizenEntity>>> levels =
                TRACKED.entrySet().iterator();
        while (levels.hasNext()) {
            final Map.Entry<ResourceKey<Level>, List<CitizenEntity>> entry = levels.next();
            final List<CitizenEntity> pool = entry.getValue();

            // Resolved by key every pass rather than held: a cached ServerLevel would outlive a reload.
            final ServerLevel level = server.getLevel(entry.getKey());
            final List<ServerPlayer> levelPlayers = level == null ? List.of() : level.players();
            if (levelPlayers.isEmpty()) {
                discardAll(pool);
                levels.remove();
                continue;
            }

            for (Iterator<CitizenEntity> citizens = pool.iterator(); citizens.hasNext(); ) {
                final CitizenEntity citizen = citizens.next();
                if (citizen.isRemoved()) {
                    // Killed, or its chunk unloaded. Either way it is no longer ours to account for.
                    citizens.remove();
                    continue;
                }
                final double distanceSq = nearestPlayerDistanceSq(citizen, levelPlayers);
                if (distanceSq > DESPAWN_RADIUS_SQ) {
                    citizen.discard();
                    citizens.remove();
                    continue;
                }
                live.add(new Viewed(citizen, distanceSq));
            }

            if (pool.isEmpty()) {
                levels.remove();
            }
        }

        if (live.size() <= globalCap) {
            return live.size();
        }

        // Over the cap: the furthest from any player are the ones whose absence nobody can notice.
        live.sort((a, b) -> Double.compare(b.distanceSq(), a.distanceSq()));
        final int excess = live.size() - globalCap;
        for (int i = 0; i < excess; i++) {
            live.get(i).citizen().discard();
        }
        for (List<CitizenEntity> pool : TRACKED.values()) {
            pool.removeIf(CitizenEntity::isRemoved);
        }
        return globalCap;
    }

    // ------------------------------------------------------------------ per viewer

    /** Bring one player's local crowd toward its budget. Returns the updated global live count. */
    private static int serveViewer(CityRegistry registry, ServerPlayer player,
                                   double densityMultiplier, int viewerCap, int globalCap, int live) {
        if (!(player.level() instanceof ServerLevel level)) {
            return live;
        }
        final ResourceKey<Level> dimension = level.dimension();
        final BlockPos origin = player.blockPosition();

        final City city = findCity(registry, dimension, origin);
        final List<Building> nearby = city == null
                ? List.<Building>of()
                : nearbyBuildings(registry, dimension, origin);

        final CitizenActivity activity = CitizenActivity.at(level.getDayTime());
        final CrowdBudget budget = CrowdBudget.evaluate(
                city == null ? 0 : city.stats().population(),
                nearby, activity, densityMultiplier, viewerCap);

        final ViewerState state = VIEWERS.computeIfAbsent(player.getUUID(), key -> new ViewerState());
        final int target = state.update(budget.target());

        final List<CitizenEntity> pool = TRACKED.get(dimension);
        final int local = countNear(pool, player);

        if (local >= target + HYSTERESIS_BAND) {
            final int surplus = Math.min(local - target, MAX_DESPAWNS_PER_PASS);
            return live - despawnFurthest(pool, player, surplus);
        }
        if (local > target - HYSTERESIS_BAND || nearby.isEmpty() || live >= globalCap) {
            return live;
        }

        final int wanted = Math.min(Math.min(target - local, MAX_SPAWNS_PER_PASS), globalCap - live);
        return live + spawnBatch(level, player, nearby, activity, wanted);
    }

    private static int spawnBatch(ServerLevel level, ServerPlayer player, List<Building> nearby,
                                  CitizenActivity activity, int wanted) {
        final RandomSource random = level.getRandom();

        // Weighted by the same street-draw figure the budget was built from, so the building that
        // generated the demand is the building that receives the crowd.
        final double[] weights = new double[nearby.size()];
        double totalWeight = 0.0D;
        for (int i = 0; i < nearby.size(); i++) {
            weights[i] = Math.max(0.0D, CrowdBudget.streetDraw(nearby.get(i)));
            totalWeight += weights[i];
        }

        final BlockPos viewer = player.blockPosition();
        int spawned = 0;
        for (int i = 0; i < wanted; i++) {
            final CitizenRole role = activity.roleAt(random.nextDouble());
            final Building anchor = chooseAnchor(nearby, weights, totalWeight, role, random);
            if (anchor == null) {
                break;
            }
            BlockPos spot = StreetSpawnFinder.find(
                    level, anchor, viewer, MIN_SPAWN_DISTANCE_SQ, CROWD_RADIUS_SQ, random);
            if (spot == null) {
                continue;
            }

            // SEAM (see PedestrianNetwork): with a real graph installed this snaps the spawn onto the
            // sidewalk network. With the empty one it returns null and nothing changes.
            final BlockPos node = network.isEmpty() ? null : network.nearestNode(level, spot, NODE_SNAP_RADIUS);
            if (node != null && StreetSpawnFinder.isStandable(level, node)) {
                spot = node;
            }

            if (spawn(level, spot, random)) {
                spawned++;
            }
        }
        return spawned;
    }

    private static boolean spawn(ServerLevel level, BlockPos pos, RandomSource random) {
        final CitizenEntity citizen = ModEntities.CITIZEN.get().create(level);
        if (citizen == null) {
            return false;
        }
        citizen.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, random.nextFloat() * 360.0F, 0.0F);
        citizen.setVariant(random.nextInt(CitizenEntity.VARIANT_COUNT));
        if (!level.addFreshEntity(citizen)) {
            return false;
        }
        TRACKED.computeIfAbsent(level.dimension(), key -> new ArrayList<>()).add(citizen);
        return true;
    }

    /**
     * Two draws weighted by street presence, keeping whichever suits the role.
     *
     * <p>Cheaper than folding role affinity into every building's weight - which would walk every
     * floor list on every spawn - and it still leaves the busiest frontage most likely to win, which
     * is the property that actually matters.
     */
    private static @Nullable Building chooseAnchor(List<Building> nearby, double[] weights,
                                                   double totalWeight, CitizenRole role,
                                                   RandomSource random) {
        final Building first = weightedPick(nearby, weights, totalWeight, random);
        final Building second = weightedPick(nearby, weights, totalWeight, random);
        if (first == null || second == null || first == second) {
            return first == null ? second : first;
        }
        final boolean firstFits = first.usableCells(role.destination()) > 0;
        final boolean secondFits = second.usableCells(role.destination()) > 0;
        return !firstFits && secondFits ? second : first;
    }

    private static @Nullable Building weightedPick(List<Building> nearby, double[] weights,
                                                   double totalWeight, RandomSource random) {
        if (nearby.isEmpty()) {
            return null;
        }
        if (totalWeight <= 0.0D) {
            // Registered but unscanned or unzoned buildings all draw zero; spread evenly rather than
            // always picking the first one in the list.
            return nearby.get(random.nextInt(nearby.size()));
        }
        double roll = random.nextDouble() * totalWeight;
        for (int i = 0; i < nearby.size(); i++) {
            roll -= weights[i];
            if (roll <= 0.0D) {
                return nearby.get(i);
            }
        }
        return nearby.get(nearby.size() - 1);
    }

    // ------------------------------------------------------------------ despawn helpers

    /** Discard the {@code count} citizens furthest from this player, without touching anyone else's. */
    private static int despawnFurthest(@Nullable List<CitizenEntity> pool, ServerPlayer player, int count) {
        if (pool == null || pool.isEmpty() || count <= 0) {
            return 0;
        }
        int removed = 0;
        for (int pass = 0; pass < count; pass++) {
            int worstIndex = -1;
            double worstDistance = -1.0D;
            for (int i = 0; i < pool.size(); i++) {
                final CitizenEntity citizen = pool.get(i);
                if (citizen.isRemoved()) {
                    continue;
                }
                final double distanceSq = citizen.distanceToSqr(player);
                // Only this viewer's surplus is this viewer's to trim; someone else may be standing
                // next to the ones further out.
                if (distanceSq > CROWD_RADIUS_SQ || distanceSq <= worstDistance) {
                    continue;
                }
                worstDistance = distanceSq;
                worstIndex = i;
            }
            if (worstIndex < 0) {
                break;
            }
            pool.get(worstIndex).discard();
            pool.remove(worstIndex);
            removed++;
        }
        return removed;
    }

    private static void discardAll(List<CitizenEntity> pool) {
        for (int i = 0; i < pool.size(); i++) {
            final CitizenEntity citizen = pool.get(i);
            if (!citizen.isRemoved()) {
                citizen.discard();
            }
        }
        pool.clear();
    }

    private static void discardEverything() {
        for (List<CitizenEntity> pool : TRACKED.values()) {
            discardAll(pool);
        }
        TRACKED.clear();
    }

    private static void forgetOfflineViewers(List<ServerPlayer> players) {
        if (VIEWERS.size() <= players.size()) {
            return;
        }
        final Set<UUID> online = new HashSet<>(Math.max(4, players.size()));
        for (int i = 0; i < players.size(); i++) {
            online.add(players.get(i).getUUID());
        }
        VIEWERS.keySet().retainAll(online);
    }

    // ------------------------------------------------------------------ queries

    private static int countNear(@Nullable List<CitizenEntity> pool, ServerPlayer player) {
        if (pool == null) {
            return 0;
        }
        int count = 0;
        for (int i = 0; i < pool.size(); i++) {
            final CitizenEntity citizen = pool.get(i);
            if (!citizen.isRemoved() && citizen.distanceToSqr(player) <= CROWD_RADIUS_SQ) {
                count++;
            }
        }
        return count;
    }

    private static double nearestPlayerDistanceSq(CitizenEntity citizen, List<ServerPlayer> players) {
        double best = Double.MAX_VALUE;
        for (int i = 0; i < players.size(); i++) {
            best = Math.min(best, citizen.distanceToSqr(players.get(i)));
        }
        return best;
    }

    /**
     * The city the player is in, or the nearest one a couple of chunks away.
     *
     * <p>The ring search exists because a player standing on the pavement just outside the last
     * claimed chunk is, as far as anyone looking at the skyline is concerned, in the city. Each lookup
     * is a hash probe on the registry's chunk index, so scanning the ring costs nothing.
     */
    private static @Nullable City findCity(CityRegistry registry, ResourceKey<Level> dimension, BlockPos origin) {
        final ChunkPos centre = new ChunkPos(origin);
        final City here = registry.byChunk(dimension, centre);
        if (here != null) {
            return here;
        }
        for (int ring = 1; ring <= CITY_SEARCH_CHUNK_RADIUS; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                    }
                    final City city = registry.byChunk(dimension, new ChunkPos(centre.x + dx, centre.z + dz));
                    if (city != null) {
                        return city;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Registered buildings whose footprint comes within {@link #CROWD_RADIUS} of the player.
     *
     * <p>Goes through the registry's per-chunk building index rather than the city's building list:
     * a downtown city can own thousands of buildings and only the dozen around the player matter.
     */
    private static List<Building> nearbyBuildings(CityRegistry registry, ResourceKey<Level> dimension,
                                                  BlockPos origin) {
        final ChunkPos centre = new ChunkPos(origin);
        final List<Building> result = new ArrayList<>();
        final Set<UUID> seen = new HashSet<>();

        for (int dx = -BUILDING_CHUNK_RADIUS; dx <= BUILDING_CHUNK_RADIUS; dx++) {
            for (int dz = -BUILDING_CHUNK_RADIUS; dz <= BUILDING_CHUNK_RADIUS; dz++) {
                final List<Building> bucket =
                        registry.buildingsInChunk(dimension, new ChunkPos(centre.x + dx, centre.z + dz));
                for (int i = 0; i < bucket.size(); i++) {
                    final Building building = bucket.get(i);
                    // A building spans several chunks and appears in each of their buckets.
                    if (!seen.add(building.id()) || !withinCrowdRadius(building, origin)) {
                        continue;
                    }
                    result.add(building);
                    if (result.size() >= MAX_NEARBY_BUILDINGS) {
                        return result;
                    }
                }
            }
        }
        return result;
    }

    /** Distance to the nearest point of the building's box, so a tower counts from its base. */
    private static boolean withinCrowdRadius(Building building, BlockPos origin) {
        final long dx = axisGap(origin.getX(), building.min().getX(), building.max().getX());
        final long dy = axisGap(origin.getY(), building.min().getY(), building.max().getY());
        final long dz = axisGap(origin.getZ(), building.min().getZ(), building.max().getZ());
        return dx * dx + dy * dy + dz * dz <= (long) CROWD_RADIUS * CROWD_RADIUS;
    }

    /**
     * Gap between a coordinate and an inclusive interval, 0 when inside it.
     *
     * <p>Written out rather than using {@code Math.clamp} because that throws when {@code min > max},
     * and a {@code Building} constructed from unnormalised corners would turn a cosmetic subsystem
     * into a server crash.
     */
    private static long axisGap(int value, int min, int max) {
        if (value < min) {
            return min - (long) value;
        }
        if (value > max) {
            return value - (long) max;
        }
        return 0L;
    }

    // ------------------------------------------------------------------ state

    /** A live citizen paired with its distance to the nearest player, for the global cap sort. */
    private record Viewed(CitizenEntity citizen, double distanceSq) {
    }

    /** Per-player smoothing. Transient by design; nothing here is worth persisting. */
    private static final class ViewerState {

        private double smoothedTarget;

        /** Feed in this pass's raw budget, get back the number to actually aim for. */
        private int update(int rawTarget) {
            if (Math.abs(rawTarget - smoothedTarget) >= TARGET_SNAP_DELTA) {
                // A jump this large means the player moved somewhere else entirely; easing into it
                // would just be a visible delay.
                smoothedTarget = rawTarget;
            } else {
                smoothedTarget += (rawTarget - smoothedTarget) * TARGET_SMOOTHING;
            }
            return (int) Math.round(smoothedTarget);
        }
    }
}
