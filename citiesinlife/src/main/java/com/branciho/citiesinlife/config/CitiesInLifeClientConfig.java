package com.branciho.citiesinlife.config;

import net.minecraft.util.Mth;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The settings that belong to one player rather than to the world.
 *
 * <p>Separate from {@link CitiesInLifeConfig} and it has to be. Everything in there decides what
 * the world actually contains — how many citizens get spawned, how big a blast is — so it is the
 * host's to set and everybody plays by the same answer. How loud a turbine is in your ears decides
 * nothing about the world at all, and a player who finds the reactor hall too noisy should be able
 * to turn it down without asking the person hosting.
 *
 * <p>Which is also why this one is not gated behind owning the world the way the settings screen's
 * other rows are. It is a volume slider.
 */
public final class CitiesInLifeClientConfig {

    /** Full volume, which is what the sounds were balanced at. */
    public static final int DEFAULT_MACHINE_VOLUME = 100;

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue MACHINE_VOLUME = BUILDER
            .comment(
                    "How loud the mod's machines and buildings are, as a percentage.",
                    "Covers everything this mod makes a noise with: turbines, boilers, pumps,",
                    "windmills, masts, reactor rods, cars, sirens and the hum of a lived-in",
                    "building. 0 switches the lot off without changing anything else - the",
                    "machines still run, they simply stop being audible.",
                    "Vanilla's own Blocks slider still applies on top of this.")
            .defineInRange("machineVolume", DEFAULT_MACHINE_VOLUME, 0, 100);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private CitiesInLifeClientConfig() {
    }

    /**
     * The setting, safe to ask for at any time.
     *
     * <p>Read on nearly every client tick from block ambience, which can begin before the config
     * file has been parsed. Falling back to full is both the right default and better than
     * throwing somewhere a throw would take the whole client down.
     */
    public static int machineVolumePercent() {
        return SPEC.isLoaded() ? MACHINE_VOLUME.get() : DEFAULT_MACHINE_VOLUME;
    }

    /** The same number as a multiplier, which is how every caller actually wants it. */
    public static float machineVolume() {
        return machineVolumePercent() / 100.0F;
    }

    /**
     * Change it and write it out.
     *
     * <p>Saved immediately rather than on closing the screen, because this is the one row on that
     * screen a player who does not own the world is allowed to touch — and the Save button there is
     * greyed out for exactly those players.
     */
    public static void setMachineVolume(int percent) {
        int next = Mth.clamp(percent, 0, 100);
        if (!SPEC.isLoaded() || next == MACHINE_VOLUME.get()) {
            return;
        }
        MACHINE_VOLUME.set(next);
        MACHINE_VOLUME.save();
    }
}
