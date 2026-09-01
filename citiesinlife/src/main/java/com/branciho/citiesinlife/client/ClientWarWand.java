package com.branciho.citiesinlife.client;

import com.branciho.citiesinlife.structure.StructureType;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * What the War Planner Wand will do to the building it is pointed at.
 *
 * <p>Kept apart from the ordinary selection's type on purpose, because it has one option the
 * ordinary one cannot have: leave it alone. Taking a building without changing it is the common
 * case — a conqueror wants the enemy's housing to go on being housing — so that is where the cycle
 * starts.
 */
public final class ClientWarWand {

    /** What to rewrite the seized building as, or null to keep it exactly as it is. */
    private static @Nullable StructureType rewriteAs;

    private ClientWarWand() {
    }

    public static @Nullable StructureType rewriteAs() {
        return rewriteAs;
    }

    /** The id to send: empty means "leave it as it was". */
    public static String rewriteId() {
        return rewriteAs == null ? "" : rewriteAs.id();
    }

    /**
     * Step through the options, with "keep it" sitting between the two ends of the list.
     *
     * <p>Wrapping through null rather than putting it first means one press either way from the
     * start of the list gets you back to it.
     *
     * <p>A city hall is not offered. The server refuses to make a seized building into one — that
     * would hand somebody a second seat of government — and an option that is always refused is
     * worse than no option.
     */
    public static void cycle(int direction) {
        List<StructureType> all = choices();
        if (rewriteAs == null) {
            rewriteAs = direction > 0 ? all.get(0) : all.get(all.size() - 1);
            return;
        }
        int next = all.indexOf(rewriteAs) + direction;
        rewriteAs = next < 0 || next >= all.size() ? null : all.get(next);
    }

    private static List<StructureType> choices() {
        List<StructureType> options = new ArrayList<>();
        for (StructureType type : StructureType.SELECTABLE) {
            if (type != StructureType.CITY_CORE) {
                options.add(type);
            }
        }
        return options;
    }

    public static Component describe() {
        return rewriteAs == null
                ? Component.translatable("hud.citiesinlife.war_keep")
                : Component.translatable("hud.citiesinlife.war_rewrite", rewriteAs.displayName());
    }

    public static void reset() {
        rewriteAs = null;
    }
}
