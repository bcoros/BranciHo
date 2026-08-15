package com.branciho.livingcities.net.payload;

import com.branciho.livingcities.LivingCities;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client asks for the city it is standing in (or its own city) to be sent and shown.
 *
 * <p>Sent when the player presses the management hotkey. Carries no data the server would trust -
 * the server resolves which city the player is allowed to see from the player's own position.
 */
public record RequestCityDataPayload(boolean openScreen) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestCityDataPayload> TYPE =
            new CustomPacketPayload.Type<>(LivingCities.id("request_city_data"));

    public static final StreamCodec<FriendlyByteBuf, RequestCityDataPayload> STREAM_CODEC =
            StreamCodec.ofMember(RequestCityDataPayload::write, RequestCityDataPayload::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeBoolean(openScreen);
    }

    private static RequestCityDataPayload read(FriendlyByteBuf buf) {
        return new RequestCityDataPayload(buf.readBoolean());
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
