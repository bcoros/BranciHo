package com.branciho.citiesinlife.net.payload;

import com.branciho.citiesinlife.CitiesInLife;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Claim or release one chunk, sent when a tile on the land map is clicked. */
public record ClaimChunkPayload(int chunkX, int chunkZ, boolean claim) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ClaimChunkPayload> TYPE =
            new CustomPacketPayload.Type<>(CitiesInLife.id("claim_chunk"));

    public static final StreamCodec<FriendlyByteBuf, ClaimChunkPayload> STREAM_CODEC =
            StreamCodec.ofMember(ClaimChunkPayload::write, ClaimChunkPayload::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(chunkX);
        buf.writeVarInt(chunkZ);
        buf.writeBoolean(claim);
    }

    private static ClaimChunkPayload read(FriendlyByteBuf buf) {
        return new ClaimChunkPayload(buf.readVarInt(), buf.readVarInt(), buf.readBoolean());
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
