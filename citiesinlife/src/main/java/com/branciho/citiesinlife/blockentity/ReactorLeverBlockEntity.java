package com.branciho.citiesinlife.blockentity;

import com.branciho.citiesinlife.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Where a lever's arm actually is, as opposed to where it has been told to be.
 *
 * <p>The position is a block state, because that is what the world and the reactor read. This holds
 * the two numbers a block state cannot: what the arm swung <em>from</em>, and when. Without them
 * the throw is instantaneous, and a lever that teleports between two poses reads as a texture
 * change rather than as something you pulled.
 */
public class ReactorLeverBlockEntity extends BlockEntity {

    /** How long the arm takes to swing, in ticks. A quarter of a second: firm, not sluggish. */
    public static final int SWING_TICKS = 5;

    private int from;
    private long swungAt = Long.MIN_VALUE;

    public ReactorLeverBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.REACTOR_LEVER.get(), pos, state);
    }

    /** Told by the block the moment the state changes, so the renderer knows where to start. */
    public void beginSwing(int fromPosition, long gameTime) {
        this.from = fromPosition;
        this.swungAt = gameTime;
        setChanged();
    }

    public int from() {
        return from;
    }

    /** 0 at the instant of the throw, 1 once the arm has arrived. */
    public float swingProgress(long gameTime, float partialTick) {
        if (swungAt == Long.MIN_VALUE) {
            return 1.0F;
        }
        float elapsed = (gameTime - swungAt) + partialTick;
        return Mth.clamp(elapsed / SWING_TICKS, 0.0F, 1.0F);
    }

    // The renderer runs on the client, so both numbers have to get there. A block-entity field
    // changed server-side sends nothing on its own.
    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        from = tag.getInt("from");
        swungAt = tag.contains("swungAt") ? tag.getLong("swungAt") : Long.MIN_VALUE;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("from", from);
        if (swungAt != Long.MIN_VALUE) {
            tag.putLong("swungAt", swungAt);
        }
    }
}
