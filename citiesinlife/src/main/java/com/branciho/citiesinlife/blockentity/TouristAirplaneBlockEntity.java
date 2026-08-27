package com.branciho.citiesinlife.blockentity;

import com.branciho.citiesinlife.block.AirfieldBlock;
import com.branciho.citiesinlife.city.City;
import com.branciho.citiesinlife.city.CityData;
import com.branciho.citiesinlife.entity.CitizenEntity;
import com.branciho.citiesinlife.entity.TouristEntity;
import com.branciho.citiesinlife.registry.ModBlockEntities;
import com.branciho.citiesinlife.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Where visitors arrive from.
 *
 * <p>No aeroplane and no runway - the player asked for exactly this, and it is the right shape. What
 * makes somewhere feel visited is people who are obviously not from here wandering about looking at
 * things, not a model on a tarmac.
 *
 * <p>Visitors are counted by asking the world how many are actually here rather than by keeping a
 * list of who was sent out. A stored list cannot tell "this tourist has been unloaded" from "this
 * tourist is dead", so it frees the slot either way and quietly spawns a replacement - once per
 * unload, forever.
 */
public class TouristAirplaneBlockEntity extends BlockEntity {

    /** Ten seconds between arrivals. Nobody is in a hurry. */
    private static final int INTERVAL_TICKS = 200;

    /** No point spawning scenery nobody is there to see. */
    private static final int SPAWN_NEAR_PLAYER = 96;

    /** How many visitors one airport keeps on the ground at once. */
    private static final int MAX_TOURISTS = 4;

    /** How far from the block a visitor may be put down, and how far out they are counted. */
    private static final int SPAWN_RADIUS = 6;
    private static final double COUNT_RADIUS = 48.0D;

    public TouristAirplaneBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TOURIST_AIRPLANE.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  TouristAirplaneBlockEntity airplane) {
        if (level.getGameTime() % INTERVAL_TICKS != 0 || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        // An airfield nobody is accountable for does nothing at all. This is where the cap actually
        // bites: placement checks can be walked round with /fill, and this one cannot.
        if (!AirfieldBlock.counted(serverLevel, pos)) {
            return;
        }
        if (serverLevel.getNearestPlayer(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                SPAWN_NEAR_PLAYER, false) == null) {
            return;
        }
        if (visitorCount(serverLevel, pos) >= MAX_TOURISTS) {
            return;
        }

        BlockPos spot = somewhereToArrive(serverLevel, pos);
        if (spot == null) {
            return;
        }
        TouristEntity tourist = ModEntities.TOURIST.get().create(serverLevel);
        if (tourist == null) {
            return;
        }
        tourist.moveTo(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D,
                serverLevel.random.nextFloat() * 360.0F, 0.0F);
        tourist.setSkin(serverLevel.random.nextInt(CitizenEntity.SKINS));
        tourist.setAirfield(pos);

        // Belonging to the city means the border protects them, exactly as it protects its own
        // people. A visitor anybody could shoot would be a strange advert for the place.
        City host = CityData.get(serverLevel.getServer()).cityAtChunk(
                serverLevel.dimension(), ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
        if (host != null) {
            tourist.setCityId(host.id());
        }

        tourist.finalizeSpawn(serverLevel, serverLevel.getCurrentDifficultyAt(spot),
                MobSpawnType.MOB_SUMMONED, null);
        serverLevel.addFreshEntity(tourist);
    }

    /** How many visitors this airport currently has on the ground. */
    private static int visitorCount(ServerLevel level, BlockPos pos) {
        List<? extends TouristEntity> here = level.getEntities(ModEntities.TOURIST.get(),
                tourist -> tourist.isAlive() && pos.equals(tourist.airfield())
                        && tourist.blockPosition().distSqr(pos) <= COUNT_RADIUS * COUNT_RADIUS);
        return here.size();
    }

    /** Somewhere beside the block with room to stand, searched outward. */
    private static @Nullable BlockPos somewhereToArrive(ServerLevel level, BlockPos pos) {
        for (int radius = 1; radius <= SPAWN_RADIUS; radius++) {
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos candidate = pos.relative(direction, radius);
                if (roomToStand(level, candidate)) {
                    return candidate;
                }
                if (roomToStand(level, candidate.above())) {
                    return candidate.above();
                }
            }
        }
        return roomToStand(level, pos.above()) ? pos.above() : null;
    }

    private static boolean roomToStand(ServerLevel level, BlockPos pos) {
        return level.isLoaded(pos)
                && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
                && !level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty();
    }
}
