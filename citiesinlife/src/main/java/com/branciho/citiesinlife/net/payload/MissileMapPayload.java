package com.branciho.citiesinlife.net.payload;

import com.branciho.citiesinlife.CitiesInLife;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Everything the missile map draws.
 *
 * <p>Its own packet rather than a use of what the client already has, because what the client
 * already has is deliberately short-sighted: foreign territory is only ever sent within twelve
 * chunks of the player and it arrives as a bare stance byte with no city attached. That is the
 * right amount for the land map, which is about the ground under your feet. It is useless for a
 * weapon whose entire point is reaching somewhere you are not.
 *
 * <p>So this carries the strategic picture and nothing else: where your silos are and what is
 * standing in them, whose land is whose across a much wider area, where each city actually is so
 * the map can jump to it, and what is currently in the air.
 *
 * <p>Sent only while the screen is open. It is a great deal more data than the ordinary sync and
 * there is no reason for anybody who is not looking at a missile map to be paying for it.
 */
public record MissileMapPayload(List<Silo> silos, List<Land> land, List<Place> places,
                                List<Track> tracks) implements CustomPacketPayload {

    public static final int MAX_SILOS = 32;
    public static final int MAX_LAND = 8192;
    public static final int MAX_PLACES = 64;
    public static final int MAX_TRACKS = 32;

    /** Whose a chunk is, from the viewer's point of view. */
    public static final byte MINE = 0;
    public static final byte NEUTRAL = 1;
    public static final byte ALLIED = 2;
    public static final byte WAR = 3;

    /**
     * One of your silos.
     *
     * <p>Only ever your own. Where somebody else keeps their rockets is exactly the sort of thing
     * you should have to go and look at rather than read off a screen.
     */
    public record Silo(UUID id, String name, int blockX, int blockZ,
                       int ballistic, int nuclear, int interceptors, boolean busy) {
    }

    /** One claimed chunk and whose it is. */
    public record Land(long chunkKey, byte relation) {
    }

    /** A city, and roughly where it is, so the map can be told to go there. */
    public record Place(String name, int chunkX, int chunkZ, byte relation) {
    }

    /** One missile in the air: where from, where to, how long, and whether it is yours. */
    public record Track(int fromX, int fromZ, int toX, int toZ, int seconds, byte kind,
                        boolean mine) {
    }

    public static final CustomPacketPayload.Type<MissileMapPayload> TYPE =
            new CustomPacketPayload.Type<>(CitiesInLife.id("missile_map"));

    public static final StreamCodec<FriendlyByteBuf, MissileMapPayload> STREAM_CODEC =
            StreamCodec.ofMember(MissileMapPayload::write, MissileMapPayload::read);

    private void write(FriendlyByteBuf buf) {
        int siloCount = Math.min(silos.size(), MAX_SILOS);
        buf.writeVarInt(siloCount);
        for (int i = 0; i < siloCount; i++) {
            Silo silo = silos.get(i);
            buf.writeUUID(silo.id());
            buf.writeUtf(silo.name(), 48);
            buf.writeVarInt(silo.blockX());
            buf.writeVarInt(silo.blockZ());
            buf.writeVarInt(silo.ballistic());
            buf.writeVarInt(silo.nuclear());
            buf.writeVarInt(silo.interceptors());
            buf.writeBoolean(silo.busy());
        }

        int landCount = Math.min(land.size(), MAX_LAND);
        buf.writeVarInt(landCount);
        for (int i = 0; i < landCount; i++) {
            buf.writeLong(land.get(i).chunkKey());
            buf.writeByte(land.get(i).relation());
        }

        int placeCount = Math.min(places.size(), MAX_PLACES);
        buf.writeVarInt(placeCount);
        for (int i = 0; i < placeCount; i++) {
            Place place = places.get(i);
            buf.writeUtf(place.name(), 48);
            buf.writeVarInt(place.chunkX());
            buf.writeVarInt(place.chunkZ());
            buf.writeByte(place.relation());
        }

        int trackCount = Math.min(tracks.size(), MAX_TRACKS);
        buf.writeVarInt(trackCount);
        for (int i = 0; i < trackCount; i++) {
            Track track = tracks.get(i);
            buf.writeVarInt(track.fromX());
            buf.writeVarInt(track.fromZ());
            buf.writeVarInt(track.toX());
            buf.writeVarInt(track.toZ());
            buf.writeVarInt(track.seconds());
            buf.writeByte(track.kind());
            buf.writeBoolean(track.mine());
        }
    }

    private static MissileMapPayload read(FriendlyByteBuf buf) {
        int siloCount = range(buf.readVarInt(), MAX_SILOS, "Silo");
        List<Silo> silos = new ArrayList<>(siloCount);
        for (int i = 0; i < siloCount; i++) {
            silos.add(new Silo(buf.readUUID(), buf.readUtf(48),
                    buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean()));
        }

        int landCount = range(buf.readVarInt(), MAX_LAND, "Land");
        List<Land> land = new ArrayList<>(landCount);
        for (int i = 0; i < landCount; i++) {
            land.add(new Land(buf.readLong(), buf.readByte()));
        }

        int placeCount = range(buf.readVarInt(), MAX_PLACES, "Place");
        List<Place> places = new ArrayList<>(placeCount);
        for (int i = 0; i < placeCount; i++) {
            places.add(new Place(buf.readUtf(48), buf.readVarInt(), buf.readVarInt(),
                    buf.readByte()));
        }

        int trackCount = range(buf.readVarInt(), MAX_TRACKS, "Track");
        List<Track> tracks = new ArrayList<>(trackCount);
        for (int i = 0; i < trackCount; i++) {
            tracks.add(new Track(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readByte(), buf.readBoolean()));
        }
        return new MissileMapPayload(silos, land, places, tracks);
    }

    private static int range(int count, int cap, String what) {
        if (count < 0 || count > cap) {
            throw new IllegalArgumentException(what + " count out of range: " + count);
        }
        return count;
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
