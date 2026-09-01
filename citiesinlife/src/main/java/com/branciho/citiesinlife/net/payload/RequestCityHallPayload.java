package com.branciho.citiesinlife.net.payload;

import com.branciho.citiesinlife.CitiesInLife;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * "What is my city hall doing?" Sent when the City Hall panel opens, and again on a timer while it
 * is open — a meeting fills up while you are looking at it, and a board that only reads correctly
 * at the moment it was opened is worse than no board.
 */
public record RequestCityHallPayload() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestCityHallPayload> TYPE =
            new CustomPacketPayload.Type<>(CitiesInLife.id("request_city_hall"));

    private static final RequestCityHallPayload INSTANCE = new RequestCityHallPayload();

    /** Carries no data, so it writes no bytes. */
    public static final StreamCodec<FriendlyByteBuf, RequestCityHallPayload> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
