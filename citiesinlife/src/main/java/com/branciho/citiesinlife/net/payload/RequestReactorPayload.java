package com.branciho.citiesinlife.net.payload;

import com.branciho.citiesinlife.CitiesInLife;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** "Tell me about the reactor this monitor belongs to." Sent on open and while the screen lives. */
public record RequestReactorPayload(BlockPos monitor) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestReactorPayload> TYPE =
            new CustomPacketPayload.Type<>(CitiesInLife.id("request_reactor"));

    public static final StreamCodec<FriendlyByteBuf, RequestReactorPayload> STREAM_CODEC =
            StreamCodec.ofMember(RequestReactorPayload::write, RequestReactorPayload::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(monitor);
    }

    private static RequestReactorPayload read(FriendlyByteBuf buf) {
        return new RequestReactorPayload(buf.readBlockPos());
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
