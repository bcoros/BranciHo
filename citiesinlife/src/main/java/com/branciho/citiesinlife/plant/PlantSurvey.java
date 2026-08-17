package com.branciho.citiesinlife.plant;

import com.branciho.citiesinlife.block.BoilerBlock;
import com.branciho.citiesinlife.block.ChimneyBlock;
import com.branciho.citiesinlife.block.TurbineBlock;
import com.branciho.citiesinlife.block.WindmillBlock;
import com.branciho.citiesinlife.city.CityData;
import com.branciho.citiesinlife.structure.Structure;
import com.branciho.citiesinlife.structure.StructureType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * What is inside a registered Turbine Power Plant.
 *
 * <p>Boilers and windmills both need to answer the same three questions — which turbine is mine, is
 * there a chimney, and is this plant even coherent — so they ask them in one place. Two copies of
 * this walk would be two chances for a boiler and a windmill to disagree about what they are
 * standing in, which is exactly the disagreement the mixed-plant rule exists to prevent.
 *
 * <p>Pairing is by sorted position: the nth generator in the box drives the nth turbine. No claim is
 * stored anywhere, and every generator computes the same answer independently, so a plant can be
 * scaled by dropping in another matched pair and nothing has to be told about it.
 */
public final class PlantSurvey {

    /** Biggest plant box the scan will walk before giving up. */
    private static final int MAX_VOLUME = 32768;

    /** What kind of plant this is, decided by what was found inside it. */
    public enum Kind {
        /** Not inside a registered plant at all. */
        NONE,
        /** Boilers and no windmills. */
        COAL,
        /** Windmills and no boilers. */
        WIND,
        /** Both, which is not a plant but two plants somebody forgot to draw separately. */
        MIXED,
        /** Registered, but with no generator in it yet. */
        EMPTY
    }

    private final Kind kind;
    private final List<BlockPos> generators;
    private final List<BlockPos> turbines;
    private final @Nullable BlockPos chimney;

    private PlantSurvey(Kind kind, List<BlockPos> generators, List<BlockPos> turbines,
                        @Nullable BlockPos chimney) {
        this.kind = kind;
        this.generators = generators;
        this.turbines = turbines;
        this.chimney = chimney;
    }

    private static final PlantSurvey NOTHING =
            new PlantSurvey(Kind.NONE, List.of(), List.of(), null);

    /** Survey the plant a block at this position belongs to. */
    public static PlantSurvey at(Level level, BlockPos pos) {
        MinecraftServer server = level.getServer();
        if (server == null) {
            return NOTHING;
        }
        Structure plant = CityData.get(server).structureAt(level.dimension(), pos);
        if (plant == null || plant.type() != StructureType.POWER_PLANT) {
            return NOTHING;
        }
        return of(level, plant.min(), plant.max());
    }

    /** Survey an arbitrary box, for checking a selection before it is registered. */
    public static PlantSurvey of(Level level, BlockPos min, BlockPos max) {
        long volume = (long) (max.getX() - min.getX() + 1)
                * (max.getY() - min.getY() + 1)
                * (max.getZ() - min.getZ() + 1);
        if (volume > MAX_VOLUME) {
            return NOTHING;
        }

        final List<BlockPos> boilers = new ArrayList<>();
        final List<BlockPos> windmills = new ArrayList<>();
        final List<BlockPos> turbines = new ArrayList<>();
        BlockPos chimney = null;

        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = min.getY(); y <= max.getY(); y++) {
            for (int z = min.getZ(); z <= max.getZ(); z++) {
                for (int x = min.getX(); x <= max.getX(); x++) {
                    cursor.set(x, y, z);
                    Block block = level.getBlockState(cursor).getBlock();
                    if (block instanceof TurbineBlock) {
                        turbines.add(cursor.immutable());
                    } else if (block instanceof BoilerBlock) {
                        boilers.add(cursor.immutable());
                    } else if (block instanceof WindmillBlock) {
                        windmills.add(cursor.immutable());
                    } else if (chimney == null && block instanceof ChimneyBlock
                            && ChimneyBlock.isOpen(level, cursor)) {
                        chimney = cursor.immutable();
                    }
                }
            }
        }

        if (!boilers.isEmpty() && !windmills.isEmpty()) {
            return new PlantSurvey(Kind.MIXED, List.of(), List.of(), chimney);
        }

        List<BlockPos> generators = boilers.isEmpty() ? windmills : boilers;
        Kind kind;
        if (generators.isEmpty()) {
            kind = Kind.EMPTY;
        } else {
            kind = boilers.isEmpty() ? Kind.WIND : Kind.COAL;
        }

        generators.sort(Comparator.comparingLong(BlockPos::asLong));
        turbines.sort(Comparator.comparingLong(BlockPos::asLong));
        return new PlantSurvey(kind, generators, turbines, chimney);
    }

    public Kind kind() {
        return kind;
    }

    public boolean registered() {
        return kind != Kind.NONE;
    }

    public @Nullable BlockPos chimney() {
        return chimney;
    }

    public int generatorCount() {
        return generators.size();
    }

    public int turbineCount() {
        return turbines.size();
    }

    /**
     * The turbine belonging to the generator standing at this position.
     *
     * <p>Null when there are more generators than turbines, which is a real state worth reporting
     * rather than papering over — an unpaired boiler burning coal for nothing is the sort of thing a
     * player wants told to their face.
     */
    public @Nullable BlockPos turbineFor(BlockPos generator) {
        int index = generators.indexOf(generator);
        if (index < 0 || index >= turbines.size()) {
            return null;
        }
        return turbines.get(index);
    }
}
