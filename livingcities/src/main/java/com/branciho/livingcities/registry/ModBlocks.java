package com.branciho.livingcities.registry;

import com.branciho.livingcities.LivingCities;
import com.branciho.livingcities.block.CityHallCoreBlock;
import com.branciho.livingcities.block.EntranceMarkerBlock;
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

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
    }
}
