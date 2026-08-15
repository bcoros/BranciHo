package com.branciho.livingcities.net.payload;

import com.branciho.livingcities.LivingCities;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Everything the client needs to draw the city overlay near the player.
 *
 * <p>Registered buildings are otherwise completely invisible: they are boxes in server data with no
 * blocks of their own. Not drawing them was the reason a demolished building's ghost was impossible to
 * find - you could be standing inside one and have no way to know.
 *
 * <p>Only the area around the requesting player is sent, and both lists are hard-capped, so this stays
 * a small packet regardless of how large a city grows.
 */
public record CityOverlayPayload(
        boolean ownTerritory,
        List<Long> claimedChunks,
        List<BuildingBox> buildings
) implements CustomPacketPayload {

    public static final int MAX_CHUNKS = 1024;
    public static final int MAX_BUILDINGS = 256;

    /** A registered building's bounds plus the little the overlay needs to label and colour it. */
    public record BuildingBox(
            UUID id,
            String name,
            String zoneId,
            boolean mixedUse,
            boolean needsRescan,
            int minX, int minY, int minZ,
            int maxX, int maxY, int maxZ
    ) {
    }

    public static final CustomPacketPayload.Type<CityOverlayPayload> TYPE =
            new CustomPacketPayload.Type<>(LivingCities.id("city_overlay"));

    public static final StreamCodec<FriendlyByteBuf, CityOverlayPayload> STREAM_CODEC =
            StreamCodec.ofMember(CityOverlayPayload::write, CityOverlayPayload::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeBoolean(ownTerritory);

        int chunkCount = Math.min(claimedChunks.size(), MAX_CHUNKS);
        buf.writeVarInt(chunkCount);
        for (int i = 0; i < chunkCount; i++) {
            buf.writeLong(claimedChunks.get(i));
        }

        int buildingCount = Math.min(buildings.size(), MAX_BUILDINGS);
        buf.writeVarInt(buildingCount);
        for (int i = 0; i < buildingCount; i++) {
            BuildingBox box = buildings.get(i);
            buf.writeUUID(box.id());
            buf.writeUtf(box.name(), 32);
            buf.writeUtf(box.zoneId(), 32);
            buf.writeBoolean(box.mixedUse());
            buf.writeBoolean(box.needsRescan());
            buf.writeVarInt(box.minX());
            buf.writeVarInt(box.minY());
            buf.writeVarInt(box.minZ());
            buf.writeVarInt(box.maxX());
            buf.writeVarInt(box.maxY());
            buf.writeVarInt(box.maxZ());
        }
    }

    private static CityOverlayPayload read(FriendlyByteBuf buf) {
        boolean own = buf.readBoolean();

        int chunkCount = buf.readVarInt();
        if (chunkCount < 0 || chunkCount > MAX_CHUNKS) {
            throw new IllegalArgumentException("Overlay chunk count out of range: " + chunkCount);
        }
        List<Long> chunks = new ArrayList<>(chunkCount);
        for (int i = 0; i < chunkCount; i++) {
            chunks.add(buf.readLong());
        }

        int buildingCount = buf.readVarInt();
        if (buildingCount < 0 || buildingCount > MAX_BUILDINGS) {
            throw new IllegalArgumentException("Overlay building count out of range: " + buildingCount);
        }
        List<BuildingBox> boxes = new ArrayList<>(buildingCount);
        for (int i = 0; i < buildingCount; i++) {
            boxes.add(new BuildingBox(
                    buf.readUUID(), buf.readUtf(32), buf.readUtf(32),
                    buf.readBoolean(), buf.readBoolean(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
        }
        return new CityOverlayPayload(own, chunks, boxes);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
