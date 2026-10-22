package com.branciho.citiesinlife.blockentity;

import com.branciho.citiesinlife.block.ChimneyBlock;
import com.branciho.citiesinlife.city.CityData;
import com.branciho.citiesinlife.registry.ModBlockEntities;
import com.branciho.citiesinlife.structure.Structure;
import com.branciho.citiesinlife.structure.StructureType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * A chimney on an ordinary house.
 *
 * <p>Nothing to do with the power plant, which drives its chimney from the boiler. This is the
 * cosmetic half: draw a box round a house with the Planner Wand, put a chimney on the roof, and it
 * smokes whenever somebody indoors has a furnace going.
 *
 * <p>It reads a lit vanilla furnace and nothing else. A modded machine may be doing something that
 * looks like smelting or may be doing something that emphatically is not, and guessing wrong is a
 * chimney that smokes for no reason anyone can see.
 */
public class ChimneyBlockEntity extends BlockEntity {

    /** How often the building is searched for a lit furnace. Smoke is not worth a per-tick scan. */
    private static final int CHECK_INTERVAL = 60;

    /** How often a smoking chimney puffs. */
    private static final int PUFF_INTERVAL = 12;

    /** Biggest house the scan will walk. A building past this is not a cottage with a hearth. */
    private static final int MAX_HOUSE_VOLUME = 32768;

    private boolean burning;
    private int ticksRun;

    public ChimneyBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CHIMNEY.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ChimneyBlockEntity chimney) {
        if (chimney.ticksRun % CHECK_INTERVAL == 0) {
            chimney.burning = ChimneyBlock.isOpen(level, pos) && hearthLit(level, pos);
        }
        if (chimney.burning && chimney.ticksRun % PUFF_INTERVAL == 0
                && level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                    pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D,
                    2, 0.08D, 0.0D, 0.08D, 0.012D);
        }
        chimney.ticksRun++;
    }

    /** Whether the building this chimney belongs to has a vanilla furnace alight in it. */
    private static boolean hearthLit(Level level, BlockPos pos) {
        MinecraftServer server = level.getServer();
        if (server == null) {
            return false;
        }
        Structure house = CityData.get(server).structureAt(level.dimension(), pos);
        // A power plant's chimney is driven by its boiler, not by somebody's furnace.
        if (house == null || house.type().isPlant()) {
            return false;
        }

        BlockPos min = house.min();
        BlockPos max = house.max();
        long volume = (long) (max.getX() - min.getX() + 1)
                * (max.getY() - min.getY() + 1)
                * (max.getZ() - min.getZ() + 1);
        if (volume > MAX_HOUSE_VOLUME) {
            return false;
        }

        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = min.getY(); y <= max.getY(); y++) {
            for (int z = min.getZ(); z <= max.getZ(); z++) {
                for (int x = min.getX(); x <= max.getX(); x++) {
                    cursor.set(x, y, z);
                    BlockState found = level.getBlockState(cursor);
                    if (!found.is(Blocks.FURNACE) && !found.is(Blocks.SMOKER)
                            && !found.is(Blocks.BLAST_FURNACE)) {
                        continue;
                    }
                    if (found.hasProperty(BlockStateProperties.LIT) && found.getValue(BlockStateProperties.LIT)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
