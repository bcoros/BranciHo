package com.branciho.citiesinlife.net.payload;

import com.branciho.citiesinlife.CitiesInLife;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

/**
 * "Your ally has gone to war. Are you coming?"
 *
 * <p>Sent rather than derived, unlike the war reminder a player gets when they log in, because this
 * one is a question with a moment attached to it. Being asked an hour later whether you want to
 * join a war that has been running all afternoon is not the same question.
 *
 * <p>An ally who is offline simply is not asked. Nothing is queued: the alliance still stands, the
 * war is still there when they get back, and they can declare on their own account if they want in.
 *
 * @param enemyCityId the city the ally would be declaring on
 */
public record CallToArmsPayload(UUID enemyCityId, String allyName, String enemyName)
        implements CustomPacketPayload {

    private static final int MAX_NAME = 64;

    public static final CustomPacketPayload.Type<CallToArmsPayload> TYPE =
            new CustomPacketPayload.Type<>(CitiesInLife.id("call_to_arms"));

    public static final StreamCodec<FriendlyByteBuf, CallToArmsPayload> STREAM_CODEC =
            StreamCodec.ofMember(CallToArmsPayload::write, CallToArmsPayload::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeUUID(enemyCityId);
        buf.writeUtf(allyName, MAX_NAME);
        buf.writeUtf(enemyName, MAX_NAME);
    }

    private static CallToArmsPayload read(FriendlyByteBuf buf) {
        return new CallToArmsPayload(buf.readUUID(), buf.readUtf(MAX_NAME), buf.readUtf(MAX_NAME));
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
