package com.branciho.livingcities.net.payload;

import com.branciho.livingcities.LivingCities;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

/** Client asks for a building's geometry to be measured again after editing it. */
public record RescanBuildingPayload(UUID buildingId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RescanBuildingPayload> TYPE =
            new CustomPacketPayload.Type<>(LivingCities.id("rescan_building"));

    public static final StreamCodec<FriendlyByteBuf, RescanBuildingPayload> STREAM_CODEC =
            StreamCodec.ofMember(RescanBuildingPayload::write, RescanBuildingPayload::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeUUID(buildingId);
    }

    private static RescanBuildingPayload read(FriendlyByteBuf buf) {
        return new RescanBuildingPayload(buf.readUUID());
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
