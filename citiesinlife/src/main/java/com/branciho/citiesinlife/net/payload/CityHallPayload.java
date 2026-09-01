package com.branciho.citiesinlife.net.payload;

import com.branciho.citiesinlife.CitiesInLife;
import com.branciho.citiesinlife.city.City;
import com.branciho.citiesinlife.city.LedgerEntry;
import com.branciho.citiesinlife.city.Meeting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything the City Hall panel shows: whether you are standing in it, what you have declared,
 * who is in the room, and what the city remembers.
 *
 * <p>{@code inHall} is decided by the server and sent, rather than worked out on the client from
 * the structure list. The client is told about structures near it for the overlay, but "am I inside
 * my own city hall right now" is the gate on every button here, and a gate the client can compute
 * for itself is a gate a modified client can open. The server refuses the actions anyway; this
 * field exists so the panel can grey the buttons out and say why, rather than letting a player
 * press things that will silently fail.
 *
 * @param hasCity  whether the player has a city at all
 * @param inHall   whether they are standing inside its city hall box
 * @param alert    the declared alert level, by string id
 * @param meeting  whether a meeting is open in this city
 * @param hushed   whether every siren and alarm the city owns has been muted
 * @param guards   how many bodyguards are on the roll
 * @param roll     the host and everyone who has turned up, in arrival order
 * @param ledger   the city's own history, oldest first
 */
public record CityHallPayload(boolean hasCity, boolean inHall, String alert, boolean meeting,
                              boolean hushed, int guards, List<String> roll,
                              List<LedgerEntry> ledger)
        implements CustomPacketPayload {

    private static final int MAX_NAME = 64;

    /** The host plus a full room. Matches what {@link Meeting} will actually admit. */
    private static final int MAX_ROLL = Meeting.MAX_ATTENDEES + 1;

    public static final CustomPacketPayload.Type<CityHallPayload> TYPE =
            new CustomPacketPayload.Type<>(CitiesInLife.id("city_hall"));

    public static final StreamCodec<FriendlyByteBuf, CityHallPayload> STREAM_CODEC =
            StreamCodec.ofMember(CityHallPayload::write, CityHallPayload::read);

    /** What the panel shows before the first packet arrives, and after the city is gone. */
    public static CityHallPayload none() {
        return new CityHallPayload(false, false, "peace", false, false, 0,
                List.of(), List.of());
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeBoolean(hasCity);
        buf.writeBoolean(inHall);
        buf.writeUtf(alert, 16);
        buf.writeBoolean(meeting);
        buf.writeBoolean(hushed);
        buf.writeVarInt(Math.max(0, Math.min(guards, City.MAX_GUARDS)));

        int names = Math.min(roll.size(), MAX_ROLL);
        buf.writeVarInt(names);
        for (int i = 0; i < names; i++) {
            buf.writeUtf(roll.get(i), MAX_NAME);
        }

        int lines = Math.min(ledger.size(), City.MAX_LEDGER);
        buf.writeVarInt(lines);
        // The TAIL of the ledger, not the head: a city at its limit should send the most recent
        // forty lines, and taking from the front would freeze the panel on ancient history.
        for (int i = ledger.size() - lines; i < ledger.size(); i++) {
            LedgerEntry entry = ledger.get(i);
            buf.writeLong(entry.at());
            buf.writeUtf(entry.key(), LedgerEntry.MAX_KEY);
            buf.writeUtf(entry.detail(), LedgerEntry.MAX_DETAIL);
        }
    }

    private static CityHallPayload read(FriendlyByteBuf buf) {
        boolean hasCity = buf.readBoolean();
        boolean inHall = buf.readBoolean();
        String alert = buf.readUtf(16);
        boolean meeting = buf.readBoolean();
        boolean hushed = buf.readBoolean();
        int guards = range(buf.readVarInt(), City.MAX_GUARDS, "bodyguards");

        int names = range(buf.readVarInt(), MAX_ROLL, "meeting roll");
        List<String> roll = new ArrayList<>(names);
        for (int i = 0; i < names; i++) {
            roll.add(buf.readUtf(MAX_NAME));
        }

        int lines = range(buf.readVarInt(), City.MAX_LEDGER, "ledger");
        List<LedgerEntry> ledger = new ArrayList<>(lines);
        for (int i = 0; i < lines; i++) {
            ledger.add(new LedgerEntry(
                    buf.readLong(), buf.readUtf(LedgerEntry.MAX_KEY),
                    buf.readUtf(LedgerEntry.MAX_DETAIL)));
        }
        return new CityHallPayload(hasCity, inHall, alert, meeting, hushed, guards, roll, ledger);
    }

    private static int range(int count, int cap, String what) {
        if (count < 0 || count > cap) {
            throw new IllegalArgumentException("Bad " + what + " count: " + count);
        }
        return count;
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
