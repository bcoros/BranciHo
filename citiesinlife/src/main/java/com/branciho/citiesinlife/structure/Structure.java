package com.branciho.citiesinlife.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A registered structure: a box the player drew, a type they chose, and what measuring the blocks
 * inside it produced.
 *
 * <p>This has no blocks of its own — it is pure server-side data. That is exactly why structure mode
 * exists: an invisible claim on ground that you cannot see and cannot delete is the single most
 * frustrating thing this kind of mod can do to a player.
 */
public final class Structure {

    private final UUID id;
    private final UUID cityId;

    /**
     * What the player calls it.
     *
     * <p>Not final any more. It is the label over the health bar and the row in the editor, and a
     * city where every third building is "Residential 4" is a city you cannot navigate.
     */
    private String name;

    /**
     * Hand-set capacity, or -1 to keep using what the box measures.
     *
     * <p>Editor mode only, and creative only. Measuring is right almost all of the time and should
     * stay the default — the whole point of this mod is that it reads what you built rather than
     * asking you to declare it. But "almost all" is not all: a build whose inside the scanner
     * cannot see, a landmark that should be worth more than its floor space, a set piece you want
     * to hold a fixed number. -1 rather than 0 because 0 is a legitimate answer somebody may want.
     */
    private int residentOverride = -1;
    private int jobOverride = -1;

    /**
     * Hand-set total health, or -1 to keep taking it from what the building is made of.
     *
     * <p>The <em>total</em>, not the current figure — Repair already exists for topping a building
     * up, and "this tower has five thousand health" is the thing worth being able to say. Mass is a
     * good default and a poor absolute: a bunker and a glass box of the same size are correctly
     * told apart by it, but a landmark that ought to survive a warhead is not something counting
     * blocks will ever produce.
     */
    private int healthOverride = -1;

    /**
     * Extra output, in whatever this building's own units are.
     *
     * <p>The editor's Boost. One number rather than four, because a building only ever does one of
     * these things: a power plant makes power, a depot clears rubbish, and the city hall is where
     * the two utilities that have no box of their own end up.
     *
     * <p>Water and sewage share the city hall's number on purpose, not as a compromise. The
     * simulation already ties them together — what a city sends to its sewers <em>is</em> what it
     * drank — so lifting the water supply without lifting the sewage capacity to match would create
     * untreated sewage out of nothing and pile it up as rubbish. One number that moves both is the
     * only version of this that is not a trap.
     *
     * <p>Zero everywhere until somebody types something, so a world that has never opened the
     * editor behaves exactly as it did.
     */
    private int boost;
    /** The least health any registered building has, however little it is made of. */
    public static final int MIN_HEALTH = 40;

    /** Longest name a building may carry. The same cap the sync payload writes with. */
    public static final int MAX_NAME = 48;

    /**
     * Highest figure an override may be set to.
     *
     * <p>A bound rather than a balance decision. Editor mode is creative-only and the player may
     * put whatever they like in these boxes, but a city with two billion residents overflows the
     * treasury arithmetic and every per-head calculation downstream of it.
     */
    public static final int MAX_OVERRIDE = 1_000_000;

    private final StructureType type;
    private final ResourceKey<Level> dimension;
    private final BlockPos min;
    private final BlockPos max;

    /**
     * What measuring the inside of it produced, in floor-equivalent cells.
     *
     * <p>Stored rather than re-derived, because it is the answer to a question about blocks that
     * were there when the player registered the building, and re-measuring on demand would quietly
     * change a city's population every time somebody opened a door.
     */
    private int usableCells;

    /**
     * How many blocks this building is made of, and how much of that is still standing.
     *
     * <p>Mass is the material: every non-air, non-liquid block inside the box when it was last
     * measured. It is what a building's health is worth, because how much punishment a thing can
     * absorb is a question about what it is built out of and not about how much room is inside it.
     * A glass box and a bunker of the same size enclose the same floor space and are not remotely
     * the same building.
     *
     * <p>Health is what is left of that. It starts full, comes off when blasts take blocks out of
     * the box, and comes back slowly while the city is left in peace. At nought the registration is
     * gone: the box is rubble, and for a city hall that takes the city with it.
     *
     * <p>Both are saved. Damage that a restart forgave was the old behaviour and it was wrong -
     * somebody who spent a night shelling a tower would come back to find it untouched.
     */
    private int blockMass;
    private int health;

    public Structure(UUID id, UUID cityId, String name, StructureType type,
                     ResourceKey<Level> dimension, BlockPos min, BlockPos max) {
        this.id = id;
        this.cityId = cityId;
        this.name = name;
        this.type = type;
        this.dimension = dimension;
        this.min = min;
        this.max = max;
    }

    public static Structure create(UUID cityId, String name, StructureType type,
                                   ResourceKey<Level> dimension, BlockPos a, BlockPos b) {
        BlockPos min = new BlockPos(
                Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
        BlockPos max = new BlockPos(
                Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
        return new Structure(UUID.randomUUID(), cityId, name, type, dimension, min, max);
    }

    public UUID id() {
        return id;
    }

    public UUID cityId() {
        return cityId;
    }

    public String name() {
        return name;
    }

    /** Rename it. Trimmed and bounded here rather than trusted from the packet. */
    public void rename(String fresh) {
        String trimmed = fresh == null ? "" : fresh.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        this.name = trimmed.length() > MAX_NAME ? trimmed.substring(0, MAX_NAME) : trimmed;
    }

    /** The hand-set resident count, or -1 when the box is doing the measuring. */
    public int residentOverride() {
        return residentOverride;
    }

    public int jobOverride() {
        return jobOverride;
    }

    public int healthOverride() {
        return healthOverride;
    }

    /** Extra output in this building's own units, or 0 for none. See the field. */
    public int boost() {
        return boost;
    }

    public void setBoost(int amount) {
        this.boost = Mth.clamp(amount, 0, MAX_OVERRIDE);
    }

    /** Whether a Boost figure means anything for this kind of building. */
    public boolean boostable() {
        return type.boostable();
    }

    /**
     * Set the total by hand, or pass -1 to hand it back to the block count.
     *
     * <p>Current health rides along in proportion, the same way it does when a building is
     * re-measured. Doubling a tower's total should not halve how healthy it looks, and a building
     * standing at full stays at full.
     */
    public void setHealthOverride(int total) {
        int wasMax = maxHealth();
        int had = health;
        this.healthOverride = total < 0 ? -1 : Math.min(total, MAX_OVERRIDE);
        int nowMax = maxHealth();
        if (wasMax <= 0) {
            health = nowMax;
            return;
        }
        health = Mth.clamp((int) ((long) had * nowMax / wasMax), 0, nowMax);
    }

    /** Pass -1 to hand a figure back to the scanner. */
    public void setResidentOverride(int people) {
        this.residentOverride = people < 0 ? -1 : Math.min(people, MAX_OVERRIDE);
    }

    public void setJobOverride(int posts) {
        this.jobOverride = posts < 0 ? -1 : Math.min(posts, MAX_OVERRIDE);
    }

    public StructureType type() {
        return type;
    }

    public ResourceKey<Level> dimension() {
        return dimension;
    }

    public BlockPos min() {
        return min;
    }

    public BlockPos max() {
        return max;
    }

    public void setMeasurement(int cells) {
        this.usableCells = Math.max(0, cells);
    }

    public int usableCells() {
        return usableCells;
    }

    /** How much material the last measurement found. */
    public int blockMass() {
        return blockMass;
    }

    /**
     * Whether anybody has actually counted what this is built out of.
     *
     * <p>False for every building registered before health existed. Until it is true the building
     * has no honest health, so nothing is allowed to damage it: the first blast against an
     * uncounted building counts it first, and a background pass counts the rest while the city
     * ticks over.
     */
    public boolean massKnown() {
        return blockMass > 0;
    }

    /**
     * The most punishment this building can take.
     *
     * <p>One point per block it is built out of, with a floor under it so a hut is not one creeper
     * from nothing and a marker with no blocks in it is not born dead.
     */
    public int maxHealth() {
        if (healthOverride >= 0) {
            return Math.max(1, healthOverride);
        }
        return Math.max(MIN_HEALTH, blockMass);
    }

    public int health() {
        return health;
    }

    /**
     * Set the mass, and bring health with it.
     *
     * <p>Health is carried across in proportion rather than reset, so re-measuring a building that
     * was already half wrecked does not quietly repair it — and extending one you have built onto
     * raises the ceiling without handing you the difference for free.
     */
    public void setMass(int mass) {
        int wasMax = maxHealth();
        int had = health;
        blockMass = Math.max(0, mass);
        int nowMax = maxHealth();
        health = wasMax <= 0
                ? nowMax
                : Mth.clamp((int) ((long) had * nowMax / wasMax), 0, nowMax);
    }

    /** Fill it up. Used when a building is first registered, and when one is rebuilt from scratch. */
    public void restore() {
        health = maxHealth();
    }

    /**
     * Take damage.
     *
     * <p>Saturating on the way in. A levelled region reports its damage as {@link Integer#MAX_VALUE}
     * and a plain subtraction would wrap straight past zero into a large positive number, which
     * reads as a building that survived being at the centre of a crater.
     *
     * @return whether that finished it
     */
    public boolean damage(int amount) {
        if (amount <= 0) {
            return health <= 0;
        }
        health = (int) Math.max(0L, (long) health - amount);
        return health <= 0;
    }

    /** Repairs itself, slowly, while nobody is knocking it down. */
    public void heal(int amount) {
        if (amount <= 0 || health >= maxHealth()) {
            return;
        }
        health = Math.min(maxHealth(), health + amount);
    }

    public int residents() {
        return residentOverride >= 0 ? residentOverride : type.residentsFor(usableCells());
    }

    public int jobs() {
        return jobOverride >= 0 ? jobOverride : type.jobsFor(usableCells());
    }

    /**
     * How much ground this structure covers, in square metres.
     *
     * <p>A park is the reason this exists — there is no inside to measure, and how much of the city
     * has been given over to grass is the whole of what one is worth. It is also what decides how
     * much of a building has to be blown up before it stops being one.
     */
    public int footprint() {
        return (max.getX() - min.getX() + 1) * (max.getZ() - min.getZ() + 1);
    }

    public boolean contains(BlockPos pos) {
        return pos.getX() >= min.getX() && pos.getX() <= max.getX()
                && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }

    public boolean intersects(BlockPos otherMin, BlockPos otherMax) {
        return min.getX() <= otherMax.getX() && max.getX() >= otherMin.getX()
                && min.getY() <= otherMax.getY() && max.getY() >= otherMin.getY()
                && min.getZ() <= otherMax.getZ() && max.getZ() >= otherMin.getZ();
    }

    /** Every chunk this structure touches, so the registry can index it spatially. */
    public List<Long> occupiedChunks() {
        List<Long> chunks = new ArrayList<>();
        int minChunkX = min.getX() >> 4;
        int maxChunkX = max.getX() >> 4;
        int minChunkZ = min.getZ() >> 4;
        int maxChunkZ = max.getZ() >> 4;
        for (int x = minChunkX; x <= maxChunkX; x++) {
            for (int z = minChunkZ; z <= maxChunkZ; z++) {
                chunks.add(ChunkPos.asLong(x, z));
            }
        }
        return chunks;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putUUID("city", cityId);
        tag.putString("name", name);
        tag.putString("type", type.id());
        tag.putString("dimension", dimension.location().toString());
        tag.putInt("cells", usableCells);
        tag.putInt("mass", blockMass);
        tag.putInt("health", health);
        // Written only when actually set, so a building the player never touched carries no trace
        // of the editor at all and picks up any future change to the measuring rules.
        if (residentOverride >= 0) {
            tag.putInt("residentsSet", residentOverride);
        }
        if (jobOverride >= 0) {
            tag.putInt("jobsSet", jobOverride);
        }
        if (healthOverride >= 0) {
            tag.putInt("healthSet", healthOverride);
        }
        if (boost > 0) {
            tag.putInt("boost", boost);
        }
        tag.putInt("minX", min.getX());
        tag.putInt("minY", min.getY());
        tag.putInt("minZ", min.getZ());
        tag.putInt("maxX", max.getX());
        tag.putInt("maxY", max.getY());
        tag.putInt("maxZ", max.getZ());
        return tag;
    }

    public static Structure load(CompoundTag tag) {
        Structure structure = new Structure(
                tag.getUUID("id"),
                tag.getUUID("city"),
                tag.getString("name"),
                StructureType.byId(tag.getString("type"), StructureType.RESIDENTIAL),
                ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(tag.getString("dimension"))),
                new BlockPos(tag.getInt("minX"), tag.getInt("minY"), tag.getInt("minZ")),
                new BlockPos(tag.getInt("maxX"), tag.getInt("maxY"), tag.getInt("maxZ")));
        // Saves written before the measurement mode existed have no "cells" tag at all: capacity
        // was derived by summing a list of detected storeys. Reading it as a plain getInt gives 0,
        // which propagates through the simulation and permanently zeroes an existing city's
        // population, so the old list is still read here purely to total it up. Nothing else in the
        // mod knows what a storey is any more - this is the last place, and only for saves that
        // predate it.
        if (tag.contains("cells")) {
            structure.usableCells = tag.getInt("cells");
        } else {
            int total = 0;
            ListTag storeys = tag.getList("floors", Tag.TAG_COMPOUND);
            for (int i = 0; i < storeys.size(); i++) {
                total += storeys.getCompound(i).getInt("cells");
            }
            structure.usableCells = total;
        }
        // Absent on every building registered before health existed, and NOT guessed at. A guess
        // was tried - material assumed from the space inside - and it was badly wrong in both
        // directions: it put a solid hut on the same footing as a shed, near enough the floor that
        // one charge finished it, and it made big and small buildings read almost alike. Nought
        // means "nobody has counted this yet", and something will go and count it.
        structure.blockMass = tag.getInt("mass");
        structure.health = tag.contains("health") ? tag.getInt("health") : structure.maxHealth();
        structure.residentOverride = tag.contains("residentsSet") ? tag.getInt("residentsSet") : -1;
        structure.jobOverride = tag.contains("jobsSet") ? tag.getInt("jobsSet") : -1;
        // Read before health below, because maxHealth() is what the missing-health fallback uses.
        structure.healthOverride = tag.contains("healthSet") ? tag.getInt("healthSet") : -1;
        structure.boost = tag.getInt("boost");
        return structure;
    }
}
