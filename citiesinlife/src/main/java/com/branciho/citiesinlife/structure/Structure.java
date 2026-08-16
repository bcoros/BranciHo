package com.branciho.citiesinlife.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
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
    private final StructureType type;
    private final ResourceKey<Level> dimension;
    private final BlockPos min;
    private final BlockPos max;
    private final List<Floor> floors = new ArrayList<>();

    /**
     * How this structure was measured, and what that measurement produced.
     *
     * <p>The cell count is stored rather than re-derived from {@link #floors} because block-volume
     * mode produces no floors at all — it measures enclosed space instead. Both modes end up in the
     * same unit so the capacity formulas do not care which was used.
     */
    private MeasureMode measureMode = MeasureMode.FLOORS;
    private int usableCells;

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

    public List<Floor> floors() {
        return floors;
    }

    public void setMeasurement(MeasureMode mode, List<Floor> detected, int cells) {
        this.measureMode = mode;
        this.usableCells = Math.max(0, cells);
        floors.clear();
        floors.addAll(detected);
    }

    public MeasureMode measureMode() {
        return measureMode;
    }

    public int floorCount() {
        return floors.size();
    }

    public int usableCells() {
        return usableCells;
    }

    public int residents() {
        return type.residentsFor(usableCells());
    }

    public int jobs() {
        return type.jobsFor(usableCells());
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
        tag.putString("measure", measureMode.id());
        tag.putInt("cells", usableCells);
        tag.putInt("minX", min.getX());
        tag.putInt("minY", min.getY());
        tag.putInt("minZ", min.getZ());
        tag.putInt("maxX", max.getX());
        tag.putInt("maxY", max.getY());
        tag.putInt("maxZ", max.getZ());
        ListTag floorList = new ListTag();
        for (Floor floor : floors) {
            floorList.add(floor.save());
        }
        tag.put("floors", floorList);
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
        structure.measureMode = MeasureMode.byId(tag.getString("measure"), MeasureMode.FLOORS);
        structure.usableCells = tag.getInt("cells");
        ListTag floorList = tag.getList("floors", Tag.TAG_COMPOUND);
        for (int i = 0; i < floorList.size(); i++) {
            structure.floors.add(Floor.load(floorList.getCompound(i)));
        }
        return structure;
    }
}
