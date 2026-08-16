package com.branciho.citiesinlife.net.payload;

import com.branciho.citiesinlife.CitiesInLife;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** "Send me my city and the structures around here." Sent when a screen or overlay opens. */
public record RequestCityPayload() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestCityPayload> TYPE =
            new CustomPacketPayload.Type<>(CitiesInLife.id("request_city"));

    private static final RequestCityPayload INSTANCE = new RequestCityPayload();

    /** Carries no data, so it writes no bytes. */
    public static final StreamCodec<FriendlyByteBuf, RequestCityPayload> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
