package com.branciho.citiesinlife.net.payload;

import com.branciho.citiesinlife.CitiesInLife;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The road near the player, so the overlay can draw it and show which way it runs.
 *
 * <p>Two parallel arrays rather than a list of pairs, matching {@code ForeignLandPayload}. Capped at
 * half what pavement is capped at: this goes out alongside the pavement sync, and doubling the
 * largest recurring packet in the mod for scenery would undo the reason that cap exists.
 */
public record RoadSyncPayload(long[] tiles, int[] flags) implements CustomPacketPayload {

    public static final int MAX_TILES = 2048;

    public static final CustomPacketPayload.Type<RoadSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(CitiesInLife.id("road_sync"));

    public static final StreamCodec<FriendlyByteBuf, RoadSyncPayload> STREAM_CODEC =
            StreamCodec.ofMember(RoadSyncPayload::write, RoadSyncPayload::read);

    public static RoadSyncPayload none() {
        return new RoadSyncPayload(new long[0], new int[0]);
    }

    private void write(FriendlyByteBuf buf) {
        int count = Math.min(Math.min(tiles.length, flags.length), MAX_TILES);
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            buf.writeLong(tiles[i]);
            buf.writeVarInt(flags[i]);
        }
    }

    private static RoadSyncPayload read(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_TILES) {
            throw new IllegalArgumentException("Road count out of range: " + count);
        }
        long[] tiles = new long[count];
        int[] flags = new int[count];
        for (int i = 0; i < count; i++) {
            tiles[i] = buf.readLong();
            flags[i] = buf.readVarInt();
        }
        return new RoadSyncPayload(tiles, flags);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
