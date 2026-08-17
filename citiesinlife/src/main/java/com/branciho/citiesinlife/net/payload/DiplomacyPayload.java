package com.branciho.citiesinlife.net.payload;

import com.branciho.citiesinlife.CitiesInLife;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

/** "Do this to that city." The server decides whether you are in any position to. */
public record DiplomacyPayload(UUID targetCityId, int action) implements CustomPacketPayload {

    public static final int ACTION_GRANT = 0;
    public static final int ACTION_REVOKE = 1;
    public static final int ACTION_DECLARE_WAR = 2;
    public static final int ACTION_MAKE_PEACE = 3;

    public static final CustomPacketPayload.Type<DiplomacyPayload> TYPE =
            new CustomPacketPayload.Type<>(CitiesInLife.id("diplomacy"));

    public static final StreamCodec<FriendlyByteBuf, DiplomacyPayload> STREAM_CODEC =
            StreamCodec.ofMember(DiplomacyPayload::write, DiplomacyPayload::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeUUID(targetCityId);
        buf.writeVarInt(action);
    }

    private static DiplomacyPayload read(FriendlyByteBuf buf) {
        return new DiplomacyPayload(buf.readUUID(), buf.readVarInt());
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
