package com.branciho.citiesinlife.net.payload;

import com.branciho.citiesinlife.CitiesInLife;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * Everyone the hologram map can see.
 *
 * <p>Only players standing on ground this city has claimed, and the filtering happens on the
 * server. That is not a nicety: a payload carrying every player's position, filtered on the client,
 * would be a mod-wide wallhack that any modified client could simply not filter. What is not sent
 * cannot be read.
 *
 * @param usable whether the table answered at all — you have a city and you are standing in its
 *               hall. Sent as a field rather than as an empty list, because "nobody is home" and
 *               "this table is not yours" are different things and the panel says so differently.
 * @param seen   who is on your ground, nearest first
 */
public record HologramPayload(boolean usable, List<Sighting> seen) implements CustomPacketPayload {

    /** One person, where they are, and how far into your territory. */
    public record Sighting(String name, int x, int y, int z, boolean own) {
    }

    public static final int MAX_NAME = 64;

    /** More than this on one city's ground and the list has stopped being readable anyway. */
    public static final int MAX_SEEN = 64;

    public static final CustomPacketPayload.Type<HologramPayload> TYPE =
            new CustomPacketPayload.Type<>(CitiesInLife.id("hologram"));

    public static final StreamCodec<FriendlyByteBuf, HologramPayload> STREAM_CODEC =
            StreamCodec.ofMember(HologramPayload::write, HologramPayload::read);

    /** What the table shows before the first packet lands, and when it is not yours to read. */
    public static HologramPayload none() {
        return new HologramPayload(false, List.of());
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeBoolean(usable);
        int count = Math.min(seen.size(), MAX_SEEN);
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            Sighting sighting = seen.get(i);
            buf.writeUtf(sighting.name(), MAX_NAME);
            buf.writeVarInt(sighting.x());
            buf.writeVarInt(sighting.y());
            buf.writeVarInt(sighting.z());
            buf.writeBoolean(sighting.own());
        }
    }

    private static HologramPayload read(FriendlyByteBuf buf) {
        boolean usable = buf.readBoolean();
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_SEEN) {
            throw new IllegalArgumentException("Bad hologram sighting count: " + count);
        }
        List<Sighting> seen = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            seen.add(new Sighting(buf.readUtf(MAX_NAME), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readBoolean()));
        }
        return new HologramPayload(usable, seen);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
