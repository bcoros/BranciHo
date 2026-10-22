package com.branciho.citiesinlife.net.payload;

import com.branciho.citiesinlife.CitiesInLife;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** "Turn my creative treasury off — or back on." Sent by Shift+I. */
public record ToggleCreativeMoneyPayload() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ToggleCreativeMoneyPayload> TYPE =
            new CustomPacketPayload.Type<>(CitiesInLife.id("toggle_creative_money"));

    private static final ToggleCreativeMoneyPayload INSTANCE = new ToggleCreativeMoneyPayload();

    /** Carries no data, so it writes no bytes. */
    public static final StreamCodec<FriendlyByteBuf, ToggleCreativeMoneyPayload> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
