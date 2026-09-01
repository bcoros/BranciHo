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
    private final String name;
    /** The least health any registered building has, however little it is made of. */
    public static final int MIN_HEALTH = 40;

    /**
     * Material assumed per cell of floor for a building registered before health existed.
     *
     * <p>Two: a room is mostly air, and its walls, floor and ceiling are roughly this much material
     * per cell of the space they enclose. Only ever used once per old building, and replaced by a
     * real count the first time anything re-measures it.
     */
    private static final int ASSUMED_MASS_PER_CELL = 2;

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
     * The most punishment this building can take.
     *
     * <p>One point per block it is built out of, with a floor under it so a hut is not one creeper
     * from nothing and a marker with no blocks in it is not born dead.
     */
    public int maxHealth() {
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
        return type.residentsFor(usableCells());
    }

    public int jobs() {
        return type.jobsFor(usableCells());
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
        // Absent on every building registered before health existed. Mass of nought would make
        // maxHealth the floor and put a cathedral on the same footing as a shed, so an unmeasured
        // building is given a mass derived from the space inside it - a rough stand-in until the
        // next blast re-measures it properly - and is loaded undamaged.
        structure.blockMass = tag.contains("mass")
                ? tag.getInt("mass")
                : structure.usableCells * ASSUMED_MASS_PER_CELL;
        structure.health = tag.contains("health") ? tag.getInt("health") : structure.maxHealth();
        return structure;
    }
}
