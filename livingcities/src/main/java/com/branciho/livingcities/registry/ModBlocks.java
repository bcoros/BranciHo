package com.branciho.livingcities.registry;

import com.branciho.livingcities.LivingCities;
import com.branciho.livingcities.block.CityHallCoreBlock;
import com.branciho.livingcities.block.CoalGeneratorBlock;
import com.branciho.livingcities.block.EntranceMarkerBlock;
import com.branciho.livingcities.block.PowerCableBlock;
import com.branciho.livingcities.block.SolarPanelBlock;
import com.branciho.livingcities.block.SubstationBlock;
import com.branciho.livingcities.block.TransmissionPylonBlock;
import com.branciho.livingcities.block.PathNodeBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {

    private ModBlocks() {
    }

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(LivingCities.MOD_ID);

    /** Placed inside a player-built city hall; creating a city is done through this block. */
    public static final DeferredBlock<CityHallCoreBlock> CITY_HALL_CORE = BLOCKS.registerBlock(
            "city_hall_core",
            CityHallCoreBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_GRAY)
                    .strength(4.0F, 1200.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .pushReaction(PushReaction.BLOCK));

    /** Marks a doorway NPCs should use instead of trying to path through walls. */
    public static final DeferredBlock<EntranceMarkerBlock> ENTRANCE_MARKER = BLOCKS.registerBlock(
            "entrance_marker",
            EntranceMarkerBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_YELLOW)
                    .strength(1.0F)
                    .sound(SoundType.STONE)
                    .noOcclusion());

    /** A vertex in the city pedestrian graph. Invisible outside city management mode. */
    public static final DeferredBlock<PathNodeBlock> PATH_NODE = BLOCKS.registerBlock(
            "path_node",
            PathNodeBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_CYAN)
                    .strength(1.0F)
                    .sound(SoundType.STONE)
                    .noOcclusion()
                    .noCollission());

    // --- electrical infrastructure -------------------------------------------------

    public static final DeferredBlock<PowerCableBlock> POWER_CABLE = BLOCKS.registerBlock(
            "power_cable",
            PowerCableBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BROWN)
                    .strength(0.5F)
                    .sound(SoundType.WOOL)
                    .noOcclusion()
                    .noCollission());

    public static final DeferredBlock<TransmissionPylonBlock> TRANSMISSION_PYLON = BLOCKS.registerBlock(
            "transmission_pylon",
            TransmissionPylonBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.5F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops());

    public static final DeferredBlock<SubstationBlock> SUBSTATION = BLOCKS.registerBlock(
            "substation",
            SubstationBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_GRAY)
                    .strength(3.5F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops());

    public static final DeferredBlock<SolarPanelBlock> SOLAR_PANEL = BLOCKS.registerBlock(
            "solar_panel",
            SolarPanelBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLUE)
                    .strength(1.5F)
                    .sound(SoundType.GLASS)
                    .noOcclusion());

    public static final DeferredBlock<CoalGeneratorBlock> COAL_GENERATOR = BLOCKS.registerBlock(
            "coal_generator",
            CoalGeneratorBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GRAY)
                    .strength(3.5F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    // Lit only when actually burning, so a running plant reads at a glance.
                    .lightLevel(state -> state.getValue(CoalGeneratorBlock.LIT) ? 13 : 0));

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
    }
}
