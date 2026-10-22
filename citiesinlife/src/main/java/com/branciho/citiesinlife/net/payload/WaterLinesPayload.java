package com.branciho.citiesinlife.net.payload;

import com.branciho.citiesinlife.CitiesInLife;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * The pipe links near the player, so the client can draw them while the tool is held.
 *
 * <p>They are invisible in play, which is the point of them, but a link you cannot see is also a
 * link you cannot check or cut. Holding the tool reveals them and nothing else does.
 */
public record WaterLinesPayload(List<long[]> lines) implements CustomPacketPayload {

    public static final int MAX_LINES = 512;

    public static final CustomPacketPayload.Type<WaterLinesPayload> TYPE =
            new CustomPacketPayload.Type<>(CitiesInLife.id("water_lines"));

    public static final StreamCodec<FriendlyByteBuf, WaterLinesPayload> STREAM_CODEC =
            StreamCodec.ofMember(WaterLinesPayload::write, WaterLinesPayload::read);

    private void write(FriendlyByteBuf buf) {
        int count = Math.min(lines.size(), MAX_LINES);
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            long[] line = lines.get(i);
            buf.writeLong(line[0]);
            buf.writeLong(line[1]);
        }
    }

    private static WaterLinesPayload read(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_LINES) {
            throw new IllegalArgumentException("Water line count out of range: " + count);
        }
        List<long[]> lines = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            lines.add(new long[]{buf.readLong(), buf.readLong()});
        }
        return new WaterLinesPayload(lines);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
