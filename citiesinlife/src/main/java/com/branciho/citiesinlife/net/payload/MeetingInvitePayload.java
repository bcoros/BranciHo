package com.branciho.citiesinlife.net.payload;

import com.branciho.citiesinlife.CitiesInLife;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

/**
 * "There is a meeting at Riverport. Are you coming?"
 *
 * <p>Pushed rather than polled, for the same reason the call to arms is: a meeting is a question
 * with a moment attached to it, and being told an hour later that one happened is not the same
 * thing as being asked.
 *
 * <p>Everyone online is asked, allies and enemies alike. A summit you can only invite your friends
 * to is not a summit, and the most useful thing a city hall can do with a rival is get them in a
 * room. Saying no costs nothing.
 *
 * <p>The city is named as well as the host, because "do you want to be teleported somewhere" is not
 * a question anybody should answer without being told whose ground they would be standing on.
 *
 * @param cityId   the city holding the meeting, sent back with the reply
 * @param hostName the player who called it
 * @param cityName the city it is being held in
 */
public record MeetingInvitePayload(UUID cityId, String hostName, String cityName)
        implements CustomPacketPayload {

    private static final int MAX_NAME = 64;

    public static final CustomPacketPayload.Type<MeetingInvitePayload> TYPE =
            new CustomPacketPayload.Type<>(CitiesInLife.id("meeting_invite"));

    public static final StreamCodec<FriendlyByteBuf, MeetingInvitePayload> STREAM_CODEC =
            StreamCodec.ofMember(MeetingInvitePayload::write, MeetingInvitePayload::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeUUID(cityId);
        buf.writeUtf(hostName, MAX_NAME);
        buf.writeUtf(cityName, MAX_NAME);
    }

    private static MeetingInvitePayload read(FriendlyByteBuf buf) {
        return new MeetingInvitePayload(buf.readUUID(), buf.readUtf(MAX_NAME), buf.readUtf(MAX_NAME));
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
