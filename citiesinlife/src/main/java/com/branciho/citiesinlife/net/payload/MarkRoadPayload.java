package com.branciho.citiesinlife.net.payload;

import com.branciho.citiesinlife.CitiesInLife;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * "Everything in this box is road, and it runs this way" - or, when removing, "is not road at all".
 *
 * <p>The flags are what the player has set on the brush. The server sanitises them: a client that
 * asks for bits this mod does not understand gets them dropped rather than stored.
 */
public record MarkRoadPayload(BlockPos pointA, BlockPos pointB, int flags, boolean remove)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<MarkRoadPayload> TYPE =
            new CustomPacketPayload.Type<>(CitiesInLife.id("mark_road"));

    public static final StreamCodec<FriendlyByteBuf, MarkRoadPayload> STREAM_CODEC =
            StreamCodec.ofMember(MarkRoadPayload::write, MarkRoadPayload::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(pointA);
        buf.writeBlockPos(pointB);
        buf.writeVarInt(flags);
        buf.writeBoolean(remove);
    }

    private static MarkRoadPayload read(FriendlyByteBuf buf) {
        return new MarkRoadPayload(
                buf.readBlockPos(), buf.readBlockPos(), buf.readVarInt(), buf.readBoolean());
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
