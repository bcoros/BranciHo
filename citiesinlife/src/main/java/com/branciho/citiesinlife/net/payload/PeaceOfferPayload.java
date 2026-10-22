package com.branciho.citiesinlife.net.payload;

import com.branciho.citiesinlife.CitiesInLife;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

/**
 * "They want to stop. Do you?"
 *
 * <p>Sent the moment a treaty is proposed, so somebody who is online is asked rather than left to
 * find out by opening a screen. The offer itself lives on the city and survives being offline; this
 * packet is only the knock at the door.
 *
 * @param fromCityId the city offering, which is also the city you would be answering
 */
public record PeaceOfferPayload(UUID fromCityId, String fromName) implements CustomPacketPayload {

    private static final int MAX_NAME = 64;

    public static final CustomPacketPayload.Type<PeaceOfferPayload> TYPE =
            new CustomPacketPayload.Type<>(CitiesInLife.id("peace_offer"));

    public static final StreamCodec<FriendlyByteBuf, PeaceOfferPayload> STREAM_CODEC =
            StreamCodec.ofMember(PeaceOfferPayload::write, PeaceOfferPayload::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeUUID(fromCityId);
        buf.writeUtf(fromName, MAX_NAME);
    }

    private static PeaceOfferPayload read(FriendlyByteBuf buf) {
        return new PeaceOfferPayload(buf.readUUID(), buf.readUtf(MAX_NAME));
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
