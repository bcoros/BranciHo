package com.branciho.citiesinlife.net.payload;

import com.branciho.citiesinlife.CitiesInLife;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * The power lines near the player, so the client can draw the wires.
 *
 * <p>Lines are pure server state — there is no block in the air between two masts — so without this
 * the player would connect two things and see nothing happen.
 */
public record PowerLinesPayload(List<long[]> lines) implements CustomPacketPayload {

    public static final int MAX_LINES = 512;

    public static final CustomPacketPayload.Type<PowerLinesPayload> TYPE =
            new CustomPacketPayload.Type<>(CitiesInLife.id("power_lines"));

    public static final StreamCodec<FriendlyByteBuf, PowerLinesPayload> STREAM_CODEC =
            StreamCodec.ofMember(PowerLinesPayload::write, PowerLinesPayload::read);

    private void write(FriendlyByteBuf buf) {
        int count = Math.min(lines.size(), MAX_LINES);
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            long[] line = lines.get(i);
            buf.writeLong(line[0]);
            buf.writeLong(line[1]);
        }
    }

    private static PowerLinesPayload read(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_LINES) {
            throw new IllegalArgumentException("Power line count out of range: " + count);
        }
        List<long[]> lines = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            lines.add(new long[]{buf.readLong(), buf.readLong()});
        }
        return new PowerLinesPayload(lines);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
