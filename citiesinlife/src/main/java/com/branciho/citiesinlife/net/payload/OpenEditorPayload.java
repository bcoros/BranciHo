package com.branciho.citiesinlife.net.payload;

import com.branciho.citiesinlife.CitiesInLife;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * "Open the editor."
 *
 * <p>Sent after the list, never before it, so the screen has something to show the moment it
 * appears. Zero fields: the answer to the question is already on its way in the packet before it.
 */
public record OpenEditorPayload() implements CustomPacketPayload {

    public static final OpenEditorPayload INSTANCE = new OpenEditorPayload();

    public static final CustomPacketPayload.Type<OpenEditorPayload> TYPE =
            new CustomPacketPayload.Type<>(CitiesInLife.id("open_editor"));

    public static final StreamCodec<FriendlyByteBuf, OpenEditorPayload> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
