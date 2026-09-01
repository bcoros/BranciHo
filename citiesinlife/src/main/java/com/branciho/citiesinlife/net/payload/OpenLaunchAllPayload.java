package com.branciho.citiesinlife.net.payload;

import com.branciho.citiesinlife.CitiesInLife;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * "Open the target list."
 *
 * <p>Sent when the launch button in the hall is pressed. A block's use handler runs on the server,
 * where {@code Minecraft.setScreen} does not exist, so the open comes back as a message — the same
 * hand-off the reactor monitor and the projection table already use.
 */
public record OpenLaunchAllPayload() implements CustomPacketPayload {

    public static final OpenLaunchAllPayload INSTANCE = new OpenLaunchAllPayload();

    public static final CustomPacketPayload.Type<OpenLaunchAllPayload> TYPE =
            new CustomPacketPayload.Type<>(CitiesInLife.id("open_launch_all"));

    public static final StreamCodec<FriendlyByteBuf, OpenLaunchAllPayload> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
