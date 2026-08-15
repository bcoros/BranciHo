package com.branciho.livingcities.net.payload;

import com.branciho.livingcities.LivingCities;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

/**
 * Client sets what one floor of a building is used for.
 *
 * <p>This is the packet that makes mixed use work: floor 1 commercial, 2-6 office, 7-25 residential,
 * all in the same structure. The zone travels as its string id rather than an ordinal so that adding or
 * reordering zone types later cannot silently reinterpret an in-flight packet as a different use.
 */
public record SetFloorZonePayload(UUID buildingId, int floorIndex, String zoneId) implements CustomPacketPayload {

    private static final int MAX_ZONE_ID_LENGTH = 32;

    public static final CustomPacketPayload.Type<SetFloorZonePayload> TYPE =
            new CustomPacketPayload.Type<>(LivingCities.id("set_floor_zone"));

    public static final StreamCodec<FriendlyByteBuf, SetFloorZonePayload> STREAM_CODEC =
            StreamCodec.ofMember(SetFloorZonePayload::write, SetFloorZonePayload::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeUUID(buildingId);
        buf.writeVarInt(floorIndex);
        buf.writeUtf(zoneId, MAX_ZONE_ID_LENGTH);
    }

    private static SetFloorZonePayload read(FriendlyByteBuf buf) {
        return new SetFloorZonePayload(buf.readUUID(), buf.readVarInt(), buf.readUtf(MAX_ZONE_ID_LENGTH));
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
