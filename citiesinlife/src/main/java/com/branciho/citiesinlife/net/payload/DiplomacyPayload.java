package com.branciho.citiesinlife.net.payload;

import com.branciho.citiesinlife.CitiesInLife;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

/**
 * "Do this to that city." The server decides whether you are in any position to.
 *
 * @param a first argument, meaning whatever the action needs: a {@link
 *          com.branciho.citiesinlife.city.Pact} ordinal, or a power price
 * @param b second argument: a water price, and unused by everything else
 */
public record DiplomacyPayload(UUID targetCityId, int action, int a, int b)
        implements CustomPacketPayload {

    public static final int ACTION_GRANT = 0;
    public static final int ACTION_REVOKE = 1;
    public static final int ACTION_DECLARE_WAR = 2;
    public static final int ACTION_MAKE_PEACE = 3;

    /** Offer a pact, or accept one already offered — the same click either way. */
    public static final int ACTION_PACT_OFFER = 4;

    /** Withdraw this city's half. Ends the pact immediately if it was live. */
    public static final int ACTION_PACT_CANCEL = 5;

    /** Set what this city charges that one for power and water, per unit per step. */
    public static final int ACTION_SET_PRICES = 6;

    /** An ally answering a call to arms by declaring on the same target. */
    public static final int ACTION_JOIN_WAR = 7;

    /** Accept a treaty they have offered. Ends the war for both. */
    public static final int ACTION_ACCEPT_PEACE = 8;

    /** Refuse one. The war carries on and they are told so. */
    public static final int ACTION_DECLINE_PEACE = 9;

    /** The two-argument actions only. Everything else can use this. */
    public static DiplomacyPayload of(UUID target, int action) {
        return new DiplomacyPayload(target, action, 0, 0);
    }

    public static final CustomPacketPayload.Type<DiplomacyPayload> TYPE =
            new CustomPacketPayload.Type<>(CitiesInLife.id("diplomacy"));

    public static final StreamCodec<FriendlyByteBuf, DiplomacyPayload> STREAM_CODEC =
            StreamCodec.ofMember(DiplomacyPayload::write, DiplomacyPayload::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeUUID(targetCityId);
        buf.writeVarInt(action);
        buf.writeVarInt(a);
        buf.writeVarInt(b);
    }

    private static DiplomacyPayload read(FriendlyByteBuf buf) {
        return new DiplomacyPayload(buf.readUUID(), buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt());
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
