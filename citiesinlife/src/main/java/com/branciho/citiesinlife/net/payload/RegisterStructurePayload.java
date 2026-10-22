package com.branciho.citiesinlife.net.payload;

import com.branciho.citiesinlife.CitiesInLife;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * "I have drawn a box and I want it to be a {@code type}."
 *
 * <p>A request, not an instruction. The server re-derives every consequence — whether the ground is
 * owned, whether anything overlaps, how much space is really in there — from its own state. The
 * only thing it takes from this packet is what the player wants.
 */
public record RegisterStructurePayload(BlockPos pointA, BlockPos pointB, String typeId,
                                       String cityName)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RegisterStructurePayload> TYPE =
            new CustomPacketPayload.Type<>(CitiesInLife.id("register_structure"));

    public static final StreamCodec<FriendlyByteBuf, RegisterStructurePayload> STREAM_CODEC =
            StreamCodec.ofMember(RegisterStructurePayload::write, RegisterStructurePayload::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(pointA);
        buf.writeBlockPos(pointB);
        buf.writeUtf(typeId, 32);
        buf.writeUtf(cityName, 32);
    }

    private static RegisterStructurePayload read(FriendlyByteBuf buf) {
        return new RegisterStructurePayload(
                buf.readBlockPos(), buf.readBlockPos(),
                buf.readUtf(32), buf.readUtf(32));
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
