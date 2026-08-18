package com.branciho.citiesinlife.service;

import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

/**
 * The public services a city can run.
 *
 * <p>Each one is a building the player declares with the Planner Wand plus, for most of them, a
 * Service NPC Spawner standing inside it. The spawner does not need telling what it is — it looks at
 * the building it is in and becomes that. One block for six jobs, and no way to put the wrong one
 * down.
 *
 * <p>Two of these staff nobody. A park is a place, not a workforce, and its whole effect is that the
 * people who live near it behave better and more of them want to move in. A military base does have
 * people, but they are hired one at a time through the Military Tool rather than turning up because
 * they are needed — which is the entire difference between a service and an army.
 */
public enum ServiceType implements StringRepresentable {

    /**
     * Police. They come out when somebody has done something, and go away again afterwards.
     */
    POLICE("police", true),

    /**
     * The fire brigade: fires in the plant, and the clogged turbines that cause them.
     *
     * <p>They do exactly what the player would do with an extinguisher and a wrench. That is the
     * point of paying them — the plant no longer needs somebody standing in it.
     */
    FIRE("fire", true),

    /**
     * Doctors. Nothing else in this mod heals a citizen: a person who is hurt stays hurt until one
     * of these walks over to them.
     */
    HOSPITAL("hospital", true),

    /** A park. Staffs nobody; changes how the neighbourhood behaves. */
    PARK("park", false),

    /** Refuse collection. A city makes rubbish whether or not anybody deals with it. */
    GARBAGE("garbage", true),

    /** The barracks. Soldiers come from the Military Tool, not from need. */
    MILITARY("military", true);

    private final String id;
    private final boolean staffed;

    ServiceType(String id, boolean staffed) {
        this.id = id;
        this.staffed = staffed;
    }

    public String id() {
        return id;
    }

    /** Whether a Service NPC Spawner inside this kind of building has anybody to spawn. */
    public boolean staffed() {
        return staffed;
    }

    /** Whether its people stay put instead of coming and going with demand. Only soldiers do. */
    public boolean permanent() {
        return this == MILITARY;
    }

    public Component displayName() {
        return Component.translatable("service.citiesinlife." + id);
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    public static ServiceType byId(String id, ServiceType fallback) {
        for (ServiceType type : values()) {
            if (type.id.equals(id)) {
                return type;
            }
        }
        return fallback;
    }
}
