package com.branciho.citiesinlife.net.payload;

import com.branciho.citiesinlife.CitiesInLife;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * How much fallout this player is standing in, from nought to a hundred.
 *
 * <p>One number, once a second, and only to players a source can actually reach. Zero is sent as
 * well as anything else — it is what tells a client that has walked out of the zone to stop
 * tinting the screen, and a client that simply stopped hearing from us would have no way to know
 * the difference between "you are clear" and "the server is busy".
 */
public record RadiationPayload(int strength) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RadiationPayload> TYPE =
            new CustomPacketPayload.Type<>(CitiesInLife.id("radiation"));

    public static final StreamCodec<FriendlyByteBuf, RadiationPayload> STREAM_CODEC =
            StreamCodec.ofMember(RadiationPayload::write, RadiationPayload::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(strength);
    }

    private static RadiationPayload read(FriendlyByteBuf buf) {
        return new RadiationPayload(buf.readVarInt());
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
