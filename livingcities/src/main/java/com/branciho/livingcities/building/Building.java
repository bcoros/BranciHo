package com.branciho.livingcities.building;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A player-built structure that has been registered as a functional city building.
 *
 * <p>The mod contributes no geometry here. The bounds are whatever the player selected, the floors
 * are whatever the scanner found inside them, and the blocks can come from any mod. What this class
 * adds is meaning: which floors are housing, which are jobs, how many people fit, and whether the
 * measurements are still trustworthy.
 */
public final class Building {

    private final UUID id;
    private UUID cityId;

    private String name;

    /** Inclusive selection bounds, in world coordinates. */
    private BlockPos min;
    private BlockPos max;

    private final List<Floor> floors = new ArrayList<>();
    private final Set<BlockPos> entrances = new LinkedHashSet<>();

    /** Game time of the last completed scan; 0 means never scanned. */
    private long lastScanGameTime;

    /**
     * Set when blocks inside the bounds changed after the last scan. Capacity keeps using the old
     * numbers until a rescan finishes, so editing a building never makes its residents vanish
     * mid-edit; it just marks the figures as stale.
     */
    private boolean needsRescan = true;

    // --- occupancy, owned by the simulation ---
    private int residents;
    private int workers;

    // --- utilities, owned by the power grid ---
    private int powerDemandKw;

    /** 0..1: how much of this building's electrical demand its grid actually meets. */
    private float powerSatisfaction;

    private int waterDemand;

    /** 0..1: how much of this building's water demand its network actually meets. */
    private float waterSatisfaction;

    public Building(UUID id, UUID cityId, String name, BlockPos min, BlockPos max) {
        this.id = id;
        this.cityId = cityId;
        this.name = name;
        this.min = min.immutable();
        this.max = max.immutable();
    }

    private Building(UUID id) {
        this.id = id;
    }

    /** Build from two arbitrary corners, normalising them into min/max. */
    public static Building fromCorners(UUID id, UUID cityId, String name, BlockPos a, BlockPos b) {
        BlockPos min = new BlockPos(
                Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
        BlockPos max = new BlockPos(
                Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
        return new Building(id, cityId, name, min, max);
    }

    // ------------------------------------------------------------------ identity and bounds

    public UUID id() {
        return id;
    }

    public UUID cityId() {
        return cityId;
    }

    public void setCityId(UUID cityId) {
        this.cityId = cityId;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BlockPos min() {
        return min;
    }

    public BlockPos max() {
        return max;
    }

    public int sizeX() {
        return max.getX() - min.getX() + 1;
    }

    public int sizeY() {
        return max.getY() - min.getY() + 1;
    }

    public int sizeZ() {
        return max.getZ() - min.getZ() + 1;
    }

    public long volume() {
        return (long) sizeX() * sizeY() * sizeZ();
    }

    public boolean contains(BlockPos pos) {
        return pos.getX() >= min.getX() && pos.getX() <= max.getX()
                && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }

    public boolean intersects(Building other) {
        return min.getX() <= other.max.getX() && max.getX() >= other.min.getX()
                && min.getY() <= other.max.getY() && max.getY() >= other.min.getY()
                && min.getZ() <= other.max.getZ() && max.getZ() >= other.min.getZ();
    }

    public AABB box() {
        return new AABB(
                min.getX(), min.getY(), min.getZ(),
                max.getX() + 1.0D, max.getY() + 1.0D, max.getZ() + 1.0D);
    }

    /** Every chunk this building's footprint touches, for the spatial index. */
    public List<ChunkPos> occupiedChunks() {
        List<ChunkPos> result = new ArrayList<>();
        int minChunkX = min.getX() >> 4;
        int maxChunkX = max.getX() >> 4;
        int minChunkZ = min.getZ() >> 4;
        int maxChunkZ = max.getZ() >> 4;
        for (int x = minChunkX; x <= maxChunkX; x++) {
            for (int z = minChunkZ; z <= maxChunkZ; z++) {
                result.add(new ChunkPos(x, z));
            }
        }
        return result;
    }

    // ------------------------------------------------------------------ floors

    public List<Floor> floors() {
        return floors;
    }

    public void replaceFloors(List<Floor> newFloors) {
        // Player-authored floors survive a rescan; detected ones are replaced wholesale.
        List<Floor> manual = floors.stream().filter(f -> f.has(FloorFlag.MANUAL)).toList();
        floors.clear();
        floors.addAll(newFloors);
        floors.addAll(manual);
        floors.sort((a, b) -> Integer.compare(a.floorY(), b.floorY()));
    }

    /**
     * Carry the previous zoning onto a fresh scan by matching floors on height.
     *
     * <p>Without this, rescanning a 25-storey tower after moving one wall would reset every floor to
     * unassigned and wipe the player's mixed-use layout.
     */
    public void inheritZoning(List<Floor> previous) {
        for (Floor floor : floors) {
            for (Floor old : previous) {
                if (Math.abs(old.floorY() - floor.floorY()) <= 1 && old.use() != ZoneUse.UNUSED) {
                    floor.setUse(old.use());
                    floor.setDensityOverride(old.densityOverride());
                    break;
                }
            }
        }
    }

    public int floorCount() {
        return (int) floors.stream().filter(f -> !f.has(FloorFlag.ROOF)).count();
    }

    /** The label shown in the UI: the dominant use, or "mixed use" when there is no single one. */
    public ZoneUse primaryUse() {
        ZoneUse best = ZoneUse.UNUSED;
        int bestArea = 0;
        int distinct = 0;
        for (ZoneUse use : ZoneUse.values()) {
            if (use == ZoneUse.UNUSED) {
                continue;
            }
            int area = usableCells(use);
            if (area > 0) {
                distinct++;
            }
            if (area > bestArea) {
                bestArea = area;
                best = use;
            }
        }
        return distinct > 1 ? ZoneUse.SPECIAL : best;
    }

    /**
     * The single use occupying the most floor area, ignoring unassigned space.
     *
     * <p>Distinct from {@link #primaryUse()}, which collapses any mixed building to SPECIAL. For
     * labelling and colouring you want to know that a mostly-residential tower is residential, even
     * when its ground floor is shops.
     */
    public ZoneUse dominantUse() {
        ZoneUse best = ZoneUse.UNUSED;
        int bestArea = 0;
        for (ZoneUse use : ZoneUse.values()) {
            if (use == ZoneUse.UNUSED) {
                continue;
            }
            int area = usableCells(use);
            if (area > bestArea) {
                bestArea = area;
                best = use;
            }
        }
        return best;
    }

    public boolean isMixedUse() {
        long distinct = floors.stream()
                .map(Floor::use)
                .filter(use -> use != ZoneUse.UNUSED)
                .distinct()
                .count();
        return distinct > 1;
    }

    public int usableCells(ZoneUse use) {
        int total = 0;
        for (Floor floor : floors) {
            if (floor.use() == use) {
                total += floor.usableCells();
            }
        }
        return total;
    }

    public int totalUsableCells() {
        int total = 0;
        for (Floor floor : floors) {
            total += floor.usableCells();
        }
        return total;
    }

    // ------------------------------------------------------------------ capacity

    public int housingCapacity() {
        int total = 0;
        for (Floor floor : floors) {
            total += floor.residents();
        }
        return total;
    }

    public int jobCapacity() {
        int total = 0;
        for (Floor floor : floors) {
            total += floor.jobs();
        }
        return total;
    }

    public int residents() {
        return residents;
    }

    public void setResidents(int residents) {
        this.residents = Math.clamp(residents, 0, housingCapacity());
    }

    public int workers() {
        return workers;
    }

    public void setWorkers(int workers) {
        this.workers = Math.clamp(workers, 0, jobCapacity());
    }

    public int powerDemandKw() {
        return powerDemandKw;
    }

    public void setPowerDemandKw(int powerDemandKw) {
        this.powerDemandKw = Math.max(0, powerDemandKw);
    }

    public float powerSatisfaction() {
        return powerSatisfaction;
    }

    public void setPowerSatisfaction(float powerSatisfaction) {
        this.powerSatisfaction = Math.clamp(powerSatisfaction, 0.0F, 1.0F);
    }

    /** True when the grid meets essentially all of this building's demand. */
    public boolean isPowered() {
        return powerSatisfaction >= 0.99F;
    }

    public int waterDemand() {
        return waterDemand;
    }

    public void setWaterDemand(int waterDemand) {
        this.waterDemand = Math.max(0, waterDemand);
    }

    public float waterSatisfaction() {
        return waterSatisfaction;
    }

    public void setWaterSatisfaction(float waterSatisfaction) {
        this.waterSatisfaction = Math.clamp(waterSatisfaction, 0.0F, 1.0F);
    }

    public boolean hasWater() {
        return waterSatisfaction >= 0.99F;
    }

    /** Centre of the building, used for "which substation reaches this". */
    public BlockPos centre() {
        return new BlockPos(
                (min.getX() + max.getX()) / 2,
                (min.getY() + max.getY()) / 2,
                (min.getZ() + max.getZ()) / 2);
    }

    /** 0..1; how well staffed this building is, which throttles its output. */
    public double staffingRatio() {
        int capacity = jobCapacity();
        return capacity <= 0 ? 1.0D : (double) workers / capacity;
    }

    // ------------------------------------------------------------------ entrances and scan state

    public Set<BlockPos> entrances() {
        return entrances;
    }

    public void addEntrance(BlockPos pos) {
        entrances.add(pos.immutable());
    }

    public void removeEntrance(BlockPos pos) {
        entrances.remove(pos);
    }

    public boolean needsRescan() {
        return needsRescan;
    }

    public void markDirty() {
        this.needsRescan = true;
    }

    public void markScanned(long gameTime) {
        this.needsRescan = false;
        this.lastScanGameTime = gameTime;
    }

    public long lastScanGameTime() {
        return lastScanGameTime;
    }

    // ------------------------------------------------------------------ serialization

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putUUID("CityId", cityId);
        tag.putString("Name", name);
        tag.putInt("MinX", min.getX());
        tag.putInt("MinY", min.getY());
        tag.putInt("MinZ", min.getZ());
        tag.putInt("MaxX", max.getX());
        tag.putInt("MaxY", max.getY());
        tag.putInt("MaxZ", max.getZ());
        tag.putLong("LastScan", lastScanGameTime);
        tag.putBoolean("NeedsRescan", needsRescan);
        tag.putInt("Residents", residents);
        tag.putInt("Workers", workers);

        ListTag floorList = new ListTag();
        for (Floor floor : floors) {
            floorList.add(floor.save());
        }
        tag.put("Floors", floorList);

        ListTag entranceList = new ListTag();
        for (BlockPos entrance : entrances) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("X", entrance.getX());
            entry.putInt("Y", entrance.getY());
            entry.putInt("Z", entrance.getZ());
            entranceList.add(entry);
        }
        tag.put("Entrances", entranceList);
        return tag;
    }

    public static Building load(CompoundTag tag) {
        Building building = new Building(tag.getUUID("Id"));
        building.cityId = tag.getUUID("CityId");
        building.name = tag.getString("Name");
        building.min = new BlockPos(tag.getInt("MinX"), tag.getInt("MinY"), tag.getInt("MinZ"));
        building.max = new BlockPos(tag.getInt("MaxX"), tag.getInt("MaxY"), tag.getInt("MaxZ"));
        building.lastScanGameTime = tag.getLong("LastScan");
        building.needsRescan = tag.getBoolean("NeedsRescan");
        building.residents = tag.getInt("Residents");
        building.workers = tag.getInt("Workers");

        ListTag floorList = tag.getList("Floors", Tag.TAG_COMPOUND);
        for (int i = 0; i < floorList.size(); i++) {
            building.floors.add(Floor.load(floorList.getCompound(i)));
        }

        ListTag entranceList = tag.getList("Entrances", Tag.TAG_COMPOUND);
        for (int i = 0; i < entranceList.size(); i++) {
            CompoundTag entry = entranceList.getCompound(i);
            building.entrances.add(new BlockPos(entry.getInt("X"), entry.getInt("Y"), entry.getInt("Z")));
        }
        return building;
    }
}
