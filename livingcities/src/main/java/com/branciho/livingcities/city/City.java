package com.branciho.livingcities.city;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server-authoritative state for a single city.
 *
 * <p>This object is never sent to clients as-is. Clients receive purpose-built summaries so a city
 * with thousands of buildings never turns into a multi-megabyte packet.
 *
 * <p>Money is stored in <em>cents</em> as a {@code long}. Floating point money accumulates rounding
 * error across millions of simulation ticks, and a long cannot silently lose precision the way a
 * double does once a treasury passes 2^53.
 */
public final class City {

    private final UUID id;
    private final ResourceKey<Level> dimension;
    private final BlockPos corePos;

    private String name;
    private UUID owner;
    private long treasuryCents;

    /** Chunk keys ({@link ChunkPos#toLong}) owned by this city. */
    private final LongSet claimedChunks = new LongOpenHashSet();

    private final Map<UUID, CityRole> members = new HashMap<>();

    /** Ids of buildings registered to this city; the building objects live in the registry. */
    private final Set<UUID> buildingIds = new LinkedHashSet<>();

    private final CityStats stats = new CityStats();

    public City(UUID id, String name, UUID owner, ResourceKey<Level> dimension, BlockPos corePos, long startingTreasuryCents) {
        this.id = id;
        this.name = name;
        this.owner = owner;
        this.dimension = dimension;
        this.corePos = corePos.immutable();
        this.treasuryCents = startingTreasuryCents;
        this.members.put(owner, CityRole.MAYOR);
        this.claimedChunks.add(new ChunkPos(corePos).toLong());
    }

    private City(UUID id, ResourceKey<Level> dimension, BlockPos corePos) {
        this.id = id;
        this.dimension = dimension;
        this.corePos = corePos;
    }

    // ------------------------------------------------------------------ identity

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public UUID owner() {
        return owner;
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
        this.members.put(owner, CityRole.MAYOR);
    }

    public ResourceKey<Level> dimension() {
        return dimension;
    }

    public BlockPos corePos() {
        return corePos;
    }

    public CityStats stats() {
        return stats;
    }

    // ------------------------------------------------------------------ membership

    public CityRole roleOf(UUID player) {
        return members.get(player);
    }

    public void setRole(UUID player, CityRole role) {
        members.put(player, role);
    }

    public void removeMember(UUID player) {
        if (!player.equals(owner)) {
            members.remove(player);
        }
    }

    public Map<UUID, CityRole> members() {
        return members;
    }

    /**
     * Whether a player may perform an action requiring {@code required}.
     * Operator/creative bypass is handled by the caller, not here, so this stays a pure data check.
     */
    public boolean hasPermission(UUID player, CityRole required) {
        CityRole role = members.get(player);
        return role != null && role.atLeast(required);
    }

    // ------------------------------------------------------------------ territory

    public LongSet claimedChunks() {
        return claimedChunks;
    }

    public int claimCount() {
        return claimedChunks.size();
    }

    public boolean owns(ChunkPos pos) {
        return claimedChunks.contains(pos.toLong());
    }

    public void addClaim(ChunkPos pos) {
        claimedChunks.add(pos.toLong());
    }

    public void removeClaim(ChunkPos pos) {
        claimedChunks.remove(pos.toLong());
    }

    /** True if the chunk touches an already-claimed chunk (4-neighbourhood), which claims require. */
    public boolean isAdjacentToTerritory(ChunkPos pos) {
        return claimedChunks.contains(new ChunkPos(pos.x + 1, pos.z).toLong())
                || claimedChunks.contains(new ChunkPos(pos.x - 1, pos.z).toLong())
                || claimedChunks.contains(new ChunkPos(pos.x, pos.z + 1).toLong())
                || claimedChunks.contains(new ChunkPos(pos.x, pos.z - 1).toLong());
    }

    // ------------------------------------------------------------------ economy

    public long treasuryCents() {
        return treasuryCents;
    }

    public boolean canAfford(long cents) {
        return treasuryCents >= cents;
    }

    /** Withdraw, refusing (and reporting) rather than going negative. */
    public boolean withdraw(long cents) {
        if (cents < 0 || treasuryCents < cents) {
            return false;
        }
        treasuryCents -= cents;
        return true;
    }

    public void deposit(long cents) {
        if (cents <= 0) {
            return;
        }
        // Saturate instead of overflowing into a negative treasury.
        long result = treasuryCents + cents;
        this.treasuryCents = result < treasuryCents ? Long.MAX_VALUE : result;
    }

    // ------------------------------------------------------------------ buildings

    public Set<UUID> buildingIds() {
        return buildingIds;
    }

    // ------------------------------------------------------------------ serialization

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putString("Name", name);
        tag.putUUID("Owner", owner);
        tag.putString("Dimension", dimension.location().toString());
        tag.put("Core", NbtUtils.writeBlockPos(corePos));
        tag.putLong("Treasury", treasuryCents);
        tag.putLongArray("Claims", claimedChunks.toLongArray());

        ListTag memberList = new ListTag();
        members.forEach((uuid, role) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Player", uuid);
            entry.putString("Role", role.name());
            memberList.add(entry);
        });
        tag.put("Members", memberList);

        ListTag buildingList = new ListTag();
        buildingIds.forEach(buildingId -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", buildingId);
            buildingList.add(entry);
        });
        tag.put("Buildings", buildingList);

        tag.put("Stats", stats.save());
        return tag;
    }

    public static City load(CompoundTag tag) {
        UUID id = tag.getUUID("Id");
        ResourceKey<Level> dimension = ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION,
                ResourceLocation.parse(tag.getString("Dimension")));
        BlockPos core = NbtUtils.readBlockPos(tag, "Core").orElse(BlockPos.ZERO);

        City city = new City(id, dimension, core);
        city.name = tag.getString("Name");
        city.owner = tag.getUUID("Owner");
        city.treasuryCents = tag.getLong("Treasury");
        for (long key : tag.getLongArray("Claims")) {
            city.claimedChunks.add(key);
        }

        ListTag memberList = tag.getList("Members", Tag.TAG_COMPOUND);
        for (int i = 0; i < memberList.size(); i++) {
            CompoundTag entry = memberList.getCompound(i);
            city.members.put(entry.getUUID("Player"), CityRole.byName(entry.getString("Role"), CityRole.CITIZEN));
        }
        // An owner must always be a mayor, even if the save was edited or migrated.
        city.members.put(city.owner, CityRole.MAYOR);

        ListTag buildingList = tag.getList("Buildings", Tag.TAG_COMPOUND);
        for (int i = 0; i < buildingList.size(); i++) {
            city.buildingIds.add(buildingList.getCompound(i).getUUID("Id"));
        }

        city.stats.load(tag.getCompound("Stats"));
        return city;
    }
}
