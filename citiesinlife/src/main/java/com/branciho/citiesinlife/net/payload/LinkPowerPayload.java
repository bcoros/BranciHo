package com.branciho.citiesinlife.net.payload;

import com.branciho.citiesinlife.CitiesInLife;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * "Run a power line between these two blocks."
 *
 * <p>Both ends are re-checked server side: that they really are power blocks, that they are within
 * the shorter of the two reaches, and that the line does not already exist.
 */
public record LinkPowerPayload(BlockPos from, BlockPos to, boolean disconnect)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<LinkPowerPayload> TYPE =
            new CustomPacketPayload.Type<>(CitiesInLife.id("link_power"));

    public static final StreamCodec<FriendlyByteBuf, LinkPowerPayload> STREAM_CODEC =
            StreamCodec.ofMember(LinkPowerPayload::write, LinkPowerPayload::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(from);
        buf.writeBlockPos(to);
        buf.writeBoolean(disconnect);
    }

    private static LinkPowerPayload read(FriendlyByteBuf buf) {
        return new LinkPowerPayload(buf.readBlockPos(), buf.readBlockPos(), buf.readBoolean());
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
