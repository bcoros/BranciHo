package com.branciho.livingcities.net.payload;

import com.branciho.livingcities.LivingCities;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client asks for the overlay data around itself, or says it has stopped looking.
 *
 * <p>Sent when the overlay is toggled and refreshed while it is on. The client never says <em>where</em>
 * it wants data for; the server uses the player's own position, so this cannot be used to survey a
 * rival's territory from across the map.
 */
public record RequestOverlayPayload(boolean enabled) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestOverlayPayload> TYPE =
            new CustomPacketPayload.Type<>(LivingCities.id("request_overlay"));

    public static final StreamCodec<FriendlyByteBuf, RequestOverlayPayload> STREAM_CODEC =
            StreamCodec.ofMember(RequestOverlayPayload::write, RequestOverlayPayload::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeBoolean(enabled);
    }

    private static RequestOverlayPayload read(FriendlyByteBuf buf) {
        return new RequestOverlayPayload(buf.readBoolean());
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
