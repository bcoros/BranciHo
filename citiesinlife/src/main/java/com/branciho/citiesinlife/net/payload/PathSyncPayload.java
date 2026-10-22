package com.branciho.citiesinlife.net.payload;

import com.branciho.citiesinlife.CitiesInLife;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** The marked pavement near the player, so the overlay can draw it. */
public record PathSyncPayload(long[] marked) implements CustomPacketPayload {

    public static final int MAX_MARKED = 4096;

    public static final CustomPacketPayload.Type<PathSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(CitiesInLife.id("path_sync"));

    public static final StreamCodec<FriendlyByteBuf, PathSyncPayload> STREAM_CODEC =
            StreamCodec.ofMember(PathSyncPayload::write, PathSyncPayload::read);

    private void write(FriendlyByteBuf buf) {
        int count = Math.min(marked.length, MAX_MARKED);
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            buf.writeLong(marked[i]);
        }
    }

    private static PathSyncPayload read(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_MARKED) {
            throw new IllegalArgumentException("Path count out of range: " + count);
        }
        long[] marked = new long[count];
        for (int i = 0; i < count; i++) {
            marked[i] = buf.readLong();
        }
        return new PathSyncPayload(marked);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
