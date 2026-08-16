package com.branciho.citiesinlife.net.payload;

import com.branciho.citiesinlife.CitiesInLife;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * "That box has your city hall in it. Are you sure?"
 *
 * <p>Sent instead of deleting. It carries the same box back so the answer can be a plain
 * {@link DeleteAreaPayload} with its confirmed flag set, rather than a second kind of request the
 * server would have to remember it was expecting.
 *
 * @param structures how many registrations go with the city, so the warning can be specific about
 *                   what is being thrown away rather than vaguely ominous
 */
public record ConfirmDeleteCityPayload(BlockPos pointA, BlockPos pointB, String cityName,
                                       int structures, int chunks, long treasury)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ConfirmDeleteCityPayload> TYPE =
            new CustomPacketPayload.Type<>(CitiesInLife.id("confirm_delete_city"));

    public static final StreamCodec<FriendlyByteBuf, ConfirmDeleteCityPayload> STREAM_CODEC =
            StreamCodec.ofMember(ConfirmDeleteCityPayload::write, ConfirmDeleteCityPayload::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(pointA);
        buf.writeBlockPos(pointB);
        buf.writeUtf(cityName, 64);
        buf.writeVarInt(structures);
        buf.writeVarInt(chunks);
        buf.writeLong(treasury);
    }

    private static ConfirmDeleteCityPayload read(FriendlyByteBuf buf) {
        return new ConfirmDeleteCityPayload(buf.readBlockPos(), buf.readBlockPos(),
                buf.readUtf(64), buf.readVarInt(), buf.readVarInt(), buf.readLong());
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
