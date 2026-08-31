package com.branciho.citiesinlife.net.payload;

import com.branciho.citiesinlife.CitiesInLife;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * A button on the City Hall panel.
 *
 * <p>One payload for every button rather than one payload each, because they all share the same
 * gate — you must be standing in your own city hall — and splitting them would mean writing that
 * check four times and forgetting it once. The action is a string rather than an enum ordinal so
 * that an old client sending a name this build has never heard of is refused rather than being
 * silently read as whichever action happens to sit at that index.
 *
 * <p>Nothing here is trusted. The action is matched against a known list, the detail is length
 * capped on the wire, and the alert level falls back to peace if it does not parse.
 *
 * @param action one of {@code alert}, {@code meeting_start}, {@code meeting_end}, {@code address}
 * @param detail the alert level id, or the message to broadcast — empty where unused
 */
public record CityHallActionPayload(String action, String detail) implements CustomPacketPayload {

    private static final int MAX_ACTION = 24;

    /** Long enough for a warning worth reading, short enough not to be a chat client. */
    public static final int MAX_DETAIL = 128;

    public static final CustomPacketPayload.Type<CityHallActionPayload> TYPE =
            new CustomPacketPayload.Type<>(CitiesInLife.id("city_hall_action"));

    public static final StreamCodec<FriendlyByteBuf, CityHallActionPayload> STREAM_CODEC =
            StreamCodec.ofMember(CityHallActionPayload::write, CityHallActionPayload::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeUtf(action, MAX_ACTION);
        buf.writeUtf(detail, MAX_DETAIL);
    }

    private static CityHallActionPayload read(FriendlyByteBuf buf) {
        return new CityHallActionPayload(buf.readUtf(MAX_ACTION), buf.readUtf(MAX_DETAIL));
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
