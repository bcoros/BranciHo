package com.branciho.citiesinlife.city;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A city: an owner, some money, some claimed ground and the structures standing on it.
 *
 * <p>Money is held in whole units rather than fractions of one. There is no need for cents here and
 * a long counts far past any treasury a player will ever accumulate.
 */
public final class City {

    /** What a city starts with, enough to claim a few chunks before it has to earn anything. */
    public static final long STARTING_TREASURY = 5_000L;

    private final UUID id;
    private String name;
    private final UUID owner;
    private final ResourceKey<Level> dimension;
    private long treasury = STARTING_TREASURY;

    private final LongSet claimedChunks = new LongOpenHashSet();
    private final List<UUID> structures = new ArrayList<>();

    /**
     * Cities this one has given the run of its land to, and cities it is at war with.
     *
     * <p>Held here rather than in a table of their own because a relationship is a fact about a city
     * in the same way its treasury is, and every question ever asked of these — may this person
     * build here, may they hit that citizen — starts from a city and not from a pair.
     *
     * <p>Both are stored one way round, and mean different things for it. A grant is simply the
     * grantor's to give. A war entry is one city saying it is hostile, and two cities count as at
     * war while <em>either</em> holds one — so a declaration cannot be shrugged off by the city on
     * the receiving end, and standing down is something you can only do for yourself.
     */
    private final Set<UUID> granted = new HashSet<>();
    private final Set<UUID> wars = new HashSet<>();

    /**
     * Capacity the city's buildings offer, and how much of it is taken up.
     *
     * <p>Housing and jobs are what the buildings <em>could</em> hold; population and employed are what
     * they currently do. Showing both is what makes a half-empty city legible instead of looking like
     * the numbers are simply wrong.
     */
    private int housing;
    private int jobs;
    private int population;
    private int employed;

    /** What the city's buildings draw, and what its network actually delivers. */
    private int powerNeeded;
    private int powerProduced;

    /** The same for water: what the people need, and what actually came out of the tanks. */
    private int waterNeeded;
    private int waterSupplied;

    public City(UUID id, String name, UUID owner, ResourceKey<Level> dimension) {
        this.id = id;
        this.name = name;
        this.owner = owner;
        this.dimension = dimension;
    }

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

    /** Territory is per dimension, so chunk (0,0) in the Nether is not chunk (0,0) here. */
    public ResourceKey<Level> dimension() {
        return dimension;
    }

    public long treasury() {
        return treasury;
    }

    public void deposit(long amount) {
        treasury += amount;
    }

    public boolean canAfford(long amount) {
        return treasury >= amount;
    }

    public boolean withdraw(long amount) {
        if (!canAfford(amount)) {
            return false;
        }
        treasury -= amount;
        return true;
    }

    public LongSet claimedChunks() {
        return claimedChunks;
    }

    public boolean owns(long chunkKey) {
        return claimedChunks.contains(chunkKey);
    }

    public void claim(long chunkKey) {
        claimedChunks.add(chunkKey);
    }

    public void unclaim(long chunkKey) {
        claimedChunks.remove(chunkKey);
    }

    /**
     * What the next chunk costs.
     *
     * <p>Rising with the size of the claim, so sprawling outward to grab land is a decision with a
     * price rather than something you do idly on the first afternoon.
     */
    public long nextClaimCost() {
        return 250L + (long) claimedChunks.size() * 100L;
    }

    public List<UUID> structures() {
        return structures;
    }

    public void addStructure(UUID structureId) {
        if (!structures.contains(structureId)) {
            structures.add(structureId);
        }
    }

    public void removeStructure(UUID structureId) {
        structures.remove(structureId);
    }

    public int housing() {
        return housing;
    }

    public int jobs() {
        return jobs;
    }

    public int population() {
        return population;
    }

    public int employed() {
        return employed;
    }

    public void setCapacity(int housing, int jobs) {
        this.housing = housing;
        this.jobs = jobs;
    }

    public void setPopulation(int population) {
        this.population = Math.max(0, population);
    }

    public void setEmployed(int employed) {
        this.employed = Math.max(0, employed);
    }

    public int powerNeeded() {
        return powerNeeded;
    }

    public int powerProduced() {
        return powerProduced;
    }

    public void setPower(int produced, int needed) {
        this.powerProduced = Math.max(0, produced);
        this.powerNeeded = Math.max(0, needed);
    }

    public int waterNeeded() {
        return waterNeeded;
    }

    public int waterSupplied() {
        return waterSupplied;
    }

    public void setWater(int supplied, int needed) {
        this.waterSupplied = Math.max(0, supplied);
        this.waterNeeded = Math.max(0, needed);
    }

    // ------------------------------------------------------------- diplomacy

    /** Whether this city has given another the run of its land. */
    public boolean grantedTo(UUID otherCityId) {
        return granted.contains(otherCityId);
    }

    /** Let another city's people build here. Returns false if nothing changed. */
    public boolean grant(UUID otherCityId) {
        return granted.add(otherCityId);
    }

    public boolean revoke(UUID otherCityId) {
        return granted.remove(otherCityId);
    }

    /**
     * Declare yourself hostile to another city.
     *
     * <p>Stored one way round even though a war is felt by both sides, and that is the point: two
     * cities are at war while <em>either</em> of them is hostile, so standing down is something you
     * can only do for yourself. If peace were one click by either party, the city that was attacked
     * could end the war unilaterally and a declaration would mean nothing.
     */
    public boolean declareWar(UUID otherCityId) {
        return wars.add(otherCityId);
    }

    /** Stand down. The war only actually ends once the other side has done the same. */
    public boolean makePeace(UUID otherCityId) {
        return wars.remove(otherCityId);
    }

    /** Whether <em>this</em> city is the hostile one. Ask {@code Diplomacy} for the real answer. */
    public boolean hostileTo(UUID otherCityId) {
        return wars.contains(otherCityId);
    }

    public Set<UUID> granted() {
        return granted;
    }

    public Set<UUID> wars() {
        return wars;
    }

    /**
     * Forget a city entirely.
     *
     * <p>Called when one is deleted. Without it a razed city leaves its wars behind in everybody
     * else's save file forever, and a new city that happened to be handed the same UUID would
     * inherit somebody else's grudge.
     */
    public boolean forget(UUID otherCityId) {
        return granted.remove(otherCityId) | wars.remove(otherCityId);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putString("name", name);
        tag.putUUID("owner", owner);
        tag.putString("dimension", dimension.location().toString());
        tag.putLong("treasury", treasury);
        tag.putLongArray("chunks", claimedChunks.toLongArray());

        ListTag structureList = new ListTag();
        for (UUID structureId : structures) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", structureId);
            structureList.add(entry);
        }
        tag.put("structures", structureList);

        tag.putInt("housing", housing);
        tag.putInt("jobs", jobs);
        tag.putInt("population", population);
        tag.putInt("employed", employed);
        tag.putInt("powerNeeded", powerNeeded);
        tag.putInt("powerProduced", powerProduced);
        tag.putInt("waterNeeded", waterNeeded);
        tag.putInt("waterSupplied", waterSupplied);
        tag.put("granted", writeIds(granted));
        tag.put("wars", writeIds(wars));
        return tag;
    }

    private static ListTag writeIds(Set<UUID> ids) {
        ListTag list = new ListTag();
        for (UUID id : ids) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", id);
            list.add(entry);
        }
        return list;
    }

    private static void readIds(CompoundTag tag, String key, Set<UUID> into) {
        // Absent on any city saved before Alpha 4, which reads as an empty list rather than as an
        // error - an old world simply starts out at peace with everybody.
        ListTag list = tag.getList(key, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            into.add(list.getCompound(i).getUUID("id"));
        }
    }

    public static City load(CompoundTag tag) {
        ResourceKey<Level> dimension = ResourceKey.create(
                Registries.DIMENSION, ResourceLocation.parse(tag.getString("dimension")));
        City city = new City(tag.getUUID("id"), tag.getString("name"), tag.getUUID("owner"), dimension);
        city.treasury = tag.getLong("treasury");
        for (long chunk : tag.getLongArray("chunks")) {
            city.claimedChunks.add(chunk);
        }
        ListTag structureList = tag.getList("structures", Tag.TAG_COMPOUND);
        for (int i = 0; i < structureList.size(); i++) {
            city.structures.add(structureList.getCompound(i).getUUID("id"));
        }
        city.housing = tag.getInt("housing");
        city.jobs = tag.getInt("jobs");
        city.population = tag.getInt("population");
        city.employed = tag.getInt("employed");
        city.powerNeeded = tag.getInt("powerNeeded");
        city.powerProduced = tag.getInt("powerProduced");
        city.waterNeeded = tag.getInt("waterNeeded");
        city.waterSupplied = tag.getInt("waterSupplied");
        readIds(tag, "granted", city.granted);
        readIds(tag, "wars", city.wars);
        return city;
    }
}
