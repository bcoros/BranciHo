package com.branciho.citiesinlife.missile;

import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

/**
 * The three things a silo can hold.
 *
 * <p>One block class and one mesh serve all three, the way one car serves four liveries. What
 * separates them is what they are for: a ballistic round is a hole in somebody's city, a nuclear
 * one is the end of that city, and an interceptor never leaves your own airspace. Everything else
 * — the shape, the doors, the flight — is the same rocket.
 */
public enum MissileKind implements StringRepresentable {

    /**
     * The one you can afford to use.
     *
     * <p>A crater you can rebuild around, fires that spread, and no fallout. It exists so that
     * having a silo is not the same as having a nuclear option: most of what a war does with
     * rockets should be this, and the other one should be a decision you take once.
     */
    BALLISTIC("ballistic_missile", 1.0F, 26.0D, 9.0F, true, false),

    /**
     * The one you use once.
     *
     * <p>An eighty-block crater, a mushroom cloud the whole server can see, and ten minutes of
     * fallout over half a kilometre. That is a district gone and the ground it stood on unusable
     * for as long as it takes to matter.
     */
    NUCLEAR("nuclear_missile", 1.0F, 80.0D, 16.0F, true, true),

    /**
     * The one that goes straight up.
     *
     * <p>Drawn at a bit over half size, because it is a smaller weapon and should look like one
     * standing next to the things it is defending against. It has no warhead of its own worth the
     * name: it either meets what is coming or it does not.
     */
    INTERCEPTOR("interceptor_missile", 0.55F, 0.0D, 0.0F, false, false);

    public static final Codec<MissileKind> CODEC = StringRepresentable.fromEnum(MissileKind::values);

    private final String id;
    private final float scale;
    private final double crater;
    private final float power;
    private final boolean offensive;
    private final boolean nuclear;

    MissileKind(String id, float scale, double crater, float power, boolean offensive,
                boolean nuclear) {
        this.id = id;
        this.scale = scale;
        this.crater = crater;
        this.power = power;
        this.offensive = offensive;
        this.nuclear = nuclear;
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    /** The registry name of the block, the texture, and the lang key, all from one string. */
    public String id() {
        return id;
    }

    /** How large the shared mesh is drawn. */
    public float scale() {
        return scale;
    }

    /** How far the crater reaches, before the meltdown scale dial is applied. */
    public double crater() {
        return crater;
    }

    /** What the vanilla explosion on top of the crater is worth - the flash, the shove, the noise. */
    public float power() {
        return power;
    }

    /** Whether this one can be fired at somebody. An interceptor cannot. */
    public boolean offensive() {
        return offensive;
    }

    /** Whether it leaves fallout behind, and whether it needs the full cloud. */
    public boolean nuclear() {
        return nuclear;
    }

    public Component displayName() {
        return Component.translatable("missile.citiesinlife." + id);
    }

    public static MissileKind byId(String id, MissileKind fallback) {
        for (MissileKind kind : values()) {
            if (kind.id.equals(id)) {
                return kind;
            }
        }
        return fallback;
    }
}
