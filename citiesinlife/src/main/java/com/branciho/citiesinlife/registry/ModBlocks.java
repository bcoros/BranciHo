package com.branciho.citiesinlife.registry;

import com.branciho.citiesinlife.CitiesInLife;
import com.branciho.citiesinlife.block.AlarmBlock;
import com.branciho.citiesinlife.block.BoilerBlock;
import com.branciho.citiesinlife.block.ChimneyBlock;
import com.branciho.citiesinlife.block.IndestructiblePipeBlock;
import com.branciho.citiesinlife.block.OfficeSpaceBlock;
import com.branciho.citiesinlife.block.RegisterCounterBlock;
import com.branciho.citiesinlife.block.ServiceSpawnerBlock;
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
import com.branciho.citiesinlife.block.ControlRodBlock;
import com.branciho.citiesinlife.block.CoolingPortBlock;
import com.branciho.citiesinlife.block.FuelRodBlock;
import com.branciho.citiesinlife.block.GridPylonBlock;
import com.branciho.citiesinlife.block.MainMonitorBlock;
import com.branciho.citiesinlife.block.NuclearTurbineBlock;
import com.branciho.citiesinlife.block.PressurizedPipeBlock;
import com.branciho.citiesinlife.block.ReactorLeverBlock;
import com.branciho.citiesinlife.block.SealingBlock;
import com.branciho.citiesinlife.block.SteamEmitterBlock;
import com.branciho.citiesinlife.block.UraniumStorageBlock;
import com.branciho.citiesinlife.nuclear.CoolingPort;
import com.branciho.citiesinlife.nuclear.ReactorLever;
import com.branciho.citiesinlife.block.SewageBlock;
import com.branciho.citiesinlife.block.SewageCollectorBlock;
import com.branciho.citiesinlife.block.PowerMastBlock;
import com.branciho.citiesinlife.block.SolarPanelBlock;
import com.branciho.citiesinlife.block.TouristAirplaneBlock;
import com.branciho.citiesinlife.block.TransitStationBlock;
import com.branciho.citiesinlife.block.TransportAirplaneBlock;
import com.branciho.citiesinlife.block.TurbineBlock;
import net.minecraft.world.level.block.Block;
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
                    .forceSolidOn()
                    .noOcclusion()));

    public static final DeferredBlock<PowerMastBlock> POWER_MAST = BLOCKS.register("power_mast",
            () -> new PowerMastBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(1.5F, 3.0F)
                    .sound(SoundType.WOOD)
                    .forceSolidOn()
                    .noOcclusion()));

    /**
     * Uranium ore, in the two flavours every ore needs.
     *
     * <p>Two blocks rather than one because minecraft:stone_ore_replaceables genuinely does not
     * include deepslate or tuff — a single-target ore generates down to y=0 and then stops dead in
     * the deepslate layer, which looks exactly like the worldgen being broken.
     */
    public static final DeferredBlock<Block> URANIUM_ORE = BLOCKS.register("uranium_ore",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(3.0F, 3.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.STONE)));

    public static final DeferredBlock<Block> DEEPSLATE_URANIUM_ORE =
            BLOCKS.register("deepslate_uranium_ore",
                    () -> new Block(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.DEEPSLATE)
                            .strength(4.5F, 3.0F)
                            .requiresCorrectToolForDrops()
                            .sound(SoundType.DEEPSLATE)));

    /**
     * The steel lattice pylon. Metal, tougher than the wooden mast, and mined with a pickaxe rather
     * than an axe - so it belongs in the pickaxe tag, not the axe one.
     */
    public static final DeferredBlock<GridPylonBlock> GRID_PYLON = BLOCKS.register("grid_pylon",
            () -> new GridPylonBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5F, 8.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .forceSolidOn()
                    .noOcclusion()));

    // ------------------------------------------------------------- the reactor

    /**
     * The core.
     *
     * <p>Rods and their lid are metal, mined with a pickaxe, and every one of them holds water in
     * its own space — that waterlogging IS how "submerged" is implemented, so it is not optional
     * decoration on these three.
     */
    public static final DeferredBlock<FuelRodBlock> FUEL_ROD = BLOCKS.register("fuel_rod",
            () -> new FuelRodBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .lightLevel(state -> state.getValue(FuelRodBlock.FILL) > 0 ? 4 : 0)
                    .forceSolidOn()
                    .noOcclusion()));

    public static final DeferredBlock<ControlRodBlock> CONTROL_ROD = BLOCKS.register("control_rod",
            () -> new ControlRodBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GRAY)
                    .strength(3.0F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .forceSolidOn()
                    .noOcclusion()));

    public static final DeferredBlock<SealingBlock> SEALING_BLOCK = BLOCKS.register("sealing_block",
            () -> new SealingBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(4.0F, 8.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .forceSolidOn()
                    .noOcclusion()));

    public static final DeferredBlock<UraniumStorageBlock> URANIUM_STORAGE =
            BLOCKS.register("uranium_storage",
                    () -> new UraniumStorageBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_GREEN)
                            .strength(3.5F, 8.0F)
                            .requiresCorrectToolForDrops()
                            .sound(SoundType.METAL)));

    public static final DeferredBlock<NuclearTurbineBlock> NUCLEAR_TURBINE =
            BLOCKS.register("nuclear_turbine",
                    () -> new NuclearTurbineBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(5.0F, 12.0F)
                            .requiresCorrectToolForDrops()
                            .sound(SoundType.METAL)
                            .forceSolidOn()
                            .noOcclusion()));

    /** The four cooling ports. One class, four registrations, four entries in the menu. */
    public static final DeferredBlock<CoolingPortBlock> INPUT_WATER_PORT =
            coolingPort(CoolingPort.INPUT_WATER);

    public static final DeferredBlock<CoolingPortBlock> OUTPUT_COOLED_PORT =
            coolingPort(CoolingPort.OUTPUT_COOLED);

    public static final DeferredBlock<CoolingPortBlock> INPUT_COOLED_PORT =
            coolingPort(CoolingPort.INPUT_COOLED);

    public static final DeferredBlock<CoolingPortBlock> OUTPUT_HEATED_PORT =
            coolingPort(CoolingPort.OUTPUT_HEATED);

    private static DeferredBlock<CoolingPortBlock> coolingPort(CoolingPort port) {
        return BLOCKS.register(port.id() + "_port",
                () -> new CoolingPortBlock(BlockBehaviour.Properties.of()
                        .mapColor(MapColor.COLOR_LIGHT_BLUE)
                        .strength(3.0F, 6.0F)
                        .requiresCorrectToolForDrops()
                        .sound(SoundType.METAL), port));
    }

    public static final DeferredBlock<SteamEmitterBlock> STEAM_EMITTER =
            BLOCKS.register("steam_emitter",
                    () -> new SteamEmitterBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_BLACK)
                            .strength(3.0F, 6.0F)
                            .requiresCorrectToolForDrops()
                            .sound(SoundType.METAL)));

    /** A water pipe with a rating plate. Inherits every connection rule its parent has. */
    public static final DeferredBlock<PressurizedPipeBlock> PRESSURIZED_PIPE =
            BLOCKS.register("pressurized_pipe",
                    () -> new PressurizedPipeBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_LIGHT_GRAY)
                            .strength(2.0F, 6.0F)
                            .requiresCorrectToolForDrops()
                            .sound(SoundType.METAL)
                            .forceSolidOn()
                            .noOcclusion()));

    /** The four controls. Same class, same model, same animation; different jobs. */
    public static final DeferredBlock<ReactorLeverBlock> COOLER_LEVER = lever(ReactorLever.COOLER);

    public static final DeferredBlock<ReactorLeverBlock> HEAT_LEVER = lever(ReactorLever.HEAT);

    public static final DeferredBlock<ReactorLeverBlock> PRESSURE_LEVER =
            lever(ReactorLever.PRESSURE);

    public static final DeferredBlock<ReactorLeverBlock> TURBINE_POWER = lever(ReactorLever.TURBINE);

    private static DeferredBlock<ReactorLeverBlock> lever(ReactorLever lever) {
        return BLOCKS.register(lever.id(),
                () -> new ReactorLeverBlock(BlockBehaviour.Properties.of()
                        .mapColor(MapColor.COLOR_BROWN)
                        .strength(2.0F, 4.0F)
                        .sound(SoundType.COPPER)
                        .noCollission()
                        .forceSolidOn()
                        .noOcclusion(), lever));
    }

    public static final DeferredBlock<MainMonitorBlock> MAIN_MONITOR =
            BLOCKS.register("main_monitor",
                    () -> new MainMonitorBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_BLACK)
                            .strength(2.5F, 5.0F)
                            .sound(SoundType.METAL)
                            .lightLevel(state -> 6)
                            .forceSolidOn()
                            .noOcclusion()));

    /**
     * The tourist airport. Full cube, so no forceSolidOn/noOcclusion pair - this codebase only uses
     * those together for models that do not fill their block.
     */
    public static final DeferredBlock<TouristAirplaneBlock> TOURIST_AIRPLANE =
            BLOCKS.register("tourist_airplane",
                    () -> new TouristAirplaneBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_LIGHT_GRAY)
                            .strength(3.0F, 6.0F)
                            .requiresCorrectToolForDrops()
                            .sound(SoundType.METAL)));

    /** The transport airport. Same shell; what differs is entirely in the block entity. */
    public static final DeferredBlock<TransportAirplaneBlock> TRANSPORT_AIRPLANE =
            BLOCKS.register("transport_airplane",
                    () -> new TransportAirplaneBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_GRAY)
                            .strength(3.0F, 6.0F)
                            .requiresCorrectToolForDrops()
                            .sound(SoundType.METAL)));

    public static final DeferredBlock<TransitStationBlock> TRANSIT_STATION = BLOCKS.register("transit_station",
            () -> new TransitStationBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GRAY)
                    .strength(3.0F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .forceSolidOn()
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
                    .forceSolidOn()
                    .noOcclusion()));

    public static final DeferredBlock<TurbineBlock> TURBINE = BLOCKS.register("turbine",
            () -> new TurbineBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(4.0F, 8.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .forceSolidOn()
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
                    .forceSolidOn()
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
                    .forceSolidOn()
                    .noOcclusion()));

    public static final DeferredBlock<WaterStorageBlock> WATER_STORAGE = BLOCKS.register("water_storage",
            () -> new WaterStorageBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLUE)
                    .strength(3.0F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)));

    /**
     * The sewage collector. A tank's counterpart, and built like one: metal, mined with a pickaxe.
     */
    public static final DeferredBlock<SewageCollectorBlock> SEWAGE_COLLECTOR =
            BLOCKS.register("sewage_collector",
                    () -> new SewageCollectorBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.TERRACOTTA_BROWN)
                            .strength(3.0F, 6.0F)
                            .requiresCorrectToolForDrops()
                            .sound(SoundType.METAL)));

    /**
     * What comes out of an outfall.
     *
     * <p>No loot table and instantly breakable, because it is scenery the machine puts down and
     * takes away again. {@code replaceable} so anything placed into it simply displaces it, the way
     * water behaves, and {@code noOcclusion} so it does not black out the block beneath.
     */
    public static final DeferredBlock<SewageBlock> SEWAGE = BLOCKS.register("sewage",
            () -> new SewageBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.TERRACOTTA_BROWN)
                    .replaceable()
                    .noCollission()
                    .noLootTable()
                    .instabreak()
                    .sound(SoundType.SLIME_BLOCK)
                    .forceSolidOn()
                    .noOcclusion()));

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
                        .forceSolidOn()
                        .noOcclusion(), colour));
    }

    public static final DeferredBlock<IndestructiblePipeBlock> INDESTRUCTIBLE_PIPE =
            BLOCKS.register("indestructible_pipe",
                    () -> new IndestructiblePipeBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_LIGHT_GRAY)
                            .strength(3.0F, 9.0F)
                            .requiresCorrectToolForDrops()
                            .sound(SoundType.METAL)
                            // It never splits; the random tick is only how a pipe laid before legs
                            // existed notices it should be standing on something.
                            .randomTicks()
                            .forceSolidOn()
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
                    .forceSolidOn()
                    .noOcclusion()));

    /** A shop counter with a till on it. Two citizens work here, and this is where a shop earns. */
    public static final DeferredBlock<RegisterCounterBlock> REGISTER_COUNTER =
            BLOCKS.register("register_counter",
                    () -> new RegisterCounterBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_LIGHT_GRAY)
                            .strength(2.0F, 4.0F)
                            .sound(SoundType.WOOD)
                            .forceSolidOn()
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
                    .forceSolidOn()
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
                    // As with the other pipes: the only thing random ticks do here is put an older
                    // tap's sides right.
                    .randomTicks()
                    .forceSolidOn()
                    .noOcclusion()));

    /**
     * The one block every service shares.
     *
     * <p>Stone-strength and no pickaxe needed: it is a fitting inside a building the player has
     * already built, and making them fetch a tool to move it a square left would be a small misery
     * repeated six times over.
     */
    public static final DeferredBlock<ServiceSpawnerBlock> SERVICE_SPAWNER =
            BLOCKS.register("service_spawner",
                    () -> new ServiceSpawnerBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_LIGHT_GRAY)
                            .strength(2.5F, 5.0F)
                            .sound(SoundType.METAL)));

    private ModBlocks() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
    }
}
