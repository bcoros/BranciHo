package com.branciho.citiesinlife.net.payload;

import com.branciho.citiesinlife.CitiesInLife;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * "Plumb that tap into that chest."
 *
 * <p>Which of the two is the tap is the server's business, not the client's — the gesture is the
 * same either way round and there is no reason to make the player remember an order. Naming the same
 * tap twice unplumbs it, so there is no separate cutting gesture to learn either.
 */
public record LinkOutletPayload(BlockPos from, BlockPos to) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<LinkOutletPayload> TYPE =
            new CustomPacketPayload.Type<>(CitiesInLife.id("link_outlet"));

    public static final StreamCodec<FriendlyByteBuf, LinkOutletPayload> STREAM_CODEC =
            StreamCodec.ofMember(LinkOutletPayload::write, LinkOutletPayload::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(from);
        buf.writeBlockPos(to);
    }

    private static LinkOutletPayload read(FriendlyByteBuf buf) {
        return new LinkOutletPayload(buf.readBlockPos(), buf.readBlockPos());
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
