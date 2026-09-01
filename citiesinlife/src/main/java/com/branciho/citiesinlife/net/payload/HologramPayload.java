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
 * @param usable  whether the table answered at all — you have a city and you are standing in its
 *                hall. Sent as a field rather than as an empty list, because "nobody is home" and
 *                "this table is not yours" are different things and the panel says so differently.
 * @param seen    who is on your ground, nearest first
 * @param claimed every chunk this city owns, packed as {@link net.minecraft.world.level.ChunkPos}
 *                keys. The map is drawn from this: a list of names with coordinates beside them is
 *                a table, and what the block is called and looks like promised a map. Sent with the
 *                sightings rather than read from the city sync because the two have to agree — a
 *                dot drawn against last tick's territory lands in the wrong square.
 */
public record HologramPayload(boolean usable, List<Sighting> seen, long[] claimed)
        implements CustomPacketPayload {

    /** One person, where they are, and how far into your territory. */
    public record Sighting(String name, int x, int y, int z, boolean own) {
    }

    public static final int MAX_NAME = 64;

    /** More than this on one city's ground and the list has stopped being readable anyway. */
    public static final int MAX_SEEN = 64;

    /**
     * Biggest territory the map will draw.
     *
     * <p>A cap rather than a limit anybody will meet: a city of four thousand chunks is a thousand
     * kilometres square and the map would be one pixel per chunk anyway. It exists so a hostile
     * packet cannot ask the client to allocate an arbitrary array.
     */
    public static final int MAX_CLAIMED = 4096;

    public static final CustomPacketPayload.Type<HologramPayload> TYPE =
            new CustomPacketPayload.Type<>(CitiesInLife.id("hologram"));

    public static final StreamCodec<FriendlyByteBuf, HologramPayload> STREAM_CODEC =
            StreamCodec.ofMember(HologramPayload::write, HologramPayload::read);

    /** What the table shows before the first packet lands, and when it is not yours to read. */
    public static HologramPayload none() {
        return new HologramPayload(false, List.of(), new long[0]);
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
        int ground = Math.min(claimed.length, MAX_CLAIMED);
        buf.writeVarInt(ground);
        for (int i = 0; i < ground; i++) {
            buf.writeLong(claimed[i]);
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
        int ground = buf.readVarInt();
        if (ground < 0 || ground > MAX_CLAIMED) {
            throw new IllegalArgumentException("Bad hologram territory size: " + ground);
        }
        long[] claimed = new long[ground];
        for (int i = 0; i < ground; i++) {
            claimed[i] = buf.readLong();
        }
        return new HologramPayload(usable, seen, claimed);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
