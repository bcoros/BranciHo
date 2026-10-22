package com.branciho.citiesinlife.net.payload;

import com.branciho.citiesinlife.CitiesInLife;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** "Who is standing on my ground right now?" Asked by the hologram map, on a timer. */
public record RequestHologramPayload() implements CustomPacketPayload {

    public static final RequestHologramPayload INSTANCE = new RequestHologramPayload();

    public static final CustomPacketPayload.Type<RequestHologramPayload> TYPE =
            new CustomPacketPayload.Type<>(CitiesInLife.id("request_hologram"));

    public static final StreamCodec<FriendlyByteBuf, RequestHologramPayload> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
