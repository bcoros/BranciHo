package com.branciho.livingcities.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Server-side configuration.
 *
 * <p>Everything that affects simulation lives in the SERVER config, so a multiplayer server dictates
 * the rules and a client cannot alter its own economy. Purely cosmetic client preferences (NPC render
 * density, overlay colours) will get their own client spec when the client layer grows.
 */
public final class LivingCitiesConfig {

    public static final ModConfigSpec SERVER_SPEC;
    public static final Server SERVER;

    static {
        Pair<Server, ModConfigSpec> pair = new ModConfigSpec.Builder().configure(Server::new);
        SERVER = pair.getLeft();
        SERVER_SPEC = pair.getRight();
    }

    private LivingCitiesConfig() {
    }

    /**
     * The capacity multiplier, or 1.0 if the config has not loaded yet.
     *
     * <p>Capacity is computed from plain data objects that can legitimately be constructed before the
     * server config exists (loading a save, datagen, tests). Reading the raw value there would throw,
     * so this degrades to the neutral default instead of taking the server down over a tuning knob.
     */
    public static double capacityScale() {
        try {
            return SERVER.capacityScale.get();
        } catch (IllegalStateException notLoadedYet) {
            return 1.0D;
        }
    }

    public static final class Server {

        // --- economy ---
        public final ModConfigSpec.LongValue startingTreasury;
        public final ModConfigSpec.LongValue baseChunkClaimCost;
        public final ModConfigSpec.DoubleValue claimCostGrowth;
        public final ModConfigSpec.IntValue residentialTaxPercent;
        public final ModConfigSpec.IntValue commercialTaxPercent;
        public final ModConfigSpec.IntValue officeTaxPercent;
        public final ModConfigSpec.IntValue industrialTaxPercent;

        // --- territory ---
        public final ModConfigSpec.BooleanValue requireAdjacentClaims;
        public final ModConfigSpec.IntValue maxClaimsPerCity;

        // --- buildings ---
        public final ModConfigSpec.IntValue maxSelectionVolume;
        public final ModConfigSpec.IntValue maxSelectionHeight;
        public final ModConfigSpec.IntValue scanBlocksPerTick;
        public final ModConfigSpec.DoubleValue capacityScale;

        // --- simulation ---
        public final ModConfigSpec.IntValue simulationIntervalTicks;
        public final ModConfigSpec.DoubleValue growthRate;

        // --- npcs ---
        public final ModConfigSpec.IntValue maxPhysicalNpcs;
        public final ModConfigSpec.DoubleValue npcDensityMultiplier;

        // --- power ---
        public final ModConfigSpec.DoubleValue kwPerResident;
        public final ModConfigSpec.DoubleValue kwPerJob;
        public final ModConfigSpec.IntValue solarPanelKw;
        public final ModConfigSpec.IntValue coalGeneratorKw;
        public final ModConfigSpec.IntValue substationRadius;
        public final ModConfigSpec.BooleanValue powerRequired;

        // --- creative / admin ---
        public final ModConfigSpec.BooleanValue creativeBypassesCost;

        Server(ModConfigSpec.Builder builder) {
            builder.comment("Living Cities - server simulation settings").push("economy");
            startingTreasury = builder
                    .comment("Treasury a newly founded city starts with, in cents (100 = $1.00).")
                    .defineInRange("startingTreasury", 5_000_00L, 0L, Long.MAX_VALUE / 2);
            baseChunkClaimCost = builder
                    .comment("Cost of the first territory chunk, in cents.")
                    .defineInRange("baseChunkClaimCost", 250_00L, 0L, Long.MAX_VALUE / 2);
            claimCostGrowth = builder
                    .comment("Each owned chunk multiplies the next claim's price by this much.",
                            "1.0 disables escalation; 1.02 makes the 100th chunk roughly 7x the first.")
                    .defineInRange("claimCostGrowth", 1.02D, 1.0D, 2.0D);
            residentialTaxPercent = builder.defineInRange("residentialTaxPercent", 8, 0, 100);
            commercialTaxPercent = builder.defineInRange("commercialTaxPercent", 10, 0, 100);
            officeTaxPercent = builder.defineInRange("officeTaxPercent", 9, 0, 100);
            industrialTaxPercent = builder.defineInRange("industrialTaxPercent", 7, 0, 100);
            builder.pop();

            builder.push("territory");
            requireAdjacentClaims = builder
                    .comment("Require new claims to touch existing territory, preventing far-flung land grabs.")
                    .define("requireAdjacentClaims", true);
            maxClaimsPerCity = builder
                    .comment("Hard cap on chunks a single city may own. 0 disables the cap.")
                    .defineInRange("maxClaimsPerCity", 4096, 0, 1_000_000);
            builder.pop();

            builder.push("buildings");
            maxSelectionVolume = builder
                    .comment("Largest building selection allowed, in blocks. Guards against a client",
                            "requesting a scan of a million-block region to stall the server.")
                    .defineInRange("maxSelectionVolume", 512_000, 1_000, 8_000_000);
            maxSelectionHeight = builder
                    .comment("Largest vertical extent of a building selection, in blocks.")
                    .defineInRange("maxSelectionHeight", 384, 8, 384);
            scanBlocksPerTick = builder
                    .comment("Building scan budget per server tick. Lower is gentler on the tick rate;",
                            "higher finishes large skyscrapers sooner.")
                    .defineInRange("scanBlocksPerTick", 24_000, 1_000, 500_000);
            capacityScale = builder
                    .comment("Global multiplier on every building's residents and jobs.",
                            "Defaults are tuned so a small house holds 6, an apartment block ~180 and a",
                            "residential tower ~1800, which is deliberately denser than real life because",
                            "Minecraft builds are small next to the cities players picture.",
                            "Set around 0.35 for Cities-Skylines-style realism.")
                    .defineInRange("capacityScale", 1.0D, 0.05D, 10.0D);
            builder.pop();

            builder.push("simulation");
            simulationIntervalTicks = builder
                    .comment("Ticks between city simulation steps. Cities are staggered across this window,",
                            "so raising it spreads load rather than delaying everything at once.")
                    .defineInRange("simulationIntervalTicks", 100, 20, 2_000);
            growthRate = builder
                    .comment("How fast population moves toward its target each simulation step.",
                            "Values above ~0.3 make population oscillate instead of settling.")
                    .defineInRange("growthRate", 0.08D, 0.001D, 0.5D);
            builder.pop();

            builder.push("npcs");
            maxPhysicalNpcs = builder
                    .comment("Hard ceiling on simultaneously spawned citizen entities across the server.",
                            "Virtual population is unaffected by this; only the visible crowd is.")
                    .defineInRange("maxPhysicalNpcs", 120, 0, 2_000);
            npcDensityMultiplier = builder
                    .comment("Scales how busy streets look. 0 disables physical NPCs entirely.")
                    .defineInRange("npcDensityMultiplier", 1.0D, 0.0D, 5.0D);
            builder.pop();

            builder.push("power");
            kwPerResident = builder
                    .comment("Electrical demand per unit of housing capacity, in kilowatts.")
                    .defineInRange("kwPerResident", 0.5D, 0.0D, 100.0D);
            kwPerJob = builder
                    .comment("Electrical demand per job, in kilowatts. Workplaces draw more than homes.")
                    .defineInRange("kwPerJob", 1.2D, 0.0D, 100.0D);
            solarPanelKw = builder
                    .comment("Output of one solar panel in full daylight, in kilowatts.")
                    .defineInRange("solarPanelKw", 6, 0, 100_000);
            coalGeneratorKw = builder
                    .comment("Output of one coal generator while burning, in kilowatts.")
                    .defineInRange("coalGeneratorKw", 120, 0, 100_000);
            substationRadius = builder
                    .comment("How far a substation distributes power to buildings, in blocks.",
                            "This is the 'do not wire every apartment' radius; raising it makes",
                            "districts easier to cover and the grid less of a planning problem.")
                    .defineInRange("substationRadius", 48, 8, 256);
            powerRequired = builder
                    .comment("Whether buildings actually suffer without electricity.",
                            "Turn off to keep the grid as decoration while still simulating a city.")
                    .define("powerRequired", true);
            builder.pop();

            builder.push("creative");
            creativeBypassesCost = builder
                    .comment("Creative-mode players claim territory and register buildings for free.")
                    .define("creativeBypassesCost", true);
            builder.pop();
        }
    }
}
