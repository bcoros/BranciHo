package com.branciho.citiesinlife.blockentity;

import com.branciho.citiesinlife.registry.ModBlockEntities;
import net.minecraft.util.Mth;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
    public static final int MAX_TIER = 1;

    /** Half as much again out of the same steam, which is what the one upgrade buys. */
    public static final float TIER_BONUS = 0.5F;

    public static final int COAL_OUTPUT = 150;
    public static final int WIND_OUTPUT = 50;

    /**
     * How much soot a turbine takes before it seizes up.
     *
     * <p>A boiler with nowhere to put its emissions pushes them through the turbine instead. Soot
     * goes in at one a tick while the fire is actually lit, so two thousand is a hundred seconds of
     * burning with no chimney - long enough to be a consequence you can see coming and short enough
     * that you only make the mistake once.
     */
    public static final int SOOT_LIMIT = 2000;

    /**
     * How long a seized turbine smoulders before it catches, and how long it burns before it goes.
     *
     * <p>Two minutes to catch and three more to explode. A clogged turbine is already a plant that
     * has stopped earning, so the fire is not the punishment - the punishment is losing the machine,
     * and five minutes is long enough that only ignoring it entirely costs you that.
     */
    private static final int TICKS_TO_IGNITE = 2400;
    private static final int TICKS_TO_EXPLODE = 3600;

    /** How hard it goes. Enough to take the turbine and make a mess, not to crater the plant. */
    private static final float BLAST = 3.2F;

    /** Degrees the rotor advances per tick at full drive - about two thirds of a turn a second. */
    private static final float SPIN_PER_TICK = 12.0F;

    /** How quickly the rotor spins up and coasts down, so it never snaps between still and fast. */
    private static final float SPIN_RAMP = 0.06F;

    private int charge;
    private int rating;

    /**
     * How hard whatever drives this turbine is asking it to run, 0..100.
     *
     * <p>The coal plant has no dial, so its turbines sit at full and always have. A reactor does,
     * and the rotors have to answer to it: at fifty per cent the machine should visibly be running
     * at half speed rather than looking identical to one at full power with a smaller number on a
     * monitor somewhere.
     */
    private int throttle = 100;

    /**
     * How well built this particular turbine is. One upgrade, so 0 or 1.
     *
     * <p>Applied as a multiplier on whatever is driving it rather than as a flat bonus, so the
     * upgrade is worth having on a coal plant and worth having on a wind farm, in proportion to
     * what each of those was worth in the first place.
     */
    private int tier;
    private int soot;
    private boolean running;
    private boolean clogged;

    /** How long it has been seized, and whether that has turned into an actual fire. */
    private int cloggedTicks;
    private boolean burning;

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
            turbine.cloggedTicks++;

            if (!turbine.burning && turbine.cloggedTicks >= TICKS_TO_IGNITE) {
                turbine.burning = true;
                turbine.cloggedTicks = 0;
                level.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 1.2F, 0.7F);
                turbine.setChanged();
                level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
            } else if (turbine.burning && turbine.cloggedTicks >= TICKS_TO_EXPLODE) {
                // Nobody came. The machine is gone.
                level.removeBlock(pos, false);
                level.explode(null, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                        BLAST, Level.ExplosionInteraction.BLOCK);
                return;
            }

            if (level.getGameTime() % 20L == 0L) {
                // Nothing else dirties this chunk while a turbine smoulders - the boiler has already
                // stopped burning by then. Without this the countdown is never written, and an
                // autosave or an unload rewinds the fuse to whatever it was before it clogged.
                turbine.setChanged();

                if (level instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(
                            turbine.burning ? ParticleTypes.FLAME : ParticleTypes.LARGE_SMOKE,
                            pos.getX() + 0.5D, pos.getY() + 1.4D, pos.getZ() + 0.5D,
                            turbine.burning ? 12 : 6, 0.4D, 0.2D, 0.4D,
                            turbine.burning ? 0.03D : 0.01D);
                }
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
        // Scaled by the throttle, so a reactor at half power turns its rotors at half speed.
        float target = turbine.running ? SPIN_PER_TICK * (turbine.throttle / 100.0F) : 0.0F;
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
    public int throttle() {
        return throttle;
    }

    /**
     * Set by whatever is driving it, once per simulation step.
     *
     * <p>Only sent to the clients when it actually changes, and only in steps of five, because a
     * value that jittered by one would be a block update every ten seconds on every turbine in the
     * world to move a rotor imperceptibly.
     */
    public void setThrottle(int percent) {
        int next = Mth.clamp(percent, 0, 100) / 5 * 5;
        if (next == throttle) {
            return;
        }
        throttle = next;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(),
                    Block.UPDATE_CLIENTS);
        }
    }

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

    /** Whether it is actually on fire, which the wrench cannot help with. */
    public boolean burning() {
        return burning;
    }

    /** Put the fire out. The soot is still in there; that is the wrench's job. */
    public boolean douse() {
        if (!burning) {
            return false;
        }
        burning = false;
        cloggedTicks = 0;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
        return true;
    }

    /** Clean it out. Returns false if there was nothing to clean, so the tool can say so. */
    public boolean repair() {
        if (soot == 0 && !clogged) {
            return false;
        }
        soot = 0;
        clogged = false;
        cloggedTicks = 0;
        burning = false;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
        return true;
    }

    public int output() {
        return running ? Math.round(rating * outputMultiplier()) : 0;
    }

    public int tier() {
        return tier;
    }

    /** What a turbine at this tier gets out of the same steam. */
    public float outputMultiplier() {
        return 1.0F + tier * TIER_BONUS;
    }

    /** Raise the tier by one. Returns false at the ceiling. */
    public boolean upgrade() {
        if (tier >= MAX_TIER) {
            return false;
        }
        tier++;
        setChanged();
        return true;
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
        tag.putBoolean("burning", burning);
        tag.putInt("throttle", throttle);
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
        // Turbines saved before the rework stored "steam" and had no rating at all. Reading a
        // missing rating as zero left them spinning and producing nothing, which looks exactly like
        // a broken turbine and is not the player's fault.
        charge = tag.contains("charge") ? tag.getInt("charge") : tag.getInt("steam");
        rating = tag.contains("rating") ? tag.getInt("rating") : COAL_OUTPUT;
        // Absent on a turbine saved before the dial existed, and a coal turbine has no dial:
        // full is both the old behaviour and the right answer for one.
        throttle = tag.contains("throttle") ? Mth.clamp(tag.getInt("throttle"), 0, 100) : 100;
        tier = Mth.clamp(tag.getInt("tier"), 0, MAX_TIER);
        soot = tag.getInt("soot");
        running = tag.getBoolean("running");
        clogged = tag.getBoolean("clogged");
        burning = tag.getBoolean("burning");
        cloggedTicks = tag.getInt("cloggedTicks");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("charge", charge);
        tag.putInt("rating", rating);
        tag.putInt("throttle", throttle);
        tag.putInt("tier", tier);
        tag.putInt("soot", soot);
        tag.putBoolean("running", running);
        tag.putBoolean("clogged", clogged);
        tag.putBoolean("burning", burning);
        tag.putInt("cloggedTicks", cloggedTicks);
    }
}
