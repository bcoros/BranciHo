package com.branciho.citiesinlife.net.payload;

import com.branciho.citiesinlife.CitiesInLife;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

/**
 * Yes or no to a meeting invitation.
 *
 * <p>The city is named again on the way back rather than remembered on the server against the
 * player, because two invitations can be in flight at once and a bare "yes" would be ambiguous
 * about which one it answered.
 *
 * <p>Accepting is not trusted on its face: the server re-checks that the meeting is still open, has
 * room, and that the player is not already sitting in another one. A client that sends an accept
 * for a meeting that ended a second ago gets a refusal, not a teleport into nowhere.
 *
 * @param cityId  the meeting being answered
 * @param joining true to attend
 */
public record MeetingReplyPayload(UUID cityId, boolean joining) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<MeetingReplyPayload> TYPE =
            new CustomPacketPayload.Type<>(CitiesInLife.id("meeting_reply"));

    public static final StreamCodec<FriendlyByteBuf, MeetingReplyPayload> STREAM_CODEC =
            StreamCodec.ofMember(MeetingReplyPayload::write, MeetingReplyPayload::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeUUID(cityId);
        buf.writeBoolean(joining);
    }

    private static MeetingReplyPayload read(FriendlyByteBuf buf) {
        return new MeetingReplyPayload(buf.readUUID(), buf.readBoolean());
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
