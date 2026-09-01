package com.branciho.citiesinlife.net.payload;

import com.branciho.citiesinlife.CitiesInLife;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Somebody else's land, near enough to matter, so the map can show it.
 *
 * <p>The stance travels with the chunk rather than being looked up per tile: the map paints four
 * hundred squares a frame and has no business resolving a city for each one.
 *
 * @param chunks  packed chunk positions
 * @param stances one {@code Relation} ordinal per chunk, in the same order
 */
public record ForeignLandPayload(long[] chunks, byte[] stances) implements CustomPacketPayload {

    /** A generous radius round the player's map view; beyond it there is nothing to draw. */
    public static final int MAX_CHUNKS = 4096;

    public static final CustomPacketPayload.Type<ForeignLandPayload> TYPE =
            new CustomPacketPayload.Type<>(CitiesInLife.id("foreign_land"));

    public static final StreamCodec<FriendlyByteBuf, ForeignLandPayload> STREAM_CODEC =
            StreamCodec.ofMember(ForeignLandPayload::write, ForeignLandPayload::read);

    private void write(FriendlyByteBuf buf) {
        int count = Math.min(Math.min(chunks.length, stances.length), MAX_CHUNKS);
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            buf.writeLong(chunks[i]);
            buf.writeByte(stances[i]);
        }
    }

    private static ForeignLandPayload read(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_CHUNKS) {
            throw new IllegalArgumentException("Foreign chunk count out of range: " + count);
        }
        long[] chunks = new long[count];
        byte[] stances = new byte[count];
        for (int i = 0; i < count; i++) {
            chunks[i] = buf.readLong();
            stances[i] = buf.readByte();
        }
        return new ForeignLandPayload(chunks, stances);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
