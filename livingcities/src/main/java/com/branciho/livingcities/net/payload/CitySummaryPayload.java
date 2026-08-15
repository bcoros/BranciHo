package com.branciho.livingcities.net.payload;

import com.branciho.livingcities.LivingCities;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

/**
 * Server pushes a compact city snapshot for the HUD and the overview tab.
 *
 * <p>Written by hand rather than with {@code StreamCodec.composite} on purpose: composite tops out at
 * a handful of components, and a city summary is inherently wide. A hand-written codec also keeps the
 * wire format explicit, which matters when this has to stay backwards compatible.
 */
public record CitySummaryPayload(
        UUID cityId,
        String cityName,
        int population,
        int housingCapacity,
        int jobs,
        int employed,
        long treasuryCents,
        long dailyIncomeCents,
        long dailyExpenseCents,
        int happinessPermille,
        int powerSatisfactionPermille,
        int claimedChunks,
        int buildingCount
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CitySummaryPayload> TYPE =
            new CustomPacketPayload.Type<>(LivingCities.id("city_summary"));

    public static final StreamCodec<FriendlyByteBuf, CitySummaryPayload> STREAM_CODEC =
            StreamCodec.ofMember(CitySummaryPayload::write, CitySummaryPayload::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeUUID(cityId);
        buf.writeUtf(cityName, CreateCityPayload.MAX_NAME_LENGTH);
        buf.writeVarInt(population);
        buf.writeVarInt(housingCapacity);
        buf.writeVarInt(jobs);
        buf.writeVarInt(employed);
        buf.writeLong(treasuryCents);
        buf.writeLong(dailyIncomeCents);
        buf.writeLong(dailyExpenseCents);
        buf.writeVarInt(happinessPermille);
        buf.writeVarInt(powerSatisfactionPermille);
        buf.writeVarInt(claimedChunks);
        buf.writeVarInt(buildingCount);
    }

    private static CitySummaryPayload read(FriendlyByteBuf buf) {
        return new CitySummaryPayload(
                buf.readUUID(),
                buf.readUtf(CreateCityPayload.MAX_NAME_LENGTH),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readLong(),
                buf.readLong(),
                buf.readLong(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt());
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
