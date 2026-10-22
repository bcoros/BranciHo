package com.branciho.citiesinlife.net.payload;

import com.branciho.citiesinlife.CitiesInLife;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

/**
 * Something the player pressed in the Military Tool.
 *
 * <p>One payload for four buttons rather than four payloads, because every one of them is "do this
 * to that soldier" and the server has to re-check the city, the base and the money for all of them
 * anyway.
 */
public record MilitaryActionPayload(Action action, UUID soldier) implements CustomPacketPayload {

    public enum Action {
        /** Take somebody on. The soldier field is ignored. */
        HIRE,
        /** Let somebody go. */
        DISMISS,
        /** Send somebody on a course. */
        TRAIN,
        /** Hand somebody whatever is in the player's off hand. */
        ARM
    }

    /** Used for actions that are not about a particular soldier. */
    public static final UUID NOBODY = new UUID(0L, 0L);

    public static final CustomPacketPayload.Type<MilitaryActionPayload> TYPE =
            new CustomPacketPayload.Type<>(CitiesInLife.id("military_action"));

    public static final StreamCodec<FriendlyByteBuf, MilitaryActionPayload> STREAM_CODEC =
            StreamCodec.ofMember(MilitaryActionPayload::write, MilitaryActionPayload::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(action.ordinal());
        buf.writeUUID(soldier);
    }

    private static MilitaryActionPayload read(FriendlyByteBuf buf) {
        int ordinal = buf.readVarInt();
        Action[] values = Action.values();
        if (ordinal < 0 || ordinal >= values.length) {
            throw new IllegalArgumentException("Unknown military action: " + ordinal);
        }
        return new MilitaryActionPayload(values[ordinal], buf.readUUID());
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
