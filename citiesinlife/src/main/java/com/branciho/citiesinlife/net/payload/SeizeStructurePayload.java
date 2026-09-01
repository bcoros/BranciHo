package com.branciho.citiesinlife.net.payload;

import com.branciho.citiesinlife.CitiesInLife;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * "Take the building in this box, and make it this."
 *
 * <p>An empty type means leave it as whatever it already was, which is the whole reason this is a
 * separate payload from registering a structure rather than a flag on it.
 */
public record SeizeStructurePayload(BlockPos pointA, BlockPos pointB, String typeId)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SeizeStructurePayload> TYPE =
            new CustomPacketPayload.Type<>(CitiesInLife.id("seize_structure"));

    public static final StreamCodec<FriendlyByteBuf, SeizeStructurePayload> STREAM_CODEC =
            StreamCodec.ofMember(SeizeStructurePayload::write, SeizeStructurePayload::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(pointA);
        buf.writeBlockPos(pointB);
        buf.writeUtf(typeId, 32);
    }

    private static SeizeStructurePayload read(FriendlyByteBuf buf) {
        return new SeizeStructurePayload(buf.readBlockPos(), buf.readBlockPos(), buf.readUtf(32));
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
