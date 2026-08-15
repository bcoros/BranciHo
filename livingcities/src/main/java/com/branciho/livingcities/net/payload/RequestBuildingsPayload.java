package com.branciho.livingcities.net.payload;

import com.branciho.livingcities.LivingCities;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.Optional;
import java.util.UUID;

/**
 * Client asks for either a page of its city's buildings, or one building's full detail.
 *
 * <p>One payload for both because they are the same question at two zoom levels, and the server
 * resolves the city from the player either way - the client never names which city it wants.
 */
public record RequestBuildingsPayload(int page, Optional<UUID> buildingId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestBuildingsPayload> TYPE =
            new CustomPacketPayload.Type<>(LivingCities.id("request_buildings"));

    public static final StreamCodec<FriendlyByteBuf, RequestBuildingsPayload> STREAM_CODEC =
            StreamCodec.ofMember(RequestBuildingsPayload::write, RequestBuildingsPayload::read);

    public static RequestBuildingsPayload page(int page) {
        return new RequestBuildingsPayload(page, Optional.empty());
    }

    public static RequestBuildingsPayload detail(UUID buildingId) {
        return new RequestBuildingsPayload(0, Optional.of(buildingId));
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(page);
        buf.writeBoolean(buildingId.isPresent());
        buildingId.ifPresent(buf::writeUUID);
    }

    private static RequestBuildingsPayload read(FriendlyByteBuf buf) {
        int page = buf.readVarInt();
        Optional<UUID> id = buf.readBoolean() ? Optional.of(buf.readUUID()) : Optional.empty();
        return new RequestBuildingsPayload(page, id);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
