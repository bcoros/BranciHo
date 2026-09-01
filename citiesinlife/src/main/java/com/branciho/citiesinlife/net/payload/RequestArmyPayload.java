package com.branciho.citiesinlife.net.payload;

import com.branciho.citiesinlife.CitiesInLife;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** "Send me the army roll." Sent when the Military Tool is used, and after every action on it. */
public record RequestArmyPayload() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestArmyPayload> TYPE =
            new CustomPacketPayload.Type<>(CitiesInLife.id("request_army"));

    private static final RequestArmyPayload INSTANCE = new RequestArmyPayload();

    public static final StreamCodec<FriendlyByteBuf, RequestArmyPayload> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
