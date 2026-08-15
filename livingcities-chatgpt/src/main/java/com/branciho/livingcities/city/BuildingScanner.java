package com.branciho.livingcities.city;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public final class BuildingScanner {
    private BuildingScanner() {}

    public static ScanResult scan(ServerLevel level, BlockPos min, BlockPos max, BuildingType type) {
        int width = max.getX() - min.getX() + 1;
        int depth = max.getZ() - min.getZ() + 1;
        int footprint = width * depth;
        int floorThreshold = Math.max(4, footprint / 5);
        int lastFloorY = Integer.MIN_VALUE;
        int floors = 0;
        int usable = 0;

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = min.getY(); y <= max.getY() - 2; y++) {
            int walkable = 0;
            for (int x = min.getX(); x <= max.getX(); x++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    cursor.set(x, y, z);
                    if (level.getBlockState(cursor).isAir()) continue;
                    cursor.set(x, y + 1, z);
                    if (!level.getBlockState(cursor).isAir()) continue;
                    cursor.set(x, y + 2, z);
                    if (!level.getBlockState(cursor).isAir()) continue;
                    walkable++;
                }
            }
            if (walkable >= floorThreshold && y - lastFloorY >= 3) {
                floors++;
                usable += walkable;
                lastFloorY = y;
            }
        }

        if (floors == 0) {
            floors = 1;
            usable = Math.max(1, footprint);
        }

        int housing = type == BuildingType.RESIDENTIAL ? Math.max(1, usable / 18) : 0;
        int jobs = switch (type) {
            case COMMERCIAL -> Math.max(1, usable / 12);
            case OFFICE -> Math.max(1, usable / 8);
            case INDUSTRIAL -> Math.max(1, usable / 16);
            case RESIDENTIAL -> Math.max(0, usable / 120);
        };
        return new ScanResult(floors, usable, housing, jobs);
    }

    public record ScanResult(int floors, int usableArea, int housing, int jobs) {}
}
