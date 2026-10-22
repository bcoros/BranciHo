package com.branciho.citiesinlife.blockentity;

import com.branciho.citiesinlife.block.AirfieldBlock;
import com.branciho.citiesinlife.block.TransportAirplaneBlock;
import com.branciho.citiesinlife.entity.CitizenEntity;
import com.branciho.citiesinlife.entity.ai.Shifts;
import com.branciho.citiesinlife.registry.ModBlockEntities;
import com.branciho.citiesinlife.road.Commute;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * One end of a flight, which is to say one end of a teleport.
 *
 * <p>A pair of these is a shortcut across the map for a player who right-clicks one, and for any
 * citizen standing on it whose day would be better on the other side.
 *
 * <p>"Would be better" is the whole of the routing, and the asymmetry in it is what stops two pads
 * bouncing somebody back and forth forever: a citizen only hops when the far end is meaningfully
 * closer to where they are trying to get to, so after the hop the same test fails at the other end.
 * The place they are trying to get to depends on what they are doing now - bed in the evening, work
 * in the morning - and not on which field happens to be filled in, or a citizen with a job could
 * fly to work and never fly home.
 */
public class TransportAirplaneBlockEntity extends BlockEntity {

    /** Two seconds between departures. */
    private static final int INTERVAL_TICKS = 40;

    /** How near the pad a citizen has to be to be swept up by it. */
    private static final double BOARDING_RANGE = 1.5D;

    /**
     * How much closer the far end must be before the trip is worth taking, squared.
     *
     * <p>Sixteen blocks. Small enough that a real journey qualifies, large enough that two pads a
     * few blocks apart never ping-pong anybody between them.
     */
    private static final double MARGIN_SQR = 256.0D;

    private @Nullable BlockPos partner;

    public TransportAirplaneBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TRANSPORT_AIRPLANE.get(), pos, state);
    }

    public @Nullable BlockPos partner() {
        return partner;
    }

    public void setPartner(@Nullable BlockPos partner) {
        this.partner = partner;
        setChanged();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  TransportAirplaneBlockEntity airplane) {
        if (level.getGameTime() % INTERVAL_TICKS != 0 || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (!AirfieldBlock.counted(serverLevel, pos)) {
            return;
        }
        BlockPos far = airplane.partner;
        if (far == null) {
            return;
        }
        // A link to a block that has been mined must not be a permanent silent failure.
        if (serverLevel.isLoaded(far)
                && !(serverLevel.getBlockState(far).getBlock() instanceof TransportAirplaneBlock)) {
            airplane.setPartner(null);
            return;
        }
        if (!serverLevel.isLoaded(far)) {
            return;
        }

        List<CitizenEntity> waiting = serverLevel.getEntitiesOfClass(
                CitizenEntity.class, new AABB(pos).inflate(BOARDING_RANGE));
        for (CitizenEntity citizen : waiting) {
            // Never pull somebody out of a moving car.
            if (Commute.driving(citizen)) {
                continue;
            }
            BlockPos target = destinationOf(serverLevel, citizen);
            if (target == null) {
                continue;
            }
            if (target.distSqr(far) + MARGIN_SQR >= target.distSqr(pos)) {
                continue;
            }
            BlockPos arrival = somewhereToLand(serverLevel, far);
            if (arrival == null) {
                // Nowhere to put them down. Dropping somebody into rock would normally kill them
                // free, but this mod cancels damage to a city's own people, so they would simply be
                // entombed there.
                continue;
            }
            citizen.teleportTo(arrival.getX() + 0.5D, arrival.getY(), arrival.getZ() + 0.5D);
            citizen.getNavigation().stop();
        }
    }

    /** Where this citizen is actually trying to get to right now. */
    private static @Nullable BlockPos destinationOf(ServerLevel level, CitizenEntity citizen) {
        boolean headingHome = Shifts.sleepingHours(level) && !citizen.nightShift();
        BlockPos first = headingHome ? citizen.home() : citizen.workstation();
        if (first != null) {
            return first;
        }
        return headingHome ? citizen.workstation() : citizen.home();
    }

    private static @Nullable BlockPos somewhereToLand(ServerLevel level, BlockPos pad) {
        if (roomToStand(level, pad.above())) {
            return pad.above();
        }
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos candidate = pad.relative(direction);
            if (roomToStand(level, candidate)) {
                return candidate;
            }
            if (roomToStand(level, candidate.above())) {
                return candidate.above();
            }
        }
        return null;
    }

    private static boolean roomToStand(ServerLevel level, BlockPos pos) {
        return level.isLoaded(pos)
                && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
                && !level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty();
    }

    /** Where a player arriving here should be put. Public because the block does the teleporting. */
    public static @Nullable BlockPos landingSpot(ServerLevel level, BlockPos pad) {
        return somewhereToLand(level, pad);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        partner = tag.contains("partner") ? BlockPos.of(tag.getLong("partner")) : null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (partner != null) {
            tag.putLong("partner", partner.asLong());
        }
    }
}
