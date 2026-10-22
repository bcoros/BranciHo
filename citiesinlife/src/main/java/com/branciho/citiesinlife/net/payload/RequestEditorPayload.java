package com.branciho.citiesinlife.net.payload;

import com.branciho.citiesinlife.CitiesInLife;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * "Send me the editor list."
 *
 * @param open whether the client wants the screen opened when the answer arrives, as opposed to
 *             refreshing a screen it already has up. One packet rather than two, because the two
 *             would race and the wrong one winning means an editor that opens twice.
 */
public record RequestEditorPayload(boolean open) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestEditorPayload> TYPE =
            new CustomPacketPayload.Type<>(CitiesInLife.id("request_editor"));

    public static final StreamCodec<FriendlyByteBuf, RequestEditorPayload> STREAM_CODEC =
            StreamCodec.ofMember(RequestEditorPayload::write, RequestEditorPayload::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeBoolean(open);
    }

    private static RequestEditorPayload read(FriendlyByteBuf buf) {
        return new RequestEditorPayload(buf.readBoolean());
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
