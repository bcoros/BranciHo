package com.branciho.citiesinlife.net;

import com.branciho.citiesinlife.city.City;
import com.branciho.citiesinlife.city.CityData;
import com.branciho.citiesinlife.net.payload.ClaimChunkPayload;
import com.branciho.citiesinlife.net.payload.CitySyncPayload;
import com.branciho.citiesinlife.net.payload.DeleteStructurePayload;
import com.branciho.citiesinlife.net.payload.RegisterStructurePayload;
import com.branciho.citiesinlife.net.payload.StructureSyncPayload;
import com.branciho.citiesinlife.scan.StructureScanner;
import com.branciho.citiesinlife.structure.Floor;
import com.branciho.citiesinlife.structure.Structure;
import com.branciho.citiesinlife.structure.StructureType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

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
            if (!ownsGroundUnder(data, city, min, max)) {
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

        List<Floor> floors = StructureScanner.scan(level, min, max);
        String name = defaultName(type, data.structuresOf(city).size() + 1);
        Structure structure = Structure.create(city.id(), name, type, level.dimension(), min, max);
        structure.setFloors(floors);
        data.addStructure(city, structure);

        player.sendSystemMessage(Component.translatable(
                "message.citiesinlife.registered",
                name, floors.size(), structure.usableCells()));
        if (floors.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.citiesinlife.no_floors"));
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
        };
    }

    // ---------------------------------------------------------------- deleting

    public static void deleteStructure(ServerPlayer player, DeleteStructurePayload payload) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        CityData data = CityData.get(server);
        Structure structure = data.structure(payload.structureId());
        if (structure == null) {
            reject(player, "structure_missing");
            return;
        }
        City city = data.city(structure.cityId());
        if (city == null || (!city.owner().equals(player.getUUID()) && !player.hasPermissions(2))) {
            reject(player, "not_yours");
            return;
        }

        String name = structure.name();
        data.removeStructure(structure.id());
        player.sendSystemMessage(Component.translatable("message.citiesinlife.deleted", name));
        sync(player);
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
                        city.population(),
                        city.jobs(),
                        city.employed(),
                        city.nextClaimCost(),
                        city.claimedChunks().toLongArray()));

        CitiesInLifeNetwork.sendTo(player, new StructureSyncPayload(nearbyStructures(data, player)));
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

    // ----------------------------------------------------------------- helpers

    private static boolean tooFar(ServerPlayer player, BlockPos pos) {
        return player.blockPosition().distSqr(pos) > (double) MAX_REACH * MAX_REACH;
    }

    private static void reject(ServerPlayer player, String key) {
        player.sendSystemMessage(Component.translatable("message.citiesinlife." + key));
    }
}
