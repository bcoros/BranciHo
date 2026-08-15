package com.branciho.livingcities.net.payload;

import com.branciho.livingcities.LivingCities;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

/**
 * Client asks for a building registration to be deleted.
 *
 * <p>Without this a registration was permanent. Since a registration is an invisible box rather than
 * anything you can break, demolishing the structure left a ghost that went on reserving the ground and
 * rejecting every new building placed there as "overlapping", with no way to clear it.
 */
public record RemoveBuildingPayload(UUID buildingId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RemoveBuildingPayload> TYPE =
            new CustomPacketPayload.Type<>(LivingCities.id("remove_building"));

    public static final StreamCodec<FriendlyByteBuf, RemoveBuildingPayload> STREAM_CODEC =
            StreamCodec.ofMember(RemoveBuildingPayload::write, RemoveBuildingPayload::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeUUID(buildingId);
    }

    private static RemoveBuildingPayload read(FriendlyByteBuf buf) {
        return new RemoveBuildingPayload(buf.readUUID());
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
