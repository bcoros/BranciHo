package com.branciho.citiesinlife.net.payload;

import com.branciho.citiesinlife.CitiesInLife;
import com.branciho.citiesinlife.city.CityFlag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * "This is my city's flag now."
 *
 * <p>Fixed length on purpose. A flag is the one piece of city data a player composes byte by byte,
 * so the packet reads exactly forty squares and refuses anything else rather than trusting a length
 * somebody else's client sent.
 */
public record SetFlagPayload(byte[] cells) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SetFlagPayload> TYPE =
            new CustomPacketPayload.Type<>(CitiesInLife.id("set_flag"));

    public static final StreamCodec<FriendlyByteBuf, SetFlagPayload> STREAM_CODEC =
            StreamCodec.ofMember(SetFlagPayload::write, SetFlagPayload::read);

    private void write(FriendlyByteBuf buf) {
        byte[] safe = CityFlag.sanitise(cells);
        for (byte cell : safe) {
            buf.writeByte(cell);
        }
    }

    private static SetFlagPayload read(FriendlyByteBuf buf) {
        byte[] cells = new byte[CityFlag.CELLS];
        for (int i = 0; i < cells.length; i++) {
            cells[i] = buf.readByte();
        }
        return new SetFlagPayload(CityFlag.sanitise(cells));
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
