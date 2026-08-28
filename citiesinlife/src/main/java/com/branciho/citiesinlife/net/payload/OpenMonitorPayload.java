package com.branciho.citiesinlife.net.payload;

import com.branciho.citiesinlife.CitiesInLife;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * "Open the control room."
 *
 * <p>A block's use handler runs on the server, where {@code Minecraft.setScreen} does not exist, so
 * opening a screen has to be a request the client acts on. The same shape as the delete-city
 * confirmation, for the same reason.
 */
public record OpenMonitorPayload(BlockPos monitor) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<OpenMonitorPayload> TYPE =
            new CustomPacketPayload.Type<>(CitiesInLife.id("open_monitor"));

    public static final StreamCodec<FriendlyByteBuf, OpenMonitorPayload> STREAM_CODEC =
            StreamCodec.ofMember(OpenMonitorPayload::write, OpenMonitorPayload::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(monitor);
    }

    private static OpenMonitorPayload read(FriendlyByteBuf buf) {
        return new OpenMonitorPayload(buf.readBlockPos());
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
