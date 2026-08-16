package com.branciho.citiesinlife.net.payload;

import com.branciho.citiesinlife.CitiesInLife;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

/** Remove a structure registration. The blocks are untouched; only the claim on them goes away. */
public record DeleteStructurePayload(UUID structureId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<DeleteStructurePayload> TYPE =
            new CustomPacketPayload.Type<>(CitiesInLife.id("delete_structure"));

    public static final StreamCodec<FriendlyByteBuf, DeleteStructurePayload> STREAM_CODEC =
            StreamCodec.ofMember(DeleteStructurePayload::write, DeleteStructurePayload::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeUUID(structureId);
    }

    private static DeleteStructurePayload read(FriendlyByteBuf buf) {
        return new DeleteStructurePayload(buf.readUUID());
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
