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
        public final ModConfigSpec.DoubleValue residentsPerHundredBlocks;
        public final ModConfigSpec.DoubleValue jobsPerHundredBlocksCommercial;
        public final ModConfigSpec.DoubleValue jobsPerHundredBlocksOffice;
        public final ModConfigSpec.DoubleValue jobsPerHundredBlocksIndustrial;

        // --- simulation ---
        public final ModConfigSpec.IntValue simulationIntervalTicks;
        public final ModConfigSpec.DoubleValue growthRate;

        // --- npcs ---
        public final ModConfigSpec.IntValue maxPhysicalNpcs;
        public final ModConfigSpec.DoubleValue npcDensityMultiplier;

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
            residentsPerHundredBlocks = builder
                    .comment("Residents per 100 usable floor blocks of residential space.")
                    .defineInRange("residentsPerHundredBlocks", 4.0D, 0.1D, 100.0D);
            jobsPerHundredBlocksCommercial = builder
                    .defineInRange("jobsPerHundredBlocksCommercial", 3.0D, 0.1D, 100.0D);
            jobsPerHundredBlocksOffice = builder
                    .defineInRange("jobsPerHundredBlocksOffice", 5.0D, 0.1D, 100.0D);
            jobsPerHundredBlocksIndustrial = builder
                    .defineInRange("jobsPerHundredBlocksIndustrial", 1.5D, 0.1D, 100.0D);
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

            builder.push("creative");
            creativeBypassesCost = builder
                    .comment("Creative-mode players claim territory and register buildings for free.")
                    .define("creativeBypassesCost", true);
            builder.pop();
        }
    }
}
