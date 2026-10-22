package com.branciho.citiesinlife.net.payload;

import com.branciho.citiesinlife.CitiesInLife;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** "Everything in this box is pavement" - or, when removing, "no longer is". */
public record MarkPathPayload(BlockPos pointA, BlockPos pointB, boolean remove)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<MarkPathPayload> TYPE =
            new CustomPacketPayload.Type<>(CitiesInLife.id("mark_path"));

    public static final StreamCodec<FriendlyByteBuf, MarkPathPayload> STREAM_CODEC =
            StreamCodec.ofMember(MarkPathPayload::write, MarkPathPayload::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(pointA);
        buf.writeBlockPos(pointB);
        buf.writeBoolean(remove);
    }

    private static MarkPathPayload read(FriendlyByteBuf buf) {
        return new MarkPathPayload(buf.readBlockPos(), buf.readBlockPos(), buf.readBoolean());
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
