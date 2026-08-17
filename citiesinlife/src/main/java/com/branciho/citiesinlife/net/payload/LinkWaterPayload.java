package com.branciho.citiesinlife.net.payload;

import com.branciho.citiesinlife.CitiesInLife;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * "Run a pipe link between these two blocks."
 *
 * <p>Both ends are re-checked server side: that they really are water blocks, that the pair is one
 * the rules allow, that they are within reach, and that this would not put a second starter pump on
 * one station.
 */
public record LinkWaterPayload(BlockPos from, BlockPos to, boolean disconnect)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<LinkWaterPayload> TYPE =
            new CustomPacketPayload.Type<>(CitiesInLife.id("link_water"));

    public static final StreamCodec<FriendlyByteBuf, LinkWaterPayload> STREAM_CODEC =
            StreamCodec.ofMember(LinkWaterPayload::write, LinkWaterPayload::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(from);
        buf.writeBlockPos(to);
        buf.writeBoolean(disconnect);
    }

    private static LinkWaterPayload read(FriendlyByteBuf buf) {
        return new LinkWaterPayload(buf.readBlockPos(), buf.readBlockPos(), buf.readBoolean());
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
