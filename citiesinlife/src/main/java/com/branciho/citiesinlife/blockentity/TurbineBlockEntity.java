package com.branciho.citiesinlife.blockentity;

import com.branciho.citiesinlife.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The turbine's insides: a buffer of whatever is driving it, and how fast the rotor is turning.
 *
 * <p>The turbine does not care what drives it. A boiler pushes steam and says the run is worth a
 * hundred and fifty; a windmill pushes wind and says fifty. That is the whole difference between a
 * coal plant and a wind farm from this block's point of view, and it is why the same turbine serves
 * both — and will serve a reactor later without changing anything here.
 *
 * <p>The buffer exists so the rotor does not stutter. A driver hands over a tick at a time and pauses
 * whenever it has to; without somewhere to hold a few seconds' worth the turbine would visibly hiccup
 * every time and read as broken rather than as a machine idling.
 */
public class TurbineBlockEntity extends BlockEntity {

    /** How much drive it holds, and how much it burns through per tick while running. */
    public static final int CHARGE_CAPACITY = 400;
    private static final int CHARGE_PER_TICK = 1;

    /** What a turbine on a coal boiler is worth, and what one on a windmill is worth. */
    public static final int COAL_OUTPUT = 150;
    public static final int WIND_OUTPUT = 50;

    /**
     * How much soot a turbine takes before it seizes up.
     *
     * <p>A boiler with nowhere to put its emissions pushes them through the turbine instead. Two
     * thousand is a bit over three minutes of running with no chimney - long enough to be a
     * consequence you can see coming and short enough that you only make the mistake once.
     */
    public static final int SOOT_LIMIT = 2000;

    /** Degrees the rotor advances per tick at full drive - about two thirds of a turn a second. */
    private static final float SPIN_PER_TICK = 12.0F;

    /** How quickly the rotor spins up and coasts down, so it never snaps between still and fast. */
    private static final float SPIN_RAMP = 0.06F;

    private int charge;
    private int rating;
    private int soot;
    private boolean running;
    private boolean clogged;

    /** Client-side only: the rotor's angle, and where it was last tick so rendering can interpolate. */
    private float spin;
    private float previousSpin;
    private float speed;

    public TurbineBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TURBINE.get(), pos, state);
    }

    // ---------------------------------------------------------------- running

    public static void serverTick(Level level, BlockPos pos, BlockState state, TurbineBlockEntity turbine) {
        boolean wasRunning = turbine.running;

        if (turbine.clogged) {
            // Seized. It keeps whatever is in the buffer, so clearing the soot brings it straight
            // back rather than making the player wait for the boiler to fill it again.
            turbine.running = false;
            if (level.getGameTime() % 20L == 0L && level instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE,
                        pos.getX() + 0.5D, pos.getY() + 1.4D, pos.getZ() + 0.5D,
                        6, 0.4D, 0.2D, 0.4D, 0.01D);
            }
        } else if (turbine.charge >= CHARGE_PER_TICK) {
            turbine.charge -= CHARGE_PER_TICK;
            turbine.running = true;
        } else {
            turbine.running = false;
        }

        if (turbine.running != wasRunning) {
            // The rotor's animation lives on the client, so the only thing worth sending is whether
            // it is turning and whether it is fouled.
            turbine.setChanged();
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        }
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, TurbineBlockEntity turbine) {
        turbine.previousSpin = turbine.spin;
        float target = turbine.running ? SPIN_PER_TICK : 0.0F;
        turbine.speed += (target - turbine.speed) * SPIN_RAMP;
        turbine.spin += turbine.speed;

        // Keep the angle small rather than letting it climb until float precision makes it judder.
        if (turbine.spin >= 360.0F) {
            turbine.spin -= 360.0F;
            turbine.previousSpin -= 360.0F;
        }
    }

    /**
     * Take drive from whatever is feeding this turbine.
     *
     * @param amount how much to add to the buffer
     * @param worth  what a turbine driven this way produces, so coal and wind can differ
     * @return how much was actually taken, so the driver knows what it still has to get rid of
     */
    public int accept(int amount, int worth) {
        if (clogged) {
            return 0;
        }
        int taken = Math.min(amount, CHARGE_CAPACITY - charge);
        if (taken > 0) {
            charge += taken;
            rating = worth;
            setChanged();
        }
        return taken;
    }

    /**
     * Push emissions through the turbine because there was nowhere else for them to go.
     *
     * <p>This is what a missing chimney costs. It is not instant and it is not silent — the turbine
     * smokes as it fouls — but left alone it will stop the plant dead until somebody takes a wrench
     * to it.
     */
    public void foul(int amount) {
        if (clogged || amount <= 0) {
            return;
        }
        soot += amount;
        if (soot >= SOOT_LIMIT) {
            soot = SOOT_LIMIT;
            clogged = true;
            running = false;
            if (level != null) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        setChanged();
    }

    /** Clean it out. Returns false if there was nothing to clean, so the tool can say so. */
    public boolean repair() {
        if (soot == 0 && !clogged) {
            return false;
        }
        soot = 0;
        clogged = false;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
        return true;
    }

    public int output() {
        return running ? rating : 0;
    }

    public boolean running() {
        return running;
    }

    public boolean clogged() {
        return clogged;
    }

    public int soot() {
        return soot;
    }

    /** The rotor's angle at this exact moment, for the renderer. */
    public float spin(float partialTick) {
        return previousSpin + (spin - previousSpin) * partialTick;
    }

    // --------------------------------------------------------------- syncing

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putBoolean("running", running);
        tag.putBoolean("clogged", clogged);
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
        charge = tag.getInt("charge");
        rating = tag.getInt("rating");
        soot = tag.getInt("soot");
        running = tag.getBoolean("running");
        clogged = tag.getBoolean("clogged");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("charge", charge);
        tag.putInt("rating", rating);
        tag.putInt("soot", soot);
        tag.putBoolean("running", running);
        tag.putBoolean("clogged", clogged);
    }
}
