package com.branciho.citiesinlife.net;

import com.branciho.citiesinlife.city.City;
import com.branciho.citiesinlife.city.CityData;
import com.branciho.citiesinlife.net.payload.ClaimChunkPayload;
import com.branciho.citiesinlife.net.payload.CitySyncPayload;
import com.branciho.citiesinlife.net.payload.ConfirmDeleteCityPayload;
import com.branciho.citiesinlife.net.payload.DeleteAreaPayload;
import com.branciho.citiesinlife.net.payload.LinkPowerPayload;
import com.branciho.citiesinlife.net.payload.LinkWaterPayload;
import com.branciho.citiesinlife.net.payload.PowerLinesPayload;
import com.branciho.citiesinlife.net.payload.RegisterStructurePayload;
import com.branciho.citiesinlife.net.payload.StructureSyncPayload;
import com.branciho.citiesinlife.net.payload.WaterLinesPayload;
import com.branciho.citiesinlife.power.PowerBlock;
import com.branciho.citiesinlife.power.PowerGrid;
import com.branciho.citiesinlife.scan.StructureScanner;
import com.branciho.citiesinlife.sim.CitySimulation;
import com.branciho.citiesinlife.structure.MeasureMode;
import com.branciho.citiesinlife.structure.Structure;
import com.branciho.citiesinlife.structure.StructureType;
import com.branciho.citiesinlife.water.WaterBlock;
import com.branciho.citiesinlife.water.WaterGrid;
import com.branciho.citiesinlife.water.WaterRole;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Everything a client can ask the server to do, and every reason it might be told no.
 *
 * <p>Nothing here trusts the packet beyond "which two corners" and "which type". Ownership, cost,
 * overlap and capacity are all re-derived from server state, because a client that can be modified
 * is a client that will be.
 */
public final class ServerActions {

    /** How far from the player a selection corner may be, to stop remote edits across the world. */
    private static final int MAX_REACH = 256;

    /** How far around the player structures are sent for the overlay, in chunks. */
    private static final int SYNC_RADIUS_CHUNKS = 8;

    private static final int MIN_NAME_LENGTH = 2;
    private static final int MAX_NAME_LENGTH = 32;

    private ServerActions() {
    }

    // ------------------------------------------------------------- registering

    public static void registerStructure(ServerPlayer player, RegisterStructurePayload payload) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        CityData data = CityData.get(server);

        BlockPos a = payload.pointA();
        BlockPos b = payload.pointB();
        if (tooFar(player, a) || tooFar(player, b)) {
            reject(player, "too_far");
            return;
        }

        BlockPos min = new BlockPos(
                Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
        BlockPos max = new BlockPos(
                Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));

        String sizeProblem = StructureScanner.validate(min, max);
        if (sizeProblem != null) {
            reject(player, sizeProblem);
            return;
        }

        StructureType type = StructureType.byId(payload.typeId(), null);
        if (type == null) {
            reject(player, "unknown_type");
            return;
        }

        City city = data.cityOf(player.getUUID(), level.dimension());

        if (type == StructureType.CITY_CORE) {
            if (city != null) {
                reject(player, "already_have_city");
                return;
            }
            city = foundCity(player, data, level, payload.cityName(), min, max);
            if (city == null) {
                return;
            }
        } else {
            if (city == null) {
                reject(player, "no_city");
                return;
            }
            // Power plants are the one exception to owning the ground. A coal plant belongs at the
            // edge of a river or out where the smoke is somebody else's problem, and claiming a
            // corridor of chunks out to it before you can even mark the building would be a tax on
            // building it in the sensible place. Windmills and reactors will want the same.
            //
            // "Not your land" is still not the same as "anybody's land", though. ownsGroundUnder was
            // the only thing in this method that looked at territory at all, so exempting a plant
            // from it outright would let one player plant an undeletable building in the middle of
            // another player's city - undeletable because deleteArea only ever removes structures
            // belonging to the caller's own city.
            if (type == StructureType.POWER_PLANT) {
                if (standsOnAnotherCity(data, city, level, min, max)) {
                    reject(player, "another_city_land");
                    return;
                }
            } else if (!ownsGroundUnder(data, city, min, max)) {
                reject(player, "not_your_land");
                return;
            }
        }

        Structure overlap = data.overlapping(level.dimension(), min, max);
        if (overlap != null) {
            player.sendSystemMessage(Component.translatable(
                    "message.citiesinlife.overlaps", overlap.name()));
            return;
        }

        MeasureMode mode = MeasureMode.byId(payload.measureModeId(), MeasureMode.FLOORS);
        StructureScanner.Measurement measured = StructureScanner.measure(level, min, max, mode);

        String name = defaultName(type, data.structuresOf(city).size() + 1);
        Structure structure = Structure.create(city.id(), name, type, level.dimension(), min, max);
        structure.setMeasurement(mode, measured.floors(), measured.usableCells());
        data.addStructure(city, structure);

        // Bring the totals up to date now rather than at the next growth tick, so opening the city
        // panel straight afterwards shows what just happened instead of the previous numbers.
        CitySimulation.refresh(data, city);

        if (!type.measured()) {
            // A power plant is a marker, so residents and jobs would both read zero and look broken.
            // Say what it is actually for instead.
            player.sendSystemMessage(Component.translatable(
                    "message.citiesinlife.registered_plant", name));
            sync(player);
            return;
        }

        player.sendSystemMessage(Component.translatable(
                "message.citiesinlife.registered",
                name, structure.residents(), structure.jobs()));

        // Point at the other mode rather than just reporting nothing: an empty measurement is
        // almost always a shape the storey detector cannot read, not a mistake by the player.
        if (structure.usableCells() == 0) {
            player.sendSystemMessage(Component.translatable(mode == MeasureMode.FLOORS
                    ? "message.citiesinlife.no_floors"
                    : "message.citiesinlife.no_interior"));
        }
        sync(player);
    }

    /**
     * Found a city around its first structure.
     *
     * <p>The chunks the city core sits on are granted rather than bought — a city that cannot afford
     * the ground its own city hall stands on is not a situation worth modelling.
     */
    private static City foundCity(ServerPlayer player, CityData data, ServerLevel level,
                                  String requestedName, BlockPos min, BlockPos max) {
        String name = requestedName.trim();
        if (name.length() < MIN_NAME_LENGTH || name.length() > MAX_NAME_LENGTH) {
            reject(player, "bad_name");
            return null;
        }
        if (data.nameTaken(name)) {
            reject(player, "name_taken");
            return null;
        }

        for (int x = min.getX() >> 4; x <= max.getX() >> 4; x++) {
            for (int z = min.getZ() >> 4; z <= max.getZ() >> 4; z++) {
                City owner = data.cityAtChunk(level.dimension(), ChunkPos.asLong(x, z));
                if (owner != null) {
                    reject(player, "chunk_owned");
                    return null;
                }
            }
        }

        ChunkPos origin = new ChunkPos(min.getX() >> 4, min.getZ() >> 4);
        City city = data.createCity(name, player.getUUID(), level.dimension(), origin);
        for (int x = min.getX() >> 4; x <= max.getX() >> 4; x++) {
            for (int z = min.getZ() >> 4; z <= max.getZ() >> 4; z++) {
                data.claimChunk(city, ChunkPos.asLong(x, z));
            }
        }
        player.sendSystemMessage(Component.translatable("message.citiesinlife.founded", name));
        return city;
    }

    /**
     * Whether any of the ground under this box belongs to a city other than the builder's.
     *
     * <p>Unclaimed ground is fine — that is the point of the power plant exemption. Somebody else's
     * ground is not, and there is no other check anywhere that would catch it.
     */
    private static boolean standsOnAnotherCity(CityData data, City city, ServerLevel level,
                                               BlockPos min, BlockPos max) {
        for (int x = min.getX() >> 4; x <= max.getX() >> 4; x++) {
            for (int z = min.getZ() >> 4; z <= max.getZ() >> 4; z++) {
                City owner = data.cityAtChunk(level.dimension(), ChunkPos.asLong(x, z));
                if (owner != null && !owner.id().equals(city.id())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean ownsGroundUnder(CityData data, City city, BlockPos min, BlockPos max) {
        for (int x = min.getX() >> 4; x <= max.getX() >> 4; x++) {
            for (int z = min.getZ() >> 4; z <= max.getZ() >> 4; z++) {
                if (!city.owns(ChunkPos.asLong(x, z))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static String defaultName(StructureType type, int index) {
        return switch (type) {
            case CITY_CORE -> "City Hall";
            case RESIDENTIAL -> "Residence " + index;
            case COMMERCIAL -> "Shop " + index;
            case BUSINESS -> "Office " + index;
            case FACTORY -> "Factory " + index;
            case POWER_PLANT -> "Power Plant " + index;
        };
    }

    // ---------------------------------------------------------------- deleting

    /**
     * Remove every registration of the player's own city that the box touches.
     *
     * <p>By area rather than by pointing at one: a registration has no blocks, so aiming at it with
     * the crosshair means aiming at nothing, and it never worked reliably. Drawing a box is the same
     * gesture that created the registration in the first place.
     */
    public static void deleteArea(ServerPlayer player, DeleteAreaPayload payload) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        CityData data = CityData.get(server);
        City city = data.cityOf(player.getUUID(), level.dimension());
        if (city == null) {
            reject(player, "no_city");
            return;
        }

        BlockPos a = payload.pointA();
        BlockPos b = payload.pointB();
        if (tooFar(player, a) || tooFar(player, b)) {
            reject(player, "too_far");
            return;
        }
        BlockPos min = new BlockPos(
                Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
        BlockPos max = new BlockPos(
                Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));

        final List<Structure> doomed = new ArrayList<>();
        for (Structure structure : data.structuresOf(city)) {
            if (structure.dimension().equals(level.dimension()) && structure.intersects(min, max)) {
                doomed.add(structure);
            }
        }
        if (doomed.isEmpty()) {
            reject(player, "nothing_in_area");
            return;
        }

        // The city hall is not just another registration: taking it away takes the city with it, so
        // the box has to be answered for before anything happens.
        boolean takesTheCity = false;
        for (Structure structure : doomed) {
            if (structure.type() == StructureType.CITY_CORE) {
                takesTheCity = true;
                break;
            }
        }
        if (takesTheCity && !payload.confirmed()) {
            CitiesInLifeNetwork.sendTo(player, new ConfirmDeleteCityPayload(
                    a, b, city.name(), city.structures().size(),
                    city.claimedChunks().size(), city.treasury()));
            return;
        }

        if (takesTheCity) {
            int removed = data.deleteCity(city);
            player.sendSystemMessage(Component.translatable(
                    "message.citiesinlife.city_deleted", city.name(), removed));
            sync(player);
            return;
        }

        for (Structure structure : doomed) {
            data.removeStructure(structure.id());
        }
        CitySimulation.refresh(data, city);
        player.sendSystemMessage(Component.translatable(
                "message.citiesinlife.deleted_area", doomed.size()));
        sync(player);
    }

    // ------------------------------------------------------------------ power

    /**
     * Run or cut a power line between two blocks.
     *
     * <p>Range is the shorter of the two ends' reaches, so a mast's long throw only counts when it is
     * talking to another mast — which is what makes masts the thing you build a transmission run out
     * of rather than an upgrade to everything.
     */
    public static void linkPower(ServerPlayer player, LinkPowerPayload payload) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        ServerLevel level = player.serverLevel();

        BlockPos from = payload.from();
        BlockPos to = payload.to();
        if (tooFar(player, from) || tooFar(player, to)) {
            reject(player, "too_far");
            return;
        }

        BlockState fromState = level.getBlockState(from);
        BlockState toState = level.getBlockState(to);
        if (!(fromState.getBlock() instanceof PowerBlock fromBlock)
                || !(toState.getBlock() instanceof PowerBlock toBlock)) {
            reject(player, "not_power_block");
            return;
        }

        BlockPos fromNode = fromBlock.networkPos(level, from, fromState);
        BlockPos toNode = toBlock.networkPos(level, to, toState);
        if (fromNode.equals(toNode)) {
            reject(player, "same_node");
            return;
        }

        PowerGrid grid = PowerGrid.get(server);

        if (payload.disconnect()) {
            if (grid.unlink(level.dimension(), fromNode, toNode)) {
                player.sendSystemMessage(Component.translatable("message.citiesinlife.line_removed"));
                sync(player);
            } else {
                reject(player, "not_linked");
            }
            return;
        }

        int range = Math.min(fromBlock.linkRange(), toBlock.linkRange());
        double distance = Math.sqrt(fromNode.distSqr(toNode));
        if (distance > range) {
            player.sendSystemMessage(Component.translatable(
                    "message.citiesinlife.too_far_apart", (int) distance, range));
            return;
        }
        if (grid.linked(level.dimension(), fromNode, toNode)) {
            reject(player, "already_linked");
            return;
        }

        grid.link(level.dimension(), fromNode, toNode);
        player.sendSystemMessage(Component.translatable(
                "message.citiesinlife.line_built", (int) distance));
        sync(player);
    }

    // ------------------------------------------------------------------ water

    /**
     * Run or cut a pipe link between two blocks.
     *
     * <p>The rules about what may join what are the whole design of the water system, so they live
     * here in one place rather than being spread across the blocks. In short: pumps talk to pumps,
     * only the end pump reaches the pipework, and pipes are nobody's business but their own.
     */
    public static void linkWater(ServerPlayer player, LinkWaterPayload payload) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        ServerLevel level = player.serverLevel();

        BlockPos from = payload.from();
        BlockPos to = payload.to();
        if (tooFar(player, from) || tooFar(player, to)) {
            reject(player, "too_far");
            return;
        }

        BlockState fromState = level.getBlockState(from);
        BlockState toState = level.getBlockState(to);
        if (!(fromState.getBlock() instanceof WaterBlock fromBlock)
                || !(toState.getBlock() instanceof WaterBlock toBlock)) {
            reject(player, "not_water_block");
            return;
        }

        BlockPos fromNode = fromBlock.networkPos(level, from, fromState);
        BlockPos toNode = toBlock.networkPos(level, to, toState);
        if (fromNode.equals(toNode)) {
            reject(player, "same_node");
            return;
        }

        WaterGrid grid = WaterGrid.get(server);

        if (payload.disconnect()) {
            if (grid.unlink(level.dimension(), fromNode, toNode)) {
                player.sendSystemMessage(Component.translatable("message.citiesinlife.pipe_removed"));
                sync(player);
            } else {
                reject(player, "not_linked");
            }
            return;
        }

        String refusal = pairingProblem(fromBlock.waterRole(), toBlock.waterRole());
        if (refusal != null) {
            reject(player, refusal);
            return;
        }

        int range = Math.min(fromBlock.linkRange(), toBlock.linkRange());
        double distance = Math.sqrt(fromNode.distSqr(toNode));
        if (distance > range) {
            player.sendSystemMessage(Component.translatable(
                    "message.citiesinlife.too_far_apart", (int) distance, range));
            return;
        }
        if (grid.linked(level.dimension(), fromNode, toNode)) {
            reject(player, "already_linked");
            return;
        }

        // One starter pump per station. Checked when the link is drawn, because a second intake that
        // silently counts for nothing is a bug the player has no way to see. The survey walks the
        // pumps only - a station is the pump chain, not everything downstream of it - so a second
        // station on its own river may still feed the same city, and its water adds to the first.
        if (fromBlock.waterRole().isPump() && toBlock.waterRole().isPump()) {
            WaterGrid.Survey fromSide = grid.surveyStation(level, fromNode);
            if (!fromSide.reaches(toNode)) {
                // Two separate stations about to become one. If joining them would put two intakes
                // on the result, say so now. (A link inside one station is merely redundant.)
                WaterGrid.Survey toSide = grid.surveyStation(level, toNode);
                if (fromSide.sources() + toSide.sources() > 1) {
                    reject(player, "two_sources");
                    return;
                }
            }
        }

        grid.link(level.dimension(), fromNode, toNode);
        player.sendSystemMessage(Component.translatable(
                "message.citiesinlife.pipe_built", (int) distance));
        sync(player);
    }

    /**
     * Whether these two roles are allowed to be joined by hand, and why not if they are not.
     *
     * @return a message key, or null when the pair is fine
     */
    private static @Nullable String pairingProblem(WaterRole from, WaterRole to) {
        if (from == WaterRole.SOURCE && to == WaterRole.SOURCE) {
            return "two_sources";
        }
        if (from == WaterRole.CONDUIT && to == WaterRole.CONDUIT) {
            return "pipes_self_join";
        }
        if (from == WaterRole.STORAGE && to == WaterRole.STORAGE) {
            return "two_tanks";
        }
        if (from.isPump() && to.isPump()) {
            return null;
        }
        // Anything else is a pump meeting the pipework, and only the end pump may do that.
        boolean fromEnd = from == WaterRole.OUTLET;
        boolean toEnd = to == WaterRole.OUTLET;
        if (from.isPump() && !fromEnd) {
            return "only_end_pump";
        }
        if (to.isPump() && !toEnd) {
            return "only_end_pump";
        }
        return null;
    }

    // ---------------------------------------------------------------- claiming

    public static void claimChunk(ServerPlayer player, ClaimChunkPayload payload) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        CityData data = CityData.get(server);
        City city = data.cityOf(player.getUUID(), level.dimension());
        if (city == null) {
            reject(player, "no_city");
            return;
        }

        ChunkPos chunk = new ChunkPos(payload.chunkX(), payload.chunkZ());
        long key = chunk.toLong();

        if (!payload.claim()) {
            if (!data.unclaimChunk(city, key)) {
                reject(player, "cannot_unclaim");
                return;
            }
            player.sendSystemMessage(Component.translatable(
                    "message.citiesinlife.unclaimed", chunk.x, chunk.z));
            sync(player);
            return;
        }

        if (city.owns(key)) {
            reject(player, "already_claimed");
            return;
        }
        if (data.cityAtChunk(level.dimension(), key) != null) {
            reject(player, "chunk_owned");
            return;
        }
        if (!data.isAdjacentToClaim(city, chunk)) {
            reject(player, "not_adjacent");
            return;
        }
        long cost = city.nextClaimCost();
        if (!city.withdraw(cost)) {
            player.sendSystemMessage(Component.translatable("message.citiesinlife.cannot_afford", cost));
            return;
        }
        data.claimChunk(city, key);
        player.sendSystemMessage(Component.translatable(
                "message.citiesinlife.claimed", chunk.x, chunk.z, cost));
        sync(player);
    }

    // ---------------------------------------------------------------- syncing

    /**
     * Re-send only what is around the player: structures and power lines.
     *
     * <p>Separate from the full sync because this runs on a timer for every player. The city figures
     * change rarely and cost a chunk array each time; what is in view changes constantly as you walk.
     */
    public static void syncSurroundings(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        CitiesInLifeNetwork.sendTo(player,
                new StructureSyncPayload(nearbyStructures(CityData.get(server), player)));
        CitiesInLifeNetwork.sendTo(player, new PowerLinesPayload(nearbyLines(server, player)));
        CitiesInLifeNetwork.sendTo(player, new WaterLinesPayload(nearbyWaterLines(server, player)));
    }

    /** Push the player's city and the structures around them. */
    public static void sync(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        CityData data = CityData.get(server);
        City city = data.cityOf(player.getUUID(), level.dimension());

        CitiesInLifeNetwork.sendTo(player, city == null
                ? CitySyncPayload.none()
                : new CitySyncPayload(
                        true,
                        city.name(),
                        city.treasury(),
                        city.housing(),
                        city.population(),
                        city.jobs(),
                        city.employed(),
                        city.powerProduced(),
                        city.powerNeeded(),
                        city.waterSupplied(),
                        city.waterNeeded(),
                        city.nextClaimCost(),
                        city.claimedChunks().toLongArray()));

        CitiesInLifeNetwork.sendTo(player, new StructureSyncPayload(nearbyStructures(data, player)));
        CitiesInLifeNetwork.sendTo(player, new PowerLinesPayload(nearbyLines(server, player)));
        CitiesInLifeNetwork.sendTo(player, new WaterLinesPayload(nearbyWaterLines(server, player)));
    }

    private static List<StructureSyncPayload.Entry> nearbyStructures(CityData data, ServerPlayer player) {
        final List<StructureSyncPayload.Entry> entries = new ArrayList<>();
        final List<UUID> seen = new ArrayList<>();
        final ChunkPos centre = player.chunkPosition();

        for (int dx = -SYNC_RADIUS_CHUNKS; dx <= SYNC_RADIUS_CHUNKS; dx++) {
            for (int dz = -SYNC_RADIUS_CHUNKS; dz <= SYNC_RADIUS_CHUNKS; dz++) {
                long key = ChunkPos.asLong(centre.x + dx, centre.z + dz);
                for (Structure structure : data.structuresInChunk(player.serverLevel().dimension(), key)) {
                    // One entry per structure however many chunks it spans.
                    if (seen.contains(structure.id())) {
                        continue;
                    }
                    if (entries.size() >= StructureSyncPayload.MAX_STRUCTURES) {
                        return entries;
                    }
                    seen.add(structure.id());
                    entries.add(new StructureSyncPayload.Entry(
                            structure.id(),
                            structure.name(),
                            structure.type().id(),
                            structure.measureMode().id(),
                            structure.min().getX(), structure.min().getY(), structure.min().getZ(),
                            structure.max().getX(), structure.max().getY(), structure.max().getZ(),
                            structure.floorCount(),
                            structure.usableCells(),
                            structure.residents(),
                            structure.jobs()));
                }
            }
        }
        return entries;
    }

    /**
     * Power lines close enough to be worth drawing.
     *
     * <p>Filtered by distance to either end, because a line is worth seeing as soon as one of its
     * poles is in view even if the other end is far off across the desert.
     */
    /** The same for water. Kept separate so the client can draw them only while the tool is held. */
    private static List<long[]> nearbyWaterLines(MinecraftServer server, ServerPlayer player) {
        final int radius = SYNC_RADIUS_CHUNKS * 16 + 64;
        final BlockPos here = player.blockPosition();
        final List<long[]> visible = new ArrayList<>();

        for (long[] line : WaterGrid.get(server).allLines(player.serverLevel().dimension())) {
            if (visible.size() >= WaterLinesPayload.MAX_LINES) {
                break;
            }
            if (withinRadius(here, line[0], radius) || withinRadius(here, line[1], radius)) {
                visible.add(line);
            }
        }
        return visible;
    }

    private static List<long[]> nearbyLines(MinecraftServer server, ServerPlayer player) {
        final int radius = SYNC_RADIUS_CHUNKS * 16 + 64;
        final BlockPos here = player.blockPosition();
        final List<long[]> visible = new ArrayList<>();

        for (long[] line : PowerGrid.get(server).allLines(player.serverLevel().dimension())) {
            if (visible.size() >= PowerLinesPayload.MAX_LINES) {
                break;
            }
            if (withinRadius(here, line[0], radius) || withinRadius(here, line[1], radius)) {
                visible.add(line);
            }
        }
        return visible;
    }

    private static boolean withinRadius(BlockPos here, long packed, int radius) {
        int dx = BlockPos.getX(packed) - here.getX();
        int dz = BlockPos.getZ(packed) - here.getZ();
        return Math.abs(dx) <= radius && Math.abs(dz) <= radius;
    }

    // ----------------------------------------------------------------- helpers

    private static boolean tooFar(ServerPlayer player, BlockPos pos) {
        return player.blockPosition().distSqr(pos) > (double) MAX_REACH * MAX_REACH;
    }

    private static void reject(ServerPlayer player, String key) {
        player.sendSystemMessage(Component.translatable("message.citiesinlife." + key));
    }
}
