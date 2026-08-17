package com.branciho.citiesinlife.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The settings a player can change without touching the code.
 *
 * <p>There is exactly one at the moment and it is the important one: how many citizens a city is
 * allowed to have walking about. NPCs are the only part of this mod that costs real frames — the
 * economy is arithmetic on a handful of numbers and does not care how big your city is, but every
 * citizen is an entity with a pathfinder attached. Somebody playing on a laptop should be able to
 * turn them down or off entirely and still have the whole rest of the mod.
 *
 * <p>Server-side rather than client-side, because it decides what actually gets spawned. Turning it
 * down does not hide citizens from one player; it means the city has fewer of them.
 */
public final class CitiesInLifeConfig {

    /** What a city gets if nobody has touched the setting. */
    public static final int DEFAULT_CITIZENS = 15;

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue CITIZENS_PER_CITY = BUILDER
            .comment(
                    "How many physical citizens each city may have walking around at once.",
                    "Set to 0 to switch NPCs off entirely - the city still has its population,",
                    "its jobs and its economy, you simply do not see anybody.",
                    "15 is the full experience; 10, 5, 3 and 2 are the lighter settings.")
            .defineInRange("citizensPerCity", DEFAULT_CITIZENS, 0, DEFAULT_CITIZENS);

    /**
     * Whether server operators ignore city borders.
     *
     * <p>Off by default, and that default is the whole point. It used to be on and unconditional,
     * which meant the person hosting the world - who is always an operator - could freely build in
     * and demolish everybody else's cities while they could not touch his. Two players sat next to
     * each other playing by different rules, with nothing on screen to explain why.
     *
     * <p>Turn it on if you are running a server and need to repair somebody's build. It is an admin
     * tool, not a way to play.
     */
    public static final ModConfigSpec.BooleanValue OPS_IGNORE_BORDERS = BUILDER
            .comment(
                    "Whether server operators can build, break and fight inside other players'",
                    "cities regardless of permission. Off by default - the host of a world is an",
                    "operator, and having them exempt from the rules everybody else plays by is",
                    "not a feature. Turn on only if you need it for moderation.")
            .define("opsIgnoreCityBorders", false);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private CitiesInLifeConfig() {
    }

    /**
     * The cap, safe to ask for at any time.
     *
     * <p>Config values throw if they are read before the file has been loaded, and the citizen
     * director is wired to the server tick, which can begin before that has happened on a fast
     * dedicated server. Falling back to the default is far better than crashing the world.
     */
    public static int citizensPerCity() {
        return SPEC.isLoaded() ? CITIZENS_PER_CITY.get() : DEFAULT_CITIZENS;
    }

    /** Safe at any time, for the same reason as above. Defaults to enforcing borders on everybody. */
    public static boolean opsIgnoreBorders() {
        return SPEC.isLoaded() && OPS_IGNORE_BORDERS.get();
    }
}
