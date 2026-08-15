package com.branciho.livingcities.city;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

public final class Building {
    private final UUID id;
    private final UUID cityId;
    private final String name;
    private final BuildingType type;
    private final BlockPos min;
    private final BlockPos max;
    private final int floors;
    private final int usableArea;
    private final int housing;
    private final int jobs;

    public Building(UUID id, UUID cityId, String name, BuildingType type, BlockPos min, BlockPos max,
                    int floors, int usableArea, int housing, int jobs) {
        this.id = id;
        this.cityId = cityId;
        this.name = name;
        this.type = type;
        this.min = min;
        this.max = max;
        this.floors = floors;
        this.usableArea = usableArea;
        this.housing = housing;
        this.jobs = jobs;
    }

    public UUID id() { return id; }
    public UUID cityId() { return cityId; }
    public String name() { return name; }
    public BuildingType type() { return type; }
    public BlockPos min() { return min; }
    public BlockPos max() { return max; }
    public int floors() { return floors; }
    public int usableArea() { return usableArea; }
    public int housing() { return housing; }
    public int jobs() { return jobs; }

    public boolean contains(BlockPos pos) {
        return pos.getX() >= min.getX() && pos.getX() <= max.getX()
                && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putUUID("CityId", cityId);
        tag.putString("Name", name);
        tag.putString("Type", type.name());
        tag.putInt("MinX", min.getX()); tag.putInt("MinY", min.getY()); tag.putInt("MinZ", min.getZ());
        tag.putInt("MaxX", max.getX()); tag.putInt("MaxY", max.getY()); tag.putInt("MaxZ", max.getZ());
        tag.putInt("Floors", floors);
        tag.putInt("UsableArea", usableArea);
        tag.putInt("Housing", housing);
        tag.putInt("Jobs", jobs);
        return tag;
    }

    public static Building load(CompoundTag tag) {
        return new Building(tag.getUUID("Id"), tag.getUUID("CityId"), tag.getString("Name"),
                BuildingType.parse(tag.getString("Type")),
                new BlockPos(tag.getInt("MinX"), tag.getInt("MinY"), tag.getInt("MinZ")),
                new BlockPos(tag.getInt("MaxX"), tag.getInt("MaxY"), tag.getInt("MaxZ")),
                tag.getInt("Floors"), tag.getInt("UsableArea"), tag.getInt("Housing"), tag.getInt("Jobs"));
    }
}
