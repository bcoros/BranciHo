package com.branciho.citiesinlife.net.payload;

import com.branciho.citiesinlife.CitiesInLife;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * "Remove every registration inside this box."
 *
 * <p>Deleting by area rather than by picking one with the crosshair: aiming at an invisible box is
 * unreliable, and the player already knows how to draw a box because that is how registrations are
 * made in the first place.
 */
public record DeleteAreaPayload(BlockPos pointA, BlockPos pointB) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<DeleteAreaPayload> TYPE =
            new CustomPacketPayload.Type<>(CitiesInLife.id("delete_area"));

    public static final StreamCodec<FriendlyByteBuf, DeleteAreaPayload> STREAM_CODEC =
            StreamCodec.ofMember(DeleteAreaPayload::write, DeleteAreaPayload::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(pointA);
        buf.writeBlockPos(pointB);
    }

    private static DeleteAreaPayload read(FriendlyByteBuf buf) {
        return new DeleteAreaPayload(buf.readBlockPos(), buf.readBlockPos());
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
