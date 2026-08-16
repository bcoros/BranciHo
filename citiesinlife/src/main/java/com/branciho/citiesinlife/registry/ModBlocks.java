package com.branciho.citiesinlife.registry;

import com.branciho.citiesinlife.CitiesInLife;
import com.branciho.citiesinlife.block.BoilerBlock;
import com.branciho.citiesinlife.block.ChimneyBlock;
import com.branciho.citiesinlife.block.FactoryOutputBlock;
import com.branciho.citiesinlife.block.PowerMastBlock;
import com.branciho.citiesinlife.block.SolarPanelBlock;
import com.branciho.citiesinlife.block.TransitStationBlock;
import com.branciho.citiesinlife.block.TurbineBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The utility blocks. */
public final class ModBlocks {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CitiesInLife.MOD_ID);

    public static final DeferredBlock<SolarPanelBlock> SOLAR_PANEL = BLOCKS.register("solar_panel",
            () -> new SolarPanelBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLUE)
                    .strength(2.0F, 4.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()));

    public static final DeferredBlock<PowerMastBlock> POWER_MAST = BLOCKS.register("power_mast",
            () -> new PowerMastBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(1.5F, 3.0F)
                    .sound(SoundType.WOOD)
                    .noOcclusion()));

    public static final DeferredBlock<TransitStationBlock> TRANSIT_STATION = BLOCKS.register("transit_station",
            () -> new TransitStationBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GRAY)
                    .strength(3.0F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .noOcclusion()));

    public static final DeferredBlock<BoilerBlock> BOILER = BLOCKS.register("boiler",
            () -> new BoilerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BROWN)
                    .strength(3.5F, 7.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    // The firebox glows through the front while it is burning.
                    .lightLevel(state -> state.getValue(BoilerBlock.LIT) ? 13 : 0)));

    public static final DeferredBlock<ChimneyBlock> CHIMNEY = BLOCKS.register("chimney",
            () -> new ChimneyBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_RED)
                    .strength(2.0F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.STONE)
                    .noOcclusion()));

    public static final DeferredBlock<TurbineBlock> TURBINE = BLOCKS.register("turbine",
            () -> new TurbineBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(4.0F, 8.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .noOcclusion()));

    public static final DeferredBlock<FactoryOutputBlock> FACTORY_OUTPUT = BLOCKS.register("factory_output",
            () -> new FactoryOutputBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.5F, 5.0F)
                    .sound(SoundType.WOOD)));

    private ModBlocks() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
    }
}
