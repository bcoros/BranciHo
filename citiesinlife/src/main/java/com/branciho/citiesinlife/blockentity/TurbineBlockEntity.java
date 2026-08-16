package com.branciho.citiesinlife.blockentity;

import com.branciho.citiesinlife.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The turbine's insides: a small buffer of steam, and how fast the rotor is turning.
 *
 * <p>The buffer exists so the rotor does not stutter. A boiler hands over steam a tick at a time and
 * pauses whenever its bucket is condensing; without somewhere to hold a few seconds' worth, the
 * turbine would visibly hiccup every time and read as broken rather than as a machine idling.
 */
public class TurbineBlockEntity extends BlockEntity {

    /** How much steam it holds, and how much it burns through per tick while running. */
    public static final int STEAM_CAPACITY = 400;
    private static final int STEAM_PER_TICK = 1;

    /**
     * Units of power a running turbine puts on the network.
     *
     * <p>Three times a solar panel at noon. It has to be worth the room, the coal and the walls, or
     * nobody would build one twice.
     */
    public static final int OUTPUT = 24;

    /** Degrees the rotor advances per tick at full steam - about two thirds of a turn a second. */
    private static final float SPIN_PER_TICK = 12.0F;

    /** How quickly the rotor spins up and coasts down, so it never snaps between still and fast. */
    private static final float SPIN_RAMP = 0.06F;

    private int steam;
    private boolean running;

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
        if (turbine.steam >= STEAM_PER_TICK) {
            turbine.steam -= STEAM_PER_TICK;
            turbine.running = true;
        } else {
            turbine.running = false;
        }
        if (turbine.running != wasRunning) {
            // The rotor's animation lives on the client, so the only thing worth sending is whether
            // there is steam going through it.
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
     * Take steam from a boiler.
     *
     * @return how much was actually taken, so the boiler knows what it still has to get rid of
     */
    public int acceptSteam(int amount) {
        int taken = Math.min(amount, STEAM_CAPACITY - steam);
        if (taken > 0) {
            steam += taken;
            setChanged();
        }
        return taken;
    }

    public int output() {
        return running ? OUTPUT : 0;
    }

    public boolean running() {
        return running;
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
        steam = tag.getInt("steam");
        running = tag.getBoolean("running");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("steam", steam);
        tag.putBoolean("running", running);
    }
}
