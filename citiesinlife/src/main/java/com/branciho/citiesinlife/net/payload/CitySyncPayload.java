package com.branciho.citiesinlife.net.payload;

import com.branciho.citiesinlife.CitiesInLife;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The player's city as the screens need to show it.
 *
 * <p>Claimed chunks come across as a flat array because the land map draws a grid of them and needs
 * the whole set; it is capped so a sprawling empire cannot turn one screen open into a huge packet.
 */
public record CitySyncPayload(
        boolean hasCity,
        String cityName,
        long treasury,
        int housing,
        int population,
        int jobs,
        int employed,
        int powerProduced,
        int powerNeeded,
        int waterSupplied,
        int waterNeeded,
        long nextClaimCost,
        boolean creativeFunded,
        long[] claimedChunks
) implements CustomPacketPayload {

    public static final int MAX_CHUNKS = 4096;

    /** What a player with no city yet receives, so the screens have something well-formed to draw. */
    public static CitySyncPayload none() {
        return new CitySyncPayload(false, "", 0L, 0, 0, 0, 0, 0, 0, 0, 0, 0L, false, new long[0]);
    }

    public static final CustomPacketPayload.Type<CitySyncPayload> TYPE =
            new CustomPacketPayload.Type<>(CitiesInLife.id("city_sync"));

    public static final StreamCodec<FriendlyByteBuf, CitySyncPayload> STREAM_CODEC =
            StreamCodec.ofMember(CitySyncPayload::write, CitySyncPayload::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeBoolean(hasCity);
        buf.writeUtf(cityName, 32);
        buf.writeLong(treasury);
        buf.writeVarInt(housing);
        buf.writeVarInt(population);
        buf.writeVarInt(jobs);
        buf.writeVarInt(employed);
        buf.writeVarInt(powerProduced);
        buf.writeVarInt(powerNeeded);
        buf.writeVarInt(waterSupplied);
        buf.writeVarInt(waterNeeded);
        buf.writeLong(nextClaimCost);
        buf.writeBoolean(creativeFunded);

        int count = Math.min(claimedChunks.length, MAX_CHUNKS);
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            buf.writeLong(claimedChunks[i]);
        }
    }

    private static CitySyncPayload read(FriendlyByteBuf buf) {
        boolean hasCity = buf.readBoolean();
        String name = buf.readUtf(32);
        long treasury = buf.readLong();
        int housing = buf.readVarInt();
        int population = buf.readVarInt();
        int jobs = buf.readVarInt();
        int employed = buf.readVarInt();
        int powerProduced = buf.readVarInt();
        int powerNeeded = buf.readVarInt();
        int waterSupplied = buf.readVarInt();
        int waterNeeded = buf.readVarInt();
        long claimCost = buf.readLong();
        boolean creativeFunded = buf.readBoolean();

        int count = buf.readVarInt();
        if (count < 0 || count > MAX_CHUNKS) {
            throw new IllegalArgumentException("Claimed chunk count out of range: " + count);
        }
        long[] chunks = new long[count];
        for (int i = 0; i < count; i++) {
            chunks[i] = buf.readLong();
        }
        return new CitySyncPayload(hasCity, name, treasury, housing, population, jobs, employed,
                powerProduced, powerNeeded, waterSupplied, waterNeeded, claimCost, creativeFunded,
                chunks);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
