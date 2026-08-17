package com.branciho.citiesinlife.blockentity;

import com.branciho.citiesinlife.block.WindmillBlock;
import com.branciho.citiesinlife.plant.PlantSurvey;
import com.branciho.citiesinlife.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A wind turbine's rotor: how fast it is turning, and what that costs anything standing in it.
 *
 * <p>The rotor is the point of the block. It sweeps a disc fifteen blocks across, and rather than
 * pretending the world is not there it clears it — terrain, trees, somebody's roof — and hits
 * anything alive that strays into the arc. Wind power in this mod is not a quiet block you drop in a
 * field; it is a machine you have to give room to.
 */
public class WindmillBlockEntity extends BlockEntity {

    /** How far the blades reach from the hub, in blocks. Fifteen across, like the real thing. */
    public static final int BLADE_REACH = 7;

    /** Blades start out at this radius, so the hub itself is not a meat grinder. */
    private static final double BLADE_INNER = 1.6D;

    /** Degrees per tick at full speed - slow and heavy, the way a big rotor turns. */
    private static final float SPIN_PER_TICK = 3.2F;

    private static final float SPIN_RAMP = 0.02F;

    /** How often the swept disc is cleared, and how often it looks for something to hit. */
    private static final int CLEAR_INTERVAL = 20;
    private static final int STRIKE_INTERVAL = 10;

    /** How often it re-checks which turbine it drives. */
    private static final int SURVEY_INTERVAL = 100;

    /** What a strike from a blade does. Enough that flying through one is a decision. */
    private static final float STRIKE_DAMAGE = 9.0F;

    private boolean turning;
    private @Nullable BlockPos turbinePos;
    private int ticksRun;

    /** Client-side only: the rotor's angle, and where it was last tick so rendering can interpolate. */
    private float spin;
    private float previousSpin;
    private float speed;

    public WindmillBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WINDMILL.get(), pos, state);
    }

    // ---------------------------------------------------------------- running

    public static void serverTick(Level level, BlockPos pos, BlockState state, WindmillBlockEntity mill) {
        boolean wasTurning = mill.turning;

        if (mill.ticksRun % SURVEY_INTERVAL == 0) {
            PlantSurvey plant = PlantSurvey.at(level, pos);
            mill.turbinePos = plant.kind() == PlantSurvey.Kind.WIND ? plant.turbineFor(pos) : null;
        }

        // Open sky is the whole fuel supply. A windmill in a cave is an ornament.
        mill.turning = level.canSeeSky(pos.above());

        if (mill.turning && mill.ticksRun % CLEAR_INTERVAL == 0) {
            mill.clearSweep(level, pos, state);
        }
        if (mill.turning && mill.ticksRun % STRIKE_INTERVAL == 0) {
            mill.strike(level, pos, state);
        }
        if (mill.turning && mill.turbinePos != null
                && level.getBlockEntity(mill.turbinePos) instanceof TurbineBlockEntity turbine) {
            turbine.accept(1, TurbineBlockEntity.WIND_OUTPUT);
        }

        mill.ticksRun++;
        if (mill.turning != wasTurning) {
            mill.setChanged();
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        }
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, WindmillBlockEntity mill) {
        mill.previousSpin = mill.spin;
        float target = mill.turning ? SPIN_PER_TICK : 0.0F;
        mill.speed += (target - mill.speed) * SPIN_RAMP;
        mill.spin += mill.speed;

        if (mill.spin >= 360.0F) {
            mill.spin -= 360.0F;
            mill.previousSpin -= 360.0F;
        }
    }

    /**
     * Tear out anything standing in the disc the blades sweep.
     *
     * <p>Walked as a flat disc in the rotor's own plane rather than as a sphere, because that is the
     * shape the blades actually occupy and clearing a ball fifteen across would eat a hillside the
     * rotor never touches.
     */
    private void clearSweep(Level level, BlockPos pos, BlockState state) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        Direction facing = state.getValue(WindmillBlock.FACING);
        // The rotor turns in the plane perpendicular to where it points, so the two axes it spans
        // are "up" and whichever horizontal direction is across the nacelle.
        Direction across = facing.getClockWise();
        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int a = -BLADE_REACH; a <= BLADE_REACH; a++) {
            for (int b = -BLADE_REACH; b <= BLADE_REACH; b++) {
                int distanceSqr = a * a + b * b;
                if (distanceSqr > BLADE_REACH * BLADE_REACH || distanceSqr <= 1) {
                    continue;
                }
                cursor.set(pos.getX() + across.getStepX() * a,
                        pos.getY() + b,
                        pos.getZ() + across.getStepZ() * a);
                BlockState hit = level.getBlockState(cursor);
                if (hit.isAir() || hit.getBlock() instanceof WindmillBlock) {
                    continue;
                }
                // Bedrock and the like stop the blades rather than the other way round.
                if (hit.getDestroySpeed(level, cursor) < 0.0F) {
                    continue;
                }
                serverLevel.destroyBlock(cursor, true);
            }
        }
    }

    /** Hit anything alive inside the arc. */
    private void strike(Level level, BlockPos pos, BlockState state) {
        Direction facing = state.getValue(WindmillBlock.FACING);
        Direction across = facing.getClockWise();
        Vec3 hub = Vec3.atCenterOf(pos);

        AABB arc = new AABB(hub, hub).inflate(BLADE_REACH + 1.0D);
        List<LivingEntity> caught = level.getEntitiesOfClass(LivingEntity.class, arc);
        for (LivingEntity entity : caught) {
            Vec3 offset = entity.position().add(0.0D, entity.getBbHeight() * 0.5D, 0.0D).subtract(hub);

            // How far out of the rotor's plane it is, and how far from the hub within that plane.
            double depth = Math.abs(offset.x * facing.getStepX() + offset.z * facing.getStepZ());
            if (depth > 1.5D) {
                continue;
            }
            double planar = Math.hypot(offset.x * across.getStepX() + offset.z * across.getStepZ(), offset.y);
            if (planar < BLADE_INNER || planar > BLADE_REACH) {
                continue;
            }
            entity.hurt(level.damageSources().flyIntoWall(), STRIKE_DAMAGE);
        }
    }

    public boolean turning() {
        return turning;
    }

    /** The rotor's angle at this exact moment, for the renderer. */
    public float spin(float partialTick) {
        return previousSpin + (spin - previousSpin) * partialTick;
    }

    // --------------------------------------------------------------- syncing

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putBoolean("turning", turning);
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // ------------------------------------------------------------ persistence

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        turning = tag.getBoolean("turning");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("turning", turning);
    }
}
