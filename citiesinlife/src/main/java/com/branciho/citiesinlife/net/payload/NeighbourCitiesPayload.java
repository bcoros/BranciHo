package com.branciho.citiesinlife.net.payload;

import com.branciho.citiesinlife.CitiesInLife;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The other cities in this world, for the Neighbours tab.
 *
 * <p>Sent whole rather than by distance. A war is a thing you want to know about whether or not the
 * other city is over the next hill, and on any server small enough for one person to run there are
 * never enough cities for the size of this to matter.
 */
public record NeighbourCitiesPayload(List<Entry> cities) implements CustomPacketPayload {

    /** Well past any server this is for; a bound rather than a design constraint. */
    public static final int MAX_CITIES = 64;

    private static final int MAX_NAME = 64;

    /**
     * One foreign city.
     *
     * @param theirStance how they regard you — this is the one that decides whether you may build
     * @param yourStance  how you regard them, so the buttons can say Revoke rather than Grant
     * @param yourPacts   the pacts you are holding your half of, as a bitmask
     * @param theirPacts  the pacts they are holding theirs of, so a button can say Accept rather
     *                    than Propose without a second round trip to find out
     * @param theirPowerPrice what they charge you per unit per step, so you can read the bill
     *                        before agreeing to it rather than after
     */
    public record Entry(UUID cityId, String name, String ownerName,
                        int theirStance, int yourStance, int chunks, int distance,
                        int yourPacts, int theirPacts,
                        int yourPowerPrice, int yourWaterPrice,
                        int theirPowerPrice, int theirWaterPrice, byte[] flag,
                        boolean youOfferedPeace, boolean theyOfferedPeace, boolean youAttack) {
    }

    public static final CustomPacketPayload.Type<NeighbourCitiesPayload> TYPE =
            new CustomPacketPayload.Type<>(CitiesInLife.id("neighbour_cities"));

    public static final StreamCodec<FriendlyByteBuf, NeighbourCitiesPayload> STREAM_CODEC =
            StreamCodec.ofMember(NeighbourCitiesPayload::write, NeighbourCitiesPayload::read);

    private void write(FriendlyByteBuf buf) {
        int count = Math.min(cities.size(), MAX_CITIES);
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            Entry entry = cities.get(i);
            buf.writeUUID(entry.cityId());
            buf.writeUtf(entry.name(), MAX_NAME);
            buf.writeUtf(entry.ownerName(), MAX_NAME);
            buf.writeVarInt(entry.theirStance());
            buf.writeVarInt(entry.yourStance());
            buf.writeVarInt(entry.chunks());
            buf.writeVarInt(entry.distance());
            buf.writeVarInt(entry.yourPacts());
            buf.writeVarInt(entry.theirPacts());
            buf.writeVarInt(entry.yourPowerPrice());
            buf.writeVarInt(entry.yourWaterPrice());
            buf.writeVarInt(entry.theirPowerPrice());
            buf.writeVarInt(entry.theirWaterPrice());
            for (byte cell : com.branciho.citiesinlife.city.CityFlag.sanitise(entry.flag())) {
                buf.writeByte(cell);
            }
            buf.writeBoolean(entry.youOfferedPeace());
            buf.writeBoolean(entry.theyOfferedPeace());
            buf.writeBoolean(entry.youAttack());
        }
    }

    private static NeighbourCitiesPayload read(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_CITIES) {
            throw new IllegalArgumentException("City count out of range: " + count);
        }
        List<Entry> cities = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Entry entry = new Entry(
                    buf.readUUID(),
                    buf.readUtf(MAX_NAME),
                    buf.readUtf(MAX_NAME),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    readFlag(buf),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean());
            cities.add(entry);
        }
        return new NeighbourCitiesPayload(cities);
    }

    private static byte[] readFlag(FriendlyByteBuf buf) {
        byte[] flag = new byte[com.branciho.citiesinlife.city.CityFlag.CELLS];
        for (int i = 0; i < flag.length; i++) {
            flag[i] = buf.readByte();
        }
        return flag;
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
