package com.branciho.citiesinlife.city;

import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

/**
 * Whose turn it is to attack.
 *
 * <p>A war used to be a single flag, and the armies read it wrong in a way that made half of every
 * war stand still: {@link City#wars()} only ever holds entries for the city that <em>declared</em>,
 * and the soldiers' goal asked whether their own set was empty. For the side that was declared on,
 * it always was. Their troops never marched, never picked an objective, and only ever fought when
 * somebody walked into them - which is exactly what it looked like from the outside.
 *
 * <p>So a war now has a shape. The side that declared attacks first; three minutes later they swap,
 * and they keep swapping until somebody signs. Both cities compute the same answer from the same
 * two numbers - who declared, and when - so neither side needs to be told whose turn it is.
 *
 * <p>Attacking means marching on enemy ground and demolishing what is on it. Defending means
 * digging in on your own. The one thing that never changes hands is the right to shoot back.
 */
public final class Warfare {

    /** How long each side gets on the offensive. Three minutes. */
    public static final int PHASE_TICKS = 20 * 60 * 3;

    private Warfare() {
    }

    /**
     * Which of these two is on the offensive right now.
     *
     * <p>Null when they are not at war, which is not the same as either of them defending.
     */
    public static @Nullable City attacker(MinecraftServer server, City a, City b) {
        if (a == null || b == null || a.id().equals(b.id())) {
            return null;
        }
        if (Diplomacy.stance(a, b) != Relation.WAR) {
            return null;
        }
        // Whichever of them declared. If somehow both did, the earlier declaration is the one that
        // set the clock going, and the later one is a formality.
        long aStarted = a.warStarted(b.id());
        long bStarted = b.warStarted(a.id());
        City declarer;
        long started;
        if (aStarted >= 0L && (bStarted < 0L || aStarted <= bStarted)) {
            declarer = a;
            started = aStarted;
        } else if (bStarted >= 0L) {
            declarer = b;
            started = bStarted;
        } else {
            // A war carried over from a save made before the clock existed. Somebody has to be
            // attacking or both armies stand still, which is the bug this class exists to fix.
            return a;
        }

        long elapsed = server.overworld().getGameTime() - started;
        boolean declarersTurn = elapsed < 0L || (elapsed / PHASE_TICKS) % 2L == 0L;
        return declarersTurn ? declarer : (declarer == a ? b : a);
    }

    /** Whether this city is currently the one advancing on that one. */
    public static boolean attacking(MinecraftServer server, City mine, City enemy) {
        City attacker = attacker(server, mine, enemy);
        return attacker != null && attacker.id().equals(mine.id());
    }

    /** How long until the roles swap, in ticks, or -1 when these two are not at war. */
    public static long untilSwap(MinecraftServer server, City a, City b) {
        if (attacker(server, a, b) == null) {
            return -1L;
        }
        long started = Math.max(a.warStarted(b.id()), b.warStarted(a.id()));
        if (started < 0L) {
            return -1L;
        }
        long elapsed = server.overworld().getGameTime() - started;
        return PHASE_TICKS - Math.floorMod(elapsed, PHASE_TICKS);
    }
}
