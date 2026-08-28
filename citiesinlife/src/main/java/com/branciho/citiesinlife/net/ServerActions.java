package com.branciho.citiesinlife.net;

import com.branciho.citiesinlife.city.City;
import com.branciho.citiesinlife.city.CityData;
import com.branciho.citiesinlife.city.Diplomacy;
import com.branciho.citiesinlife.city.Relation;
import com.branciho.citiesinlife.net.payload.ClaimChunkPayload;
import com.branciho.citiesinlife.net.payload.CitySyncPayload;
import com.branciho.citiesinlife.net.payload.ConfirmDeleteCityPayload;
import com.branciho.citiesinlife.net.payload.DeleteAreaPayload;
import com.branciho.citiesinlife.net.payload.DiplomacyPayload;
import com.branciho.citiesinlife.net.payload.ForeignLandPayload;
import com.branciho.citiesinlife.net.payload.LinkPowerPayload;
import com.branciho.citiesinlife.net.payload.LinkOutletPayload;
import com.branciho.citiesinlife.net.payload.LinkWaterPayload;
import com.branciho.citiesinlife.net.payload.ArmySyncPayload;
import com.branciho.citiesinlife.net.payload.MarkPathPayload;
import com.branciho.citiesinlife.net.payload.MarkRoadPayload;
import com.branciho.citiesinlife.net.payload.MilitaryActionPayload;
import com.branciho.citiesinlife.net.payload.NeighbourCitiesPayload;
import com.branciho.citiesinlife.net.payload.PathSyncPayload;
import com.branciho.citiesinlife.net.payload.RoadSyncPayload;
import com.branciho.citiesinlife.net.payload.PowerLinesPayload;
import com.branciho.citiesinlife.net.payload.RegisterStructurePayload;
import com.branciho.citiesinlife.net.payload.SeizeStructurePayload;
import com.branciho.citiesinlife.net.payload.StructureSyncPayload;
import com.branciho.citiesinlife.net.payload.WaterLinesPayload;
import com.branciho.citiesinlife.block.EndPipeBlock;
import com.branciho.citiesinlife.blockentity.EndPipeBlockEntity;
import com.branciho.citiesinlife.blockentity.ServiceSpawnerBlockEntity;
import com.branciho.citiesinlife.entity.ServiceEntity;
import com.branciho.citiesinlife.registry.ModEntities;
import com.branciho.citiesinlife.path.PathNetwork;
import com.branciho.citiesinlife.blockentity.TransportAirplaneBlockEntity;
import com.branciho.citiesinlife.block.TransportAirplaneBlock;
import com.branciho.citiesinlife.road.RoadNetwork;
import com.branciho.citiesinlife.road.RoadTile;
import com.branciho.citiesinlife.nuclear.ReactorFault;
import com.branciho.citiesinlife.nuclear.ReactorSurvey;
import com.branciho.citiesinlife.plant.PlantSurvey;
import com.branciho.citiesinlife.power.PowerBlock;
import com.branciho.citiesinlife.power.PowerGrid;
import com.branciho.citiesinlife.scan.StructureScanner;
import com.branciho.citiesinlife.sim.CitySimulation;
import com.branciho.citiesinlife.sim.CreativeFunding;
import com.branciho.citiesinlife.structure.MeasureMode;
import com.branciho.citiesinlife.structure.Structure;
import com.branciho.citiesinlife.structure.StructureType;
import com.branciho.citiesinlife.upgrade.Upgradeable;
import com.branciho.citiesinlife.net.payload.UpgradePayload;
import com.branciho.citiesinlife.water.WaterBlock;
import com.branciho.citiesinlife.water.WaterGrid;
import com.branciho.citiesinlife.water.WaterRole;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import it.unimi.dsi.fastutil.longs.LongArrayList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    /** How far around the player pavement is sent for the overlay. */
    private static final int PATH_SYNC_RADIUS = SYNC_RADIUS_CHUNKS * 16;

    /** The chunk each player was last sent pavement for, so it is not re-sent every tick. */
    private static final Map<UUID, Long> lastPathChunk = new HashMap<>();

    /** The chunk each player was last sent road for. Same reasoning as pavement. */
    private static final Map<UUID, Long> lastRoadChunk = new HashMap<>();

    /** Half-made airport links, per player, waiting for their second click. */
    private static final Map<UUID, BlockPos> pendingAirplaneLink = new HashMap<>();

    /**
     * How far around the player another city's land is sent for the map, in chunks.
     *
     * <p>Matched to what the map can actually draw plus a little slack. It was twice this, which
     * shipped roughly five times as many chunks as could ever appear on screen.
     */
    private static final int FOREIGN_LAND_RADIUS = 12;

    /**
     * What declaring war costs the treasury.
     *
     * <p>Without a price, a declaration is a permission switch: declare, help yourself to a
     * neighbour's town, stand down, repeat. It has to be a decision you can regret.
     */
    private static final long WAR_COST = 2_500L;

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
            if (type.isPlant()) {
                if (standsOnAnotherCity(data, city, level, min, max)) {
                    reject(player, "another_city_land");
                    return;
                }
            } else if (!ownsGroundUnder(data, city, min, max)) {
                reject(player, "not_your_land");
                return;
            }
        }

        // A plant is coal or wind, never both. Catching it here means the player is told when they
        // draw the box, rather than finding out later from a boiler that quietly refuses to light.
        if (type == StructureType.POWER_PLANT
                && PlantSurvey.of(level, min, max).kind() == PlantSurvey.Kind.MIXED) {
            reject(player, "mixed_generators");
            return;
        }

        // A reactor is refused at the moment the box is drawn if it is not a reactor. Every other
        // structure type can be registered around thin air and filled in later; this one cannot,
        // because a half-built core that registers happily and then sits inert is indistinguishable
        // from the feature being broken.
        if (type == StructureType.NUCLEAR_PLANT) {
            ReactorFault problem = ReactorSurvey.of(level, min, max).buildFault();
            if (problem != null) {
                player.sendSystemMessage(problem.describe());
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
            // A marker rather than a capacity: residents and jobs would both read zero and look
            // broken, so say what the building is actually for instead. There are three of these
            // now, and a power plant's message is no use for a park.
            player.sendSystemMessage(Component.translatable(switch (type) {
                case POWER_PLANT -> "message.citiesinlife.registered_plant";
                case NUCLEAR_PLANT -> "message.citiesinlife.registered_reactor";
                case PARK -> "message.citiesinlife.registered_park";
                case MILITARY_BASE -> "message.citiesinlife.registered_base";
                default -> "message.citiesinlife.registered_marker";
            }, name));
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
            case NUCLEAR_PLANT -> "Nuclear Plant " + index;
            case POLICE_STATION -> "Police Station " + index;
            case FIRE_STATION -> "Fire Station " + index;
            case HOSPITAL -> "Hospital " + index;
            case PARK -> "Park " + index;
            case GARBAGE_DEPOT -> "Depot " + index;
            case MILITARY_BASE -> "Barracks " + index;
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
            // Every other player's Neighbours list still has this city on it, with buttons that
            // now do nothing. They are cheap to refresh and expensive to leave stale.
            for (ServerPlayer other : server.getPlayerList().getPlayers()) {
                if (!other.getUUID().equals(player.getUUID())) {
                    syncNeighbours(other);
                }
            }
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

        // Cutting somebody's transmission line is griefing that leaves no trace and breaks no
        // block, so it has to be refused here rather than by the block rules.
        if (!Diplomacy.mayInterfere(server, player, from) || !Diplomacy.mayInterfere(server, player, to)) {
            reject(player, "protected_land_tool");
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
        // Same reasoning as the power line: cutting a city's water off is an edit to that city, and
        // it happens without a single block changing.
        if (!Diplomacy.mayInterfere(server, player, from) || !Diplomacy.mayInterfere(server, player, to)) {
            reject(player, "protected_land_tool");
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
        if (from == WaterRole.SEWAGE && to == WaterRole.SEWAGE) {
            return "two_collectors";
        }
        // A tank wired straight to a sewer is somebody about to drink their own drains. They can
        // still do it the long way round, through pipes, and the tap will go brown to tell them.
        if ((from == WaterRole.SEWAGE && to == WaterRole.STORAGE)
                || (from == WaterRole.STORAGE && to == WaterRole.SEWAGE)) {
            return "sewage_into_tank";
        }
        // Reactor ports get their rules written out rather than left to fall through, because this
        // method FAILS OPEN: any pair it does not name is permitted. A role added without rules is
        // silently linkable to everything, which is how you end up drinking reactor coolant.
        if (from == WaterRole.PORT || to == WaterRole.PORT) {
            WaterRole other = from == WaterRole.PORT ? to : from;
            if (other == WaterRole.STORAGE || other == WaterRole.SEWAGE) {
                return "port_into_supply";
            }
            // PORT to PORT is the one link the cooling loop actually needs. Which two ports is not
            // decided here - the survey checks that the water input reached a cooled output, and
            // says so by name if it did not. Roles alone cannot tell those apart.
            return null;
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
        // A city at war buys no land. Whatever it is going to hold at the end of this, it has to
        // take - otherwise the answer to being invaded is to buy a wall of chunks around yourself
        // faster than the other side can walk across them.
        if (!city.wars().isEmpty()) {
            reject(player, "at_war_no_land");
            return;
        }
        if (data.cityAtChunk(level.dimension(), key) != null) {
            reject(player, "chunk_owned");
            return;
        }
        // Somebody else's power plant is allowed to stand on unclaimed ground. Buying the ground out
        // from under it would lock its owner out of their own machinery without touching a block.
        if (data.foreignStructureInChunk(level.dimension(), key, city.id()) != null) {
            reject(player, "chunk_has_their_building");
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

    /**
     * Plumb an end pipe into something that holds buckets.
     *
     * <p>Its own gesture — sneak and left click with the Pipe Connect Tool — because the ordinary
     * one is a right click, and right-clicking a chest opens the chest. Two clicks that mean
     * different things are better than one click that sometimes means the wrong one.
     */
    public static void linkOutlet(ServerPlayer player, LinkOutletPayload payload) {
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
        if (!Diplomacy.mayInterfere(server, player, from) || !Diplomacy.mayInterfere(server, player, to)) {
            reject(player, "protected_land_tool");
            return;
        }

        // Either order. Which end is the tap is a fact about the world, not about the click.
        BlockPos tapPos;
        BlockPos containerPos;
        if (level.getBlockState(from).getBlock() instanceof EndPipeBlock) {
            tapPos = from;
            containerPos = to;
        } else if (level.getBlockState(to).getBlock() instanceof EndPipeBlock) {
            tapPos = to;
            containerPos = from;
        } else {
            reject(player, "no_end_pipe");
            return;
        }

        if (!(level.getBlockEntity(tapPos) instanceof EndPipeBlockEntity tap)) {
            reject(player, "no_end_pipe");
            return;
        }

        // Naming the same tap twice means "unplumb this one".
        if (tapPos.equals(containerPos)) {
            if (tap.outlet() == null) {
                reject(player, "outlet_not_linked");
                return;
            }
            tap.setOutlet(null);
            player.sendSystemMessage(Component.translatable("message.citiesinlife.outlet_unlinked"));
            return;
        }
        if (!(level.getBlockEntity(containerPos) instanceof Container)) {
            reject(player, "not_a_container");
            return;
        }
        double distance = Math.sqrt(tapPos.distSqr(containerPos));
        if (distance > EndPipeBlockEntity.LINK_RANGE) {
            player.sendSystemMessage(Component.translatable("message.citiesinlife.too_far_apart",
                    (int) distance, EndPipeBlockEntity.LINK_RANGE));
            return;
        }

        tap.setOutlet(containerPos);
        player.sendSystemMessage(Component.translatable("message.citiesinlife.outlet_linked"));
    }

    // ------------------------------------------------------------------ paths

    /**
     * Mark - or unmark - a box of ground as pavement.
     *
     * <p>Deliberately not tied to a city. Paths are not property: a road between two towns belongs
     * to neither of them, and a player who wants to draw one should not have to buy the ground it
     * crosses first. Nothing about a path grants or implies a claim, so there is nothing here worth
     * stealing.
     */
    public static void markPath(ServerPlayer player, MarkPathPayload payload) {
        MinecraftServer server = player.getServer();
        if (server == null) {
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

        // Marking pavement is an edit to how a city's people behave, so it answers to the same rule
        // as breaking a block there - it simply never touches a block, so the block events miss it.
        //
        // Every chunk the box spans, not just its two corners. Testing the corners alone meant a box
        // drawn wide around a city passed the check on the empty ground outside it and then wiped
        // every street inside.
        if (!mayEditWholeBox(server, player, min, max)) {
            reject(player, "protected_land_tool");
            return;
        }

        if (payload.remove()) {
            // See PathNetwork.ERASE_HEIGHT_SLACK: you paint where you mean, you erase where you
            // remember, and being a few blocks out in height is the ordinary case.
            BlockPos[] widened = widenForErase(min, max, PathNetwork.ERASE_HEIGHT_SLACK);
            min = widened[0];
            max = widened[1];
        }

        int changed = PathNetwork.get(server).mark(
                player.serverLevel().dimension(), min, max, payload.remove());
        if (changed == 0) {
            reject(player, payload.remove() ? "no_path_there" : "already_path");
        } else {
            player.sendSystemMessage(Component.translatable(payload.remove()
                    ? "message.citiesinlife.path_cleared"
                    : "message.citiesinlife.path_marked", changed));
        }
        syncPaths(player, true);
    }

    /**
     * Whether the player may edit every chunk a box touches.
     *
     * <p>Chunk by chunk rather than block by block: territory is granted per chunk, so one probe per
     * chunk is exact and a box the size of a city is still a handful of lookups.
     */
    private static boolean mayEditWholeBox(MinecraftServer server, ServerPlayer player,
                                           BlockPos min, BlockPos max) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = min.getX() >> 4; x <= max.getX() >> 4; x++) {
            for (int z = min.getZ() >> 4; z <= max.getZ() >> 4; z++) {
                cursor.set(x << 4, min.getY(), z << 4);
                if (!Diplomacy.mayInterfere(server, player, cursor)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Send the pavement around the player, but only when it can have changed.
     *
     * <p>Four thousand positions is thirty kilobytes, and pushing that at every player twice a
     * second for scenery that does not move would be the most expensive thing this mod does. It only
     * goes out when the player crosses into a new chunk or has just drawn some.
     */
    public static void syncPaths(ServerPlayer player, boolean force) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        long chunkKey = ChunkPos.asLong(player.chunkPosition().x, player.chunkPosition().z);
        Long last = lastPathChunk.get(player.getUUID());
        if (!force && last != null && last == chunkKey) {
            return;
        }
        lastPathChunk.put(player.getUUID(), chunkKey);

        LongArrayList near = PathNetwork.get(server).near(
                player.serverLevel().dimension(), player.blockPosition(),
                PATH_SYNC_RADIUS, PathSyncPayload.MAX_MARKED);
        CitiesInLifeNetwork.sendTo(player, new PathSyncPayload(near.toLongArray()));
    }

    // ------------------------------------------------------------------ roads

    /**
     * Paint - or clear - a box of ground as road, with a direction of travel on it.
     *
     * <p>Answers to the same rule as pavement, for the same reason: it changes how a city's people
     * behave without ever touching a block, so the block events miss it entirely and the check has
     * to be made here, chunk by chunk across the whole box.
     *
     * <p>Unlike pavement a road is not neutral ground. It is still not property - a road between two
     * towns belongs to neither - but a car will only follow one across someone else's border when it
     * is marked as a highway, which is decided in {@link RoadNetwork} and not here.
     */
    public static void markRoad(ServerPlayer player, MarkRoadPayload payload) {
        MinecraftServer server = player.getServer();
        if (server == null) {
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

        if (!mayEditWholeBox(server, player, min, max)) {
            reject(player, "protected_land_tool");
            return;
        }

        // Never trust the brush. A client can ask for any int; only the bits this mod understands
        // are stored, and a paint with no kind at all is refused rather than written as a blank tile
        // that the network could not tell from an empty one.
        int flags = payload.flags() & RoadTile.ALL;
        if (!payload.remove() && flags == 0) {
            reject(player, "road_no_kind");
            syncRoads(player, true);
            return;
        }

        if (payload.remove()) {
            BlockPos[] widened = widenForErase(min, max, RoadNetwork.ERASE_HEIGHT_SLACK);
            min = widened[0];
            max = widened[1];
        }

        int changed = RoadNetwork.get(server).mark(
                player.serverLevel().dimension(), min, max, flags, payload.remove());
        if (changed == 0) {
            reject(player, payload.remove() ? "no_road_there" : "already_road");
        } else {
            player.sendSystemMessage(Component.translatable(payload.remove()
                    ? "message.citiesinlife.road_cleared"
                    : "message.citiesinlife.road_marked", changed));
            if (!payload.remove()) {
                warnAboutDirection(player, min, max, flags);
            }
        }
        syncRoads(player, true);
    }

    /**
     * A box for erasing: the one the player drew, given room in height if it was flat.
     *
     * <p>Returned as a two-element array rather than a small record, because it is used twice and
     * nowhere else.
     */
    private static BlockPos[] widenForErase(BlockPos min, BlockPos max, int slack) {
        if (min.getY() != max.getY()) {
            return new BlockPos[]{min, max};
        }
        return new BlockPos[]{
                new BlockPos(min.getX(), min.getY() - slack, min.getZ()),
                new BlockPos(max.getX(), max.getY() + slack, max.getZ())};
    }

    /**
     * Say something when a street has been painted running the wrong way.
     *
     * <p>The brush defaults to a two-way north-south street, so painting an east-west road without
     * touching the direction buttons produces a road that is perfectly valid, drawn correctly on the
     * overlay, and completely unusable by a car. Nothing about that is visible from standing on it,
     * and the failure surfaces much later as "citizens ignore my roads".
     *
     * <p>Only for plain streets. A junction and a car park are passable every way by definition, so
     * there is nothing to get wrong.
     */
    private static void warnAboutDirection(ServerPlayer player, BlockPos min, BlockPos max, int flags) {
        if (RoadTile.is(flags, RoadTile.INTERSECTION) || RoadTile.is(flags, RoadTile.PARKING)) {
            return;
        }
        int spanX = max.getX() - min.getX();
        int spanZ = max.getZ() - min.getZ();
        // Only when the box is clearly a street rather than a square patch.
        if (Math.abs(spanX - spanZ) < 3) {
            return;
        }
        boolean runsEastWest = spanX > spanZ;
        boolean allowsEastWest = RoadTile.is(flags, RoadTile.EAST) || RoadTile.is(flags, RoadTile.WEST);
        boolean allowsNorthSouth = RoadTile.is(flags, RoadTile.NORTH) || RoadTile.is(flags, RoadTile.SOUTH);
        if (runsEastWest == allowsEastWest && runsEastWest != allowsNorthSouth) {
            return;
        }
        if (runsEastWest ? allowsEastWest : allowsNorthSouth) {
            return;
        }
        player.sendSystemMessage(Component.translatable(runsEastWest
                ? "message.citiesinlife.road_wrong_way_ew"
                : "message.citiesinlife.road_wrong_way_ns"));
    }

    /** Send the road around the player, on the same terms as pavement. */
    public static void syncRoads(ServerPlayer player, boolean force) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        long chunkKey = ChunkPos.asLong(player.chunkPosition().x, player.chunkPosition().z);
        Long last = lastRoadChunk.get(player.getUUID());
        if (!force && last != null && last == chunkKey) {
            return;
        }
        lastRoadChunk.put(player.getUUID(), chunkKey);

        RoadNetwork roads = RoadNetwork.get(server);
        LongArrayList near = roads.near(player.serverLevel().dimension(), player.blockPosition(),
                PATH_SYNC_RADIUS, RoadSyncPayload.MAX_TILES);
        CitiesInLifeNetwork.sendTo(player, new RoadSyncPayload(
                near.toLongArray(), roads.flagsFor(player.serverLevel().dimension(), near)));
    }

    // -------------------------------------------------------------- airfields

    /**
     * One gesture: link a pair of airports, or fly between an already linked pair.
     *
     * <p>Which of the two it is depends on the state of the block clicked, not on a modifier key.
     * See {@link TransportAirplaneBlock} for why there is no sneak in this - a sneaking player
     * holding anything never reaches the block at all.
     */
    public static void useAirplane(ServerPlayer player, BlockPos pos) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (!(level.getBlockEntity(pos) instanceof TransportAirplaneBlockEntity here)) {
            return;
        }

        // An airport nobody is accountable for does nothing, the same as it spawns nobody.
        if (CityData.get(server).airfieldOwner(level.dimension(), pos) == null) {
            reject(player, "airplane_uncounted");
            return;
        }

        BlockPos partner = here.partner();
        if (partner != null) {
            fly(player, level, partner);
            return;
        }

        // Linking is an edit to somebody's build, so it answers to the same rule as breaking a
        // block there.
        if (!Diplomacy.mayInterfere(server, player, pos)) {
            reject(player, "protected_land_tool");
            return;
        }

        BlockPos pending = pendingAirplaneLink.get(player.getUUID());
        if (pending == null) {
            pendingAirplaneLink.put(player.getUUID(), pos);
            player.displayClientMessage(
                    Component.translatable("message.citiesinlife.airplane_link_started"), true);
            return;
        }
        if (pending.equals(pos)) {
            pendingAirplaneLink.remove(player.getUUID());
            reject(player, "airplane_same_block");
            return;
        }
        if (!(level.getBlockEntity(pending) instanceof TransportAirplaneBlockEntity other)) {
            pendingAirplaneLink.remove(player.getUUID());
            reject(player, "airplane_not_linkable");
            return;
        }

        here.setPartner(pending);
        other.setPartner(pos);
        pendingAirplaneLink.remove(player.getUUID());
        player.sendSystemMessage(Component.translatable("message.citiesinlife.airplane_linked"));
    }

    /**
     * Put the player down at the far end.
     *
     * <p>Re-checked at the moment of use rather than trusted from when the link was made. Ground
     * changes hands, and a pad built on land somebody has since claimed must not be a way in.
     */
    private static void fly(ServerPlayer player, ServerLevel level, BlockPos partner) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        if (!level.isLoaded(partner)
                || !(level.getBlockState(partner).getBlock() instanceof TransportAirplaneBlock)) {
            reject(player, "airplane_far_end_gone");
            return;
        }
        if (!Diplomacy.mayInterfere(server, player, partner)) {
            reject(player, "airplane_far_end_protected");
            return;
        }
        BlockPos arrival = TransportAirplaneBlockEntity.landingSpot(level, partner);
        if (arrival == null) {
            reject(player, "airplane_no_room");
            return;
        }
        // The six-argument ServerPlayer override, which is the one that tells the client it moved.
        player.teleportTo(level, arrival.getX() + 0.5D, arrival.getY(), arrival.getZ() + 0.5D,
                player.getYRot(), player.getXRot());
    }

    /**
     * Turn this player's creative treasury off, or back on.
     *
     * <p>Answered on the action bar rather than in chat. It is a switch you flip while building and
     * a line of chat for every press would be noise.
     */
    public static void toggleCreativeMoney(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        boolean enabled = CityData.get(server).toggleCreativeMoney(player.getUUID());
        CreativeFunding.sync(server);
        sync(player);
        player.displayClientMessage(Component.translatable(enabled
                ? "hud.citiesinlife.creative_money_on"
                : "hud.citiesinlife.creative_money_off"), true);
    }

    // ---------------------------------------------------------------- conquest

    /**
     * Take somebody else's building.
     *
     * <p>The only test is whether the chunk it stands in belongs to the taker, and that is a
     * stronger rule than it sounds: a chunk with a foreign building on it cannot be bought, so the
     * only way to be standing in one you own with somebody else's building in it is to have taken it
     * with soldiers.
     *
     * <p>A city hall is never on the table. Taking one would delete a player's entire city from
     * under them with a single click, and there is no version of that which is not a disaster.
     */
    public static void seizeStructure(ServerPlayer player, SeizeStructurePayload payload) {
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
        City city = data.cityOf(player.getUUID(), level.dimension());
        if (city == null) {
            reject(player, "no_city");
            return;
        }

        BlockPos min = new BlockPos(
                Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
        BlockPos max = new BlockPos(
                Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));

        Structure target = foreignStructureIn(data, level, city, min, max);
        if (target == null) {
            reject(player, "nothing_to_seize");
            return;
        }
        if (target.type() == StructureType.CITY_CORE) {
            reject(player, "cannot_seize_hall");
            return;
        }
        if (!conquered(data, level, city, target)) {
            reject(player, "not_conquered");
            return;
        }

        StructureType type = payload.typeId().isEmpty()
                ? target.type()
                : StructureType.byId(payload.typeId(), null);
        if (type == null || type == StructureType.CITY_CORE) {
            reject(player, "unknown_type");
            return;
        }
        // Seizing rewrites a building's type without running ANY of the checks registerStructure
        // does - not the land test, not the mixed-generator test, and not the reactor's build
        // validation. Rewriting a captured shed into a nuclear plant would therefore conjure one
        // that never had to be a reactor at all. The city hall is already refused here for the
        // same shape of reason.
        if (type == StructureType.NUCLEAR_PLANT && target.type() != StructureType.NUCLEAR_PLANT) {
            reject(player, "cannot_seize_into_reactor");
            return;
        }

        City loser = data.city(target.cityId());
        data.removeStructure(target.id());

        MeasureMode mode = target.measureMode();
        StructureScanner.Measurement measured =
                StructureScanner.measure(level, target.min(), target.max(), mode);
        String name = defaultName(type, data.structuresOf(city).size() + 1);
        Structure taken = Structure.create(
                city.id(), name, type, level.dimension(), target.min(), target.max());
        taken.setMeasurement(mode, measured.floors(), measured.usableCells());
        data.addStructure(city, taken);

        CitySimulation.refresh(data, city);
        if (loser != null) {
            CitySimulation.refresh(data, loser);
            tell(server, loser, "message.citiesinlife.building_lost", city.name());
        }

        player.sendSystemMessage(Component.translatable("message.citiesinlife.seized",
                target.name(), type.displayName()));
        sync(player);
    }

    /** A registered building inside this box that belongs to somebody else. */
    private static @Nullable Structure foreignStructureIn(CityData data, ServerLevel level, City city,
                                                          BlockPos min, BlockPos max) {
        for (int x = min.getX() >> 4; x <= max.getX() >> 4; x++) {
            for (int z = min.getZ() >> 4; z <= max.getZ() >> 4; z++) {
                for (Structure structure : data.structuresInChunk(level.dimension(), ChunkPos.asLong(x, z))) {
                    if (!structure.cityId().equals(city.id()) && structure.intersects(min, max)) {
                        return structure;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Whether this city holds the ground the building stands on.
     *
     * <p>Judged by the middle of the building rather than by every chunk it touches. A tower that
     * straddles a boundary would otherwise be untakeable until both sides of the street had fallen,
     * which reads as the wand being broken rather than as a rule.
     */
    private static boolean conquered(CityData data, ServerLevel level, City city, Structure target) {
        int x = (target.min().getX() + target.max().getX()) / 2;
        int z = (target.min().getZ() + target.max().getZ()) / 2;
        City here = data.cityAtChunk(level.dimension(), ChunkPos.asLong(x >> 4, z >> 4));
        return here != null && here.id().equals(city.id());
    }

    // ---------------------------------------------------------------- military

    /**
     * Names for people the player has just paid for.
     *
     * <p>A list rather than "Soldier 1", "Soldier 2", because the Military Tool is the only screen
     * in the mod where the player makes decisions about individuals — firing one, arming one,
     * sending one on a course — and a numbered list makes every one of those decisions feel like
     * moving a counter.
     */
    private static final String[] SOLDIER_NAMES = {
        "Adams", "Bailey", "Cortez", "Dunn", "Eriksen", "Faro", "Gill", "Hoxha",
        "Ivarsen", "Jelinek", "Kovac", "Lang", "Mireles", "Novak", "Osei", "Petrov",
        "Quinn", "Rao", "Sokol", "Tamm", "Ustinov", "Varga", "Whyte", "Zeman"
    };

    /** Push the army roll to whoever is looking at it. */
    public static void syncArmy(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        City city = CityData.get(server).cityOf(player.getUUID(), player.level().dimension());
        if (city == null) {
            CitiesInLifeNetwork.sendTo(player, ArmySyncPayload.none());
            return;
        }

        long now = player.level().getGameTime();
        List<ArmySyncPayload.Entry> roll = new ArrayList<>();
        for (City.Soldier soldier : city.army()) {
            int secondsLeft = soldier.inTraining()
                    ? (int) Math.max(0L, (soldier.trainingDoneAt() - now) / 20L)
                    : 0;
            roll.add(new ArmySyncPayload.Entry(soldier.id(), soldier.name(), soldier.training(),
                    weaponName(soldier), secondsLeft));
        }

        CitiesInLifeNetwork.sendTo(player, new ArmySyncPayload(
                hasBase(server, city, player.level().dimension()),
                city.treasury(), City.HIRE_COST, City.TRAIN_COST, City.MAX_ARMY, roll));
    }

    /** What to print next to a soldier's name. Their own hands, if they have been given nothing. */
    private static String weaponName(City.Soldier soldier) {
        ItemStack held = ServiceSpawnerBlockEntity.weaponOf(soldier);
        return held.isEmpty() ? "" : held.getHoverName().getString();
    }

    private static boolean hasBase(MinecraftServer server, City city, ResourceKey<Level> dimension) {
        for (Structure structure : CityData.get(server).structuresOf(city)) {
            if (structure.type() == StructureType.MILITARY_BASE
                    && structure.dimension().equals(dimension)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Hire, dismiss, arm or train.
     *
     * <p>Every one of these re-checks the city, the base and the money server-side. The screen is a
     * convenience; it is not the authority on whether the player can afford a soldier.
     */
    public static void militaryAction(ServerPlayer player, MilitaryActionPayload payload) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        CityData data = CityData.get(server);
        ResourceKey<Level> dimension = player.level().dimension();
        City city = data.cityOf(player.getUUID(), dimension);
        if (city == null) {
            reject(player, "no_city");
            return;
        }
        if (!hasBase(server, city, dimension)) {
            reject(player, "no_military_base");
            return;
        }

        switch (payload.action()) {
            case HIRE -> hire(player, data, city);
            case DISMISS -> dismiss(player, data, city, payload.soldier());
            case TRAIN -> train(player, data, city, payload.soldier());
            case ARM -> arm(player, data, city, payload.soldier());
        }
        syncArmy(player);
        sync(player);
    }

    private static void hire(ServerPlayer player, CityData data, City city) {
        if (city.army().size() >= City.MAX_ARMY) {
            reject(player, "army_full");
            return;
        }
        if (!city.withdraw(City.HIRE_COST)) {
            player.sendSystemMessage(
                    Component.translatable("message.citiesinlife.cannot_afford", City.HIRE_COST));
            return;
        }
        String name = SOLDIER_NAMES[player.level().random.nextInt(SOLDIER_NAMES.length)];
        city.enlist(new City.Soldier(UUID.randomUUID(), name, 0, "", 0L));
        data.setDirty();
        player.sendSystemMessage(Component.translatable("message.citiesinlife.hired", name));
    }

    private static void dismiss(ServerPlayer player, CityData data, City city, UUID id) {
        City.Soldier soldier = city.soldier(id);
        if (soldier == null) {
            return;
        }
        city.discharge(id);
        data.setDirty();
        // The body goes with the record. The barracks would sweep it up on its next pass anyway,
        // but a soldier who keeps standing there after being fired reads as the button not working.
        for (ServiceEntity body : soldiersOf(player, id)) {
            body.discard();
        }
        player.sendSystemMessage(Component.translatable("message.citiesinlife.dismissed", soldier.name()));
    }

    private static void train(ServerPlayer player, CityData data, City city, UUID id) {
        City.Soldier soldier = city.soldier(id);
        if (soldier == null) {
            return;
        }
        if (soldier.inTraining()) {
            reject(player, "already_training");
            return;
        }
        if (soldier.training() >= ServiceEntity.MAX_TRAINING) {
            reject(player, "fully_trained");
            return;
        }
        if (!city.withdraw(City.TRAIN_COST)) {
            player.sendSystemMessage(
                    Component.translatable("message.citiesinlife.cannot_afford", City.TRAIN_COST));
            return;
        }
        city.replace(soldier.startingCourse(player.level().getGameTime() + City.TRAIN_TICKS));
        data.setDirty();
        player.sendSystemMessage(
                Component.translatable("message.citiesinlife.training_started", soldier.name()));
    }

    /**
     * Hand a soldier whatever the player is holding in their off hand.
     *
     * <p>The off hand rather than a slot in the screen, because the whole point of this is that the
     * weapon might come from a mod this build has never heard of — anything that can be held can be
     * handed over, and no inventory widget has to know what it is.
     */
    private static void arm(ServerPlayer player, CityData data, City city, UUID id) {
        City.Soldier soldier = city.soldier(id);
        if (soldier == null) {
            return;
        }
        ItemStack offering = player.getOffhandItem();
        if (offering.isEmpty()) {
            reject(player, "nothing_to_give");
            return;
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(offering.getItem());
        city.replace(soldier.withWeapon(itemId.toString()));
        data.setDirty();

        ItemStack given = offering.split(1);
        for (ServiceEntity body : soldiersOf(player, id)) {
            body.setItemSlot(EquipmentSlot.MAINHAND, given.copy());
            body.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        }
        player.sendSystemMessage(Component.translatable("message.citiesinlife.armed",
                soldier.name(), given.getHoverName()));
    }

    /** Every body currently standing about that belongs to one entry on the roll. */
    private static List<ServiceEntity> soldiersOf(ServerPlayer player, UUID soldierId) {
        return new ArrayList<>(player.serverLevel().getEntities(ModEntities.SERVICE.get(),
                entity -> entity.isAlive() && soldierId.equals(entity.soldierId())));
    }

    /** Forget a player who has left, so this map does not grow for the life of the server. */
    public static void forget(ServerPlayer player) {
        lastPathChunk.remove(player.getUUID());
        lastRoadChunk.remove(player.getUUID());
        pendingAirplaneLink.remove(player.getUUID());
    }

    // ------------------------------------------------------------- diplomacy

    /**
     * Grant, revoke, declare war, or stand down.
     *
     * <p>Nothing here trusts the packet past "which city" and "which of four things". Whether the
     * sender has any standing to do it is re-derived from server state, because a screen that can be
     * modified is a screen that will be.
     */
    public static void diplomacy(ServerPlayer player, DiplomacyPayload payload) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        CityData data = CityData.get(server);
        City own = data.cityOf(player.getUUID(), player.serverLevel().dimension());
        if (own == null) {
            reject(player, "no_city");
            return;
        }
        City target = data.city(payload.targetCityId());
        // A city in another dimension can neither be seen on this player's Neighbours list nor
        // answer back, so a crafted packet naming one would write a war nobody could ever end.
        if (target == null || target.id().equals(own.id())
                || !target.dimension().equals(own.dimension())) {
            reject(player, "unknown_city");
            return;
        }

        boolean changed = switch (payload.action()) {
            case DiplomacyPayload.ACTION_GRANT -> {
                // Being at war and being welcome are not compatible, and letting somebody grant
                // permission mid-war would read as a truce that the war rules then ignore.
                if (Diplomacy.stance(own, target) == Relation.WAR) {
                    reject(player, "at_war_cannot_grant");
                    yield false;
                }
                if (own.grant(target.id())) {
                    tell(server, target, "message.citiesinlife.granted_by", own.name());
                    player.sendSystemMessage(Component.translatable(
                            "message.citiesinlife.granted_to", target.name()));
                    yield true;
                }
                yield false;
            }
            case DiplomacyPayload.ACTION_REVOKE -> {
                if (own.revoke(target.id())) {
                    tell(server, target, "message.citiesinlife.revoked_by", own.name());
                    player.sendSystemMessage(Component.translatable(
                            "message.citiesinlife.revoked_from", target.name()));
                    yield true;
                }
                yield false;
            }
            case DiplomacyPayload.ACTION_DECLARE_WAR -> {
                // A war cancels any welcome, both ways. Otherwise an attacker keeps the run of the
                // place they granted themselves access to before declaring.
                if (!own.canAfford(WAR_COST)) {
                    player.sendSystemMessage(Component.translatable(
                            "message.citiesinlife.war_too_expensive", WAR_COST));
                    yield false;
                }
                own.revoke(target.id());
                target.revoke(own.id());
                if (own.declareWar(target.id())) {
                    own.withdraw(WAR_COST);
                    tell(server, target, "message.citiesinlife.war_declared_on_you", own.name());
                    player.sendSystemMessage(Component.translatable(
                            "message.citiesinlife.war_declared", target.name()));
                    yield true;
                }
                yield false;
            }
            case DiplomacyPayload.ACTION_MAKE_PEACE -> {
                // Ending a war clears both sides. Requiring each of them to stand down separately
                // read as a broken button to whichever city was attacked: it had nothing of its own
                // to withdraw, so its only option did nothing at all. The cost of declaring is what
                // keeps war from being spammed; making peace hard to reach never did.
                boolean ended = own.makePeace(target.id()) | target.makePeace(own.id());
                if (ended) {
                    tell(server, target, "message.citiesinlife.peace_agreed", own.name());
                    player.sendSystemMessage(Component.translatable(
                            "message.citiesinlife.peace_agreed", target.name()));
                    yield true;
                }
                yield false;
            }
            default -> false;
        };

        if (changed) {
            data.setDirty();
            syncNeighbours(player);
            // The other side's screens are looking at numbers that have just changed under them.
            ServerPlayer other = server.getPlayerList().getPlayer(target.owner());
            if (other != null) {
                syncNeighbours(other);
            }
        }
    }

    /**
     * Tell a player about any war they are in, as they arrive.
     *
     * <p>A declaration made while somebody was offline is otherwise silently dropped, which defeats
     * the whole reason a declaration is announced at all. Derived from state rather than queued, so
     * there is no message backlog to persist and nothing to go stale.
     */
    public static void greetWithWars(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        CityData data = CityData.get(server);
        for (City own : data.cities()) {
            if (!own.owner().equals(player.getUUID())) {
                continue;
            }
            for (City other : data.cities()) {
                if (other.id().equals(own.id())) {
                    continue;
                }
                if (Diplomacy.stance(own, other) == Relation.WAR) {
                    player.sendSystemMessage(Component.translatable(
                            "message.citiesinlife.still_at_war", own.name(), other.name()));
                }
            }
        }
    }

    /** Tell a city's owner something, if they happen to be online to hear it. */
    private static void tell(MinecraftServer server, City city, String key, Object argument) {
        ServerPlayer owner = server.getPlayerList().getPlayer(city.owner());
        if (owner != null) {
            owner.sendSystemMessage(Component.translatable(key, argument));
        }
    }

    /** The other cities in this dimension, and the land they hold near the player. */
    public static void syncNeighbours(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        CityData data = CityData.get(server);
        City own = data.cityOf(player.getUUID(), player.serverLevel().dimension());
        ChunkPos here = player.chunkPosition();

        List<NeighbourCitiesPayload.Entry> entries = new ArrayList<>();
        LongArrayList chunks = new LongArrayList();
        List<Byte> stances = new ArrayList<>();

        for (City city : data.cities()) {
            if (!city.dimension().equals(player.serverLevel().dimension())) {
                continue;
            }
            if (own != null && city.id().equals(own.id())) {
                continue;
            }
            if (city.owner().equals(player.getUUID())) {
                // Their own city in another dimension has already been skipped; this catches the
                // case of a player who somehow owns two here.
                continue;
            }

            Relation theirs = Diplomacy.stance(city, own);
            Relation yours = Diplomacy.stance(own, city);

            if (entries.size() < NeighbourCitiesPayload.MAX_CITIES) {
                entries.add(new NeighbourCitiesPayload.Entry(
                        city.id(),
                        city.name(),
                        nameOf(server, city.owner()),
                        theirs.ordinal(),
                        yours.ordinal(),
                        city.claimedChunks().size(),
                        distanceInChunks(here, city)));
            }

            for (long chunkKey : city.claimedChunks()) {
                if (chunks.size() >= ForeignLandPayload.MAX_CHUNKS) {
                    break;
                }
                if (Math.abs(ChunkPos.getX(chunkKey) - here.x) > FOREIGN_LAND_RADIUS
                        || Math.abs(ChunkPos.getZ(chunkKey) - here.z) > FOREIGN_LAND_RADIUS) {
                    continue;
                }
                chunks.add(chunkKey);
                stances.add((byte) theirs.ordinal());
            }
        }

        byte[] packedStances = new byte[stances.size()];
        for (int i = 0; i < packedStances.length; i++) {
            packedStances[i] = stances.get(i);
        }
        CitiesInLifeNetwork.sendTo(player, new NeighbourCitiesPayload(entries));
        CitiesInLifeNetwork.sendTo(player, new ForeignLandPayload(chunks.toLongArray(), packedStances));
    }

    /** How far the nearest bit of a city's territory is, in chunks; -1 if it holds none. */
    private static int distanceInChunks(ChunkPos here, City city) {
        int best = -1;
        for (long chunkKey : city.claimedChunks()) {
            int dx = ChunkPos.getX(chunkKey) - here.x;
            int dz = ChunkPos.getZ(chunkKey) - here.z;
            int distance = Math.max(Math.abs(dx), Math.abs(dz));
            if (best < 0 || distance < best) {
                best = distance;
            }
        }
        return best;
    }

    private static String nameOf(MinecraftServer server, UUID playerId) {
        ServerPlayer online = server.getPlayerList().getPlayer(playerId);
        if (online != null) {
            return online.getGameProfile().getName();
        }
        GameProfileCache cache = server.getProfileCache();
        if (cache != null) {
            return cache.get(playerId).map(profile -> profile.getName())
                    .orElse("?");
        }
        return "?";
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
        syncPaths(player, false);
        syncRoads(player, false);
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
                        city.sewageHandled(),
                        city.sewageProduced(),
                        city.nextClaimCost(),
                        city.refuse(),
                        city.refuseTolerance(),
                        city.creativeFunded(),
                        city.claimedChunks().toLongArray()));

        CitiesInLifeNetwork.sendTo(player, new StructureSyncPayload(nearbyStructures(data, player)));
        CitiesInLifeNetwork.sendTo(player, new PowerLinesPayload(nearbyLines(server, player)));
        CitiesInLifeNetwork.sendTo(player, new WaterLinesPayload(nearbyWaterLines(server, player)));
        syncPaths(player, true);
        syncRoads(player, true);
        syncNeighbours(player);
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

    // --------------------------------------------------------------- upgrades

    /**
     * Buy a machine one level up, paid for out of the city's treasury.
     *
     * <p>The money comes from the city whose ground the machine stands on, not from the city the
     * player happens to run. That matters on a shared server: upgrading somebody else's turbine
     * out of their own treasury would be a very odd kind of gift.
     */
    public static void upgrade(ServerPlayer player, UpgradePayload payload) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        BlockPos pos = payload.pos();
        if (tooFar(player, pos)) {
            reject(player, "too_far");
            return;
        }
        if (!Diplomacy.mayInterfere(server, player, pos)) {
            reject(player, "protected_land_tool");
            return;
        }

        ServerLevel level = player.serverLevel();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof Upgradeable machine)) {
            reject(player, "not_upgradeable");
            return;
        }

        int tier = machine.tierAt(level, pos, state);
        if (tier >= machine.maxTier()) {
            player.sendSystemMessage(Component.translatable(
                    "message.citiesinlife.already_top_tier", tier + 1));
            return;
        }

        CityData data = CityData.get(server);
        City city = data.cityAtChunk(level.dimension(),
                ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
        if (city == null) {
            reject(player, "upgrade_needs_city");
            return;
        }

        long cost = machine.upgradeCost(tier);
        if (!city.withdraw(cost)) {
            player.sendSystemMessage(Component.translatable(
                    "message.citiesinlife.upgrade_too_dear", cost, city.treasury()));
            return;
        }

        if (!machine.upgrade(level, pos, state)) {
            // Refund rather than swallow. A machine that took the money and did nothing would be
            // indistinguishable from one that worked, right up until the numbers did not move.
            city.deposit(cost);
            reject(player, "upgrade_failed");
            return;
        }
        data.setDirty();

        player.sendSystemMessage(machine.describe(level, pos, level.getBlockState(pos)));
        player.sendSystemMessage(Component.translatable("message.citiesinlife.upgrade_paid", cost));
        sync(player);
    }

    private static void reject(ServerPlayer player, String key) {
        player.sendSystemMessage(Component.translatable("message.citiesinlife." + key));
    }
}
