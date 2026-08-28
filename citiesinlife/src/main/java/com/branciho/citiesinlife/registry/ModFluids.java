package com.branciho.citiesinlife.registry;

import com.branciho.citiesinlife.CitiesInLife;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Sewage, as an actual fluid.
 *
 * <p>It was a block before, and it was wrong in exactly the way a block is wrong: half a metre
 * deep, tiled, breakable, and spreading by rules it had invented for itself. A fluid is not a
 * thing you can approximate — you swim in it, you fall into it, it fills a hollow and finds its
 * own level, and every one of those behaviours is somewhere in vanilla's flowing-fluid code rather
 * than in a spread rule anybody could write from scratch.
 *
 * <p>So this is water, with different numbers and a brown tint. Slower to spread and slower to tick
 * because it is sludge, and it will not put a fire out or make obsidian, but everything that makes
 * water feel like a liquid comes for free.
 */
public final class ModFluids {

    public static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, CitiesInLife.MOD_ID);

    public static final DeferredRegister<Fluid> FLUIDS =
            DeferredRegister.create(Registries.FLUID, CitiesInLife.MOD_ID);

    /**
     * How it feels to be in it.
     *
     * <p>Denser and far more viscous than water, so you wade rather than swim and you sink. You can
     * still drown in it, which seemed like the right answer for a canal full of effluent.
     */
    public static final DeferredHolder<FluidType, FluidType> SEWAGE_TYPE =
            FLUID_TYPES.register("sewage", () -> new FluidType(FluidType.Properties.create()
                    .descriptionId("block.citiesinlife.sewage")
                    .density(1400)
                    .viscosity(2400)
                    .canSwim(true)
                    .canDrown(true)
                    .supportsBoating(true)
                    .motionScale(0.006D)
                    .fallDistanceModifier(0.0F)));

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> SEWAGE =
            FLUIDS.register("sewage", () -> new BaseFlowingFluid.Source(properties()));

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> FLOWING_SEWAGE =
            FLUIDS.register("flowing_sewage", () -> new BaseFlowingFluid.Flowing(properties()));

    /**
     * The numbers that make it sludge rather than water.
     *
     * <p>A shorter slope search and a faster level drop mean it does not run halfway across a
     * continent from one outfall, and a slow tick rate means it creeps. Built fresh for each of the
     * two fluids because the builder is mutable and sharing one instance is a way to have the still
     * and flowing forms quietly disagree.
     */
    private static BaseFlowingFluid.Properties properties() {
        return new BaseFlowingFluid.Properties(SEWAGE_TYPE, SEWAGE, FLOWING_SEWAGE)
                .block(ModBlocks.SEWAGE)
                .bucket(ModItems.SEWAGE_BUCKET)
                .slopeFindDistance(3)
                .levelDecreasePerBlock(2)
                .tickRate(12)
                .explosionResistance(100.0F);
    }

    private ModFluids() {
    }

    public static void register(IEventBus bus) {
        FLUID_TYPES.register(bus);
        FLUIDS.register(bus);
    }
}
