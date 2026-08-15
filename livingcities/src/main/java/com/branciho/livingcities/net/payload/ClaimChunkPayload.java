package com.branciho.livingcities.net.payload;

import com.branciho.livingcities.LivingCities;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.level.ChunkPos;

/** Client asks the server to claim or release a chunk for a city. */
public record ClaimChunkPayload(int chunkX, int chunkZ, boolean claim) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ClaimChunkPayload> TYPE =
            new CustomPacketPayload.Type<>(LivingCities.id("claim_chunk"));

    public static final StreamCodec<FriendlyByteBuf, ClaimChunkPayload> STREAM_CODEC =
            StreamCodec.ofMember(ClaimChunkPayload::write, ClaimChunkPayload::read);

    public ChunkPos chunkPos() {
        return new ChunkPos(chunkX, chunkZ);
    }

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
