package com.branciho.citiesinlife.registry;

import com.branciho.citiesinlife.CitiesInLife;
import com.branciho.citiesinlife.block.AlarmBlock;
import com.branciho.citiesinlife.block.BoilerBlock;
import com.branciho.citiesinlife.block.ChimneyBlock;
import com.branciho.citiesinlife.block.IndestructiblePipeBlock;
import com.branciho.citiesinlife.block.OfficeSpaceBlock;
import com.branciho.citiesinlife.block.RegisterCounterBlock;
import com.branciho.citiesinlife.block.WindmillBlock;
import com.branciho.citiesinlife.block.WindmillColour;
import com.branciho.citiesinlife.block.EndPipeBlock;
import com.branciho.citiesinlife.block.EndPumpBlock;
import com.branciho.citiesinlife.block.PipeConnectorBlock;
import com.branciho.citiesinlife.block.PumpBlock;
import com.branciho.citiesinlife.block.StarterPumpBlock;
import com.branciho.citiesinlife.block.ValveBlock;
import com.branciho.citiesinlife.block.WaterPipeBlock;
import com.branciho.citiesinlife.block.WaterStorageBlock;
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
                    // Random ticks are only here to give a chimney built in an older version a
                    // chance to notice it now has insides. Nothing about a chimney is random.
                    .randomTicks()
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

    // ------------------------------------------------------------------ water

    public static final DeferredBlock<StarterPumpBlock> STARTER_PUMP = BLOCKS.register("starter_pump",
            () -> new StarterPumpBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .strength(3.0F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)));

    public static final DeferredBlock<PumpBlock> PUMP = BLOCKS.register("pump",
            () -> new PumpBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)));

    public static final DeferredBlock<EndPumpBlock> END_PUMP = BLOCKS.register("end_pump",
            () -> new EndPumpBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WARPED_NYLIUM)
                    .strength(3.0F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)));

    public static final DeferredBlock<WaterPipeBlock> WATER_PIPE = BLOCKS.register("water_pipe",
            () -> new WaterPipeBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(1.5F, 3.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    // Random ticks are how a pipe eventually splits.
                    .randomTicks()
                    .noOcclusion()));

    public static final DeferredBlock<PipeConnectorBlock> PIPE_CONNECTOR = BLOCKS.register("pipe_connector",
            () -> new PipeConnectorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(1.5F, 3.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)));

    public static final DeferredBlock<ValveBlock> VALVE = BLOCKS.register("valve",
            () -> new ValveBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_RED)
                    .strength(1.5F, 3.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .noOcclusion()));

    public static final DeferredBlock<WaterStorageBlock> WATER_STORAGE = BLOCKS.register("water_storage",
            () -> new WaterStorageBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLUE)
                    .strength(3.0F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)));

    /**
     * The four windmill liveries.
     *
     * <p>Registered as four blocks rather than one with a colour property so each shows up in the
     * creative menu on its own - picking the colour before you place it is one decision instead of a
     * placement followed by a correction.
     */
    public static final DeferredBlock<WindmillBlock> WINDMILL_WHITE = windmill(WindmillColour.WHITE);
    public static final DeferredBlock<WindmillBlock> WINDMILL_BLACK = windmill(WindmillColour.BLACK);
    public static final DeferredBlock<WindmillBlock> WINDMILL_BLUE = windmill(WindmillColour.BLUE);
    public static final DeferredBlock<WindmillBlock> WINDMILL_GREEN = windmill(WindmillColour.GREEN);

    /** Every windmill is the same machine; only the paint differs. */
    private static DeferredBlock<WindmillBlock> windmill(WindmillColour colour) {
        return BLOCKS.register(colour.blockName(),
                () -> new WindmillBlock(BlockBehaviour.Properties.of()
                        .mapColor(MapColor.QUARTZ)
                        .strength(4.0F, 8.0F)
                        .requiresCorrectToolForDrops()
                        .sound(SoundType.METAL)
                        .noOcclusion(), colour));
    }

    public static final DeferredBlock<IndestructiblePipeBlock> INDESTRUCTIBLE_PIPE =
            BLOCKS.register("indestructible_pipe",
                    () -> new IndestructiblePipeBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_LIGHT_GRAY)
                            .strength(3.0F, 9.0F)
                            .requiresCorrectToolForDrops()
                            .sound(SoundType.METAL)
                            .noOcclusion()));

    /**
     * A desk with a chair in front of it. One citizen works here.
     *
     * <p>Wood rather than metal, and it does not need a pickaxe: an office is furniture, and having
     * to fetch a tool to move a desk one square left would be a small misery repeated forever.
     */
    public static final DeferredBlock<OfficeSpaceBlock> OFFICE_SPACE = BLOCKS.register("office_space",
            () -> new OfficeSpaceBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .strength(1.5F, 3.0F)
                    .sound(SoundType.WOOD)
                    .noOcclusion()));

    /** A shop counter with a till on it. Two citizens work here, and this is where a shop earns. */
    public static final DeferredBlock<RegisterCounterBlock> REGISTER_COUNTER =
            BLOCKS.register("register_counter",
                    () -> new RegisterCounterBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_LIGHT_GRAY)
                            .strength(2.0F, 4.0F)
                            .sound(SoundType.WOOD)
                            .noOcclusion()));

    /**
     * The plant siren.
     *
     * <p>Glows steadily rather than in step with its own flash - relighting the chunk three times a
     * second for a flicker the lens already shows would be an expensive way to say the same thing.
     */
    public static final DeferredBlock<AlarmBlock> ALARM = BLOCKS.register("alarm",
            () -> new AlarmBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_RED)
                    .strength(2.0F, 4.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .lightLevel(AlarmBlock::lightFor)
                    .noOcclusion()));

    /**
     * The tap. Not the End Pump - that is the seam where drawn links become pipe; this is the far
     * end of the pipes, where the number turns back into water.
     */
    public static final DeferredBlock<EndPipeBlock> END_PIPE = BLOCKS.register("end_pipe",
            () -> new EndPipeBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .strength(2.0F, 4.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.COPPER)
                    .noOcclusion()));

    private ModBlocks() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
    }
}
