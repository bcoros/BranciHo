package com.branciho.citiesinlife.net.payload;

import com.branciho.citiesinlife.CitiesInLife;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * "I am looking at the missile map."
 *
 * <p>Asked for rather than pushed, and re-asked while the screen is open. The strategic picture is
 * far more data than the ordinary sync and it goes stale in seconds when something is in the air —
 * so it is sent to the one person looking at it, for as long as they are looking.
 */
public record RequestMissileMapPayload() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestMissileMapPayload> TYPE =
            new CustomPacketPayload.Type<>(CitiesInLife.id("request_missile_map"));

    private static final RequestMissileMapPayload INSTANCE = new RequestMissileMapPayload();

    /** Carries no data, so it writes no bytes. */
    public static final StreamCodec<FriendlyByteBuf, RequestMissileMapPayload> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
