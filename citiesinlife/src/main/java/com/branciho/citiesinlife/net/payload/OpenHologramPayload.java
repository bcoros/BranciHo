package com.branciho.citiesinlife.net.payload;

import com.branciho.citiesinlife.CitiesInLife;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * "Open the projection."
 *
 * <p>A block's use handler runs on the server, where {@code Minecraft.setScreen} does not exist —
 * and a common-side class that so much as mentions a screen class is a crash waiting for the first
 * dedicated server. So the open comes back as a message, the same way the reactor monitor's does.
 */
public record OpenHologramPayload() implements CustomPacketPayload {

    public static final OpenHologramPayload INSTANCE = new OpenHologramPayload();

    public static final CustomPacketPayload.Type<OpenHologramPayload> TYPE =
            new CustomPacketPayload.Type<>(CitiesInLife.id("open_hologram"));

    public static final StreamCodec<FriendlyByteBuf, OpenHologramPayload> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
