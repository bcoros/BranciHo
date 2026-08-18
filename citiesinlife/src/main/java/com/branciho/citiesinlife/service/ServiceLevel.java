package com.branciho.citiesinlife.service;

import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

/**
 * How many people a service is allowed to have on the street at once.
 *
 * <p>Set on the spawner rather than in the config, because it is a decision about one building and
 * not about the world: a village police box wants one officer and a city centre station wants four,
 * and both can exist in the same save.
 *
 * <p>These are caps and not quotas. A high setting with nothing going on still puts nobody outside.
 */
public enum ServiceLevel implements StringRepresentable {

    LOW("low", 1),
    MEDIUM("medium", 2),
    HIGH("high", 4);

    private final String id;
    private final int headcount;

    ServiceLevel(String id, int headcount) {
        this.id = id;
        this.headcount = headcount;
    }

    public String id() {
        return id;
    }

    public int headcount() {
        return headcount;
    }

    public ServiceLevel next() {
        ServiceLevel[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public Component displayName() {
        return Component.translatable("service_level.citiesinlife." + id);
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    public static ServiceLevel byId(String id, ServiceLevel fallback) {
        for (ServiceLevel level : values()) {
            if (level.id.equals(id)) {
                return level;
            }
        }
        return fallback;
    }
}
