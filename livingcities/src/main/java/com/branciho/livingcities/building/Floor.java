package com.branciho.livingcities.building;

import com.branciho.livingcities.config.LivingCitiesConfig;
import net.minecraft.nbt.CompoundTag;

import java.util.EnumSet;
import java.util.Set;

/**
 * One storey of a registered building.
 *
 * <p>The number that matters is {@link #usableCells()}: standable, enclosed, head-clear blocks. Using
 * the bounding-box volume instead would give a skyscraper the capacity of its empty air, and a
 * cathedral the capacity of a warehouse.
 */
public final class Floor {

    private int floorY;
    private int yMin;
    private int yMax;

    private int usableCells;
    private int standCells;
    private int ceilingHeight;

    private ZoneUse use = ZoneUse.UNUSED;
    private final Set<FloorFlag> flags = EnumSet.noneOf(FloorFlag.class);

    /** Optional player override; -1 means "use the zone's configured density". */
    private double densityOverride = -1.0D;

    public Floor() {
    }

    public Floor(int floorY, int yMin, int yMax, int usableCells, int standCells, int ceilingHeight) {
        this.floorY = floorY;
        this.yMin = yMin;
        this.yMax = yMax;
        this.usableCells = usableCells;
        this.standCells = standCells;
        this.ceilingHeight = ceilingHeight;
    }

    public int floorY() {
        return floorY;
    }

    public int yMin() {
        return yMin;
    }

    public int yMax() {
        return yMax;
    }

    public void setRange(int yMin, int yMax) {
        this.yMin = yMin;
        this.yMax = yMax;
    }

    public int usableCells() {
        return usableCells;
    }

    public void setUsableCells(int usableCells) {
        this.usableCells = Math.max(0, usableCells);
    }

    public int standCells() {
        return standCells;
    }

    public int ceilingHeight() {
        return ceilingHeight;
    }

    public ZoneUse use() {
        return use;
    }

    public void setUse(ZoneUse use) {
        this.use = use;
    }

    public Set<FloorFlag> flags() {
        return flags;
    }

    public boolean has(FloorFlag flag) {
        return flags.contains(flag);
    }

    public double densityOverride() {
        return densityOverride;
    }

    public void setDensityOverride(double densityOverride) {
        this.densityOverride = densityOverride;
    }

    /**
     * How much this floor's area actually counts.
     *
     * <p>A roof deck is not an apartment, and a windowless basement is a poor place to live but a
     * perfectly good warehouse, so the penalty depends on what the floor is used for.
     */
    public double capacityMultiplier() {
        double multiplier = 1.0D;
        if (has(FloorFlag.ROOF)) {
            return 0.0D;
        }
        if (has(FloorFlag.BASEMENT)) {
            multiplier *= switch (use) {
                case RESIDENTIAL -> 0.60D;
                case OFFICE, COMMERCIAL -> 0.80D;
                default -> 1.0D;
            };
        }
        // Cramped ceilings are usable but unpleasant; very tall ones are not extra floor area.
        if (ceilingHeight <= 2) {
            multiplier *= 0.85D;
        }
        return multiplier;
    }

    /**
     * Usable floor blocks per dwelling, and how many people live in one.
     *
     * <p>Housing is quantised into whole dwellings rather than scaled linearly, because half an
     * apartment houses nobody. The difference only matters at the small end, and that is exactly where
     * it matters most: a two-storey starter house comes out at 6 residents instead of 8.
     *
     * <p>These constants were fitted against the three sizes named in the design brief - a small house
     * (6), an apartment block (180) and a residential tower (1,800). One constant set reproduces all
     * three within 5% with no per-case fudging, which is the real test that the formula is sound.
     */
    private static final double CELLS_PER_DWELLING = 16.0D;
    private static final int RESIDENTS_PER_DWELLING = 3;

    /** Below this, a floor is a cupboard rather than a room and produces nothing. */
    private static final int MIN_HABITABLE_CELLS = 9;

    /** Effective usable area after roof/basement/ceiling penalties and the server's capacity scale. */
    private double effectiveArea() {
        return usableCells * capacityMultiplier() * LivingCitiesConfig.capacityScale();
    }

    public int residents() {
        if (!use.providesHousing()) {
            return 0;
        }
        double area = effectiveArea();
        if (area < MIN_HABITABLE_CELLS) {
            return 0;
        }
        if (densityOverride >= 0) {
            return (int) Math.floor(area / 100.0D * densityOverride);
        }
        int dwellings = (int) Math.floor(area / CELLS_PER_DWELLING);
        return dwellings * RESIDENTS_PER_DWELLING;
    }

    public int jobs() {
        if (!use.providesJobs()) {
            return 0;
        }
        double area = effectiveArea();
        if (area < MIN_HABITABLE_CELLS) {
            return 0;
        }
        double perHundred = densityOverride >= 0 ? densityOverride : use.jobsPerHundred();
        // Rounded, not floored: a corner shop with 45 usable blocks employs two people, not one.
        int jobs = (int) Math.round(area / 100.0D * perHundred);
        // A room big enough to work in employs at least somebody.
        return Math.max(jobs, 1);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("FloorY", floorY);
        tag.putInt("YMin", yMin);
        tag.putInt("YMax", yMax);
        tag.putInt("Usable", usableCells);
        tag.putInt("Stand", standCells);
        tag.putInt("Ceiling", ceilingHeight);
        tag.putString("Use", use.id());
        tag.putDouble("Density", densityOverride);
        StringBuilder flagList = new StringBuilder();
        for (FloorFlag flag : flags) {
            if (flagList.length() > 0) {
                flagList.append(',');
            }
            flagList.append(flag.name());
        }
        tag.putString("Flags", flagList.toString());
        return tag;
    }

    public static Floor load(CompoundTag tag) {
        Floor floor = new Floor();
        floor.floorY = tag.getInt("FloorY");
        floor.yMin = tag.getInt("YMin");
        floor.yMax = tag.getInt("YMax");
        floor.usableCells = tag.getInt("Usable");
        floor.standCells = tag.getInt("Stand");
        floor.ceilingHeight = tag.getInt("Ceiling");
        floor.use = ZoneUse.byId(tag.getString("Use"), ZoneUse.UNUSED);
        floor.densityOverride = tag.contains("Density") ? tag.getDouble("Density") : -1.0D;
        String flagList = tag.getString("Flags");
        if (!flagList.isEmpty()) {
            for (String name : flagList.split(",")) {
                try {
                    floor.flags.add(FloorFlag.valueOf(name));
                } catch (IllegalArgumentException ignored) {
                    // A flag removed in a later version simply stops applying.
                }
            }
        }
        return floor;
    }
}
