package com.branciho.livingcities.net.payload;

import com.branciho.livingcities.LivingCities;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Server sends one building's details for the building panel.
 *
 * <p>The floor list is capped rather than trusted to be small. A player can legitimately select a
 * 384-block-tall region, and a pathological build could in principle detect a floor every few blocks;
 * without a cap that becomes an oversized packet and a disconnect. Anything past the cap is summarised
 * by {@link #floorsOmitted()} so the UI can say so honestly instead of silently showing a short list.
 */
public record BuildingDetailPayload(
        UUID buildingId,
        String name,
        boolean mixedUse,
        boolean needsRescan,
        int totalUsableCells,
        int housingCapacity,
        int residents,
        int jobCapacity,
        int workers,
        int floorsOmitted,
        List<FloorRow> floors
) implements CustomPacketPayload {

    /** Hard ceiling on rows sent to a client. Twice any sane storey count for a 384-block selection. */
    public static final int MAX_FLOOR_ROWS = 128;

    private static final int MAX_NAME_LENGTH = 32;
    private static final int MAX_ZONE_ID_LENGTH = 32;

    /** One row of the floor table. Flags travel as a comma list so new flags do not break old clients. */
    public record FloorRow(
            int index,
            int floorY,
            int yMin,
            int yMax,
            int usableCells,
            int ceilingHeight,
            String zoneId,
            String flags,
            int residents,
            int jobs
    ) {
    }

    public static final CustomPacketPayload.Type<BuildingDetailPayload> TYPE =
            new CustomPacketPayload.Type<>(LivingCities.id("building_detail"));

    public static final StreamCodec<FriendlyByteBuf, BuildingDetailPayload> STREAM_CODEC =
            StreamCodec.ofMember(BuildingDetailPayload::write, BuildingDetailPayload::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeUUID(buildingId);
        buf.writeUtf(name, MAX_NAME_LENGTH);
        buf.writeBoolean(mixedUse);
        buf.writeBoolean(needsRescan);
        buf.writeVarInt(totalUsableCells);
        buf.writeVarInt(housingCapacity);
        buf.writeVarInt(residents);
        buf.writeVarInt(jobCapacity);
        buf.writeVarInt(workers);
        buf.writeVarInt(floorsOmitted);

        int count = Math.min(floors.size(), MAX_FLOOR_ROWS);
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            FloorRow row = floors.get(i);
            buf.writeVarInt(row.index());
            buf.writeVarInt(row.floorY());
            buf.writeVarInt(row.yMin());
            buf.writeVarInt(row.yMax());
            buf.writeVarInt(row.usableCells());
            buf.writeVarInt(row.ceilingHeight());
            buf.writeUtf(row.zoneId(), MAX_ZONE_ID_LENGTH);
            buf.writeUtf(row.flags(), 128);
            buf.writeVarInt(row.residents());
            buf.writeVarInt(row.jobs());
        }
    }

    private static BuildingDetailPayload read(FriendlyByteBuf buf) {
        UUID id = buf.readUUID();
        String name = buf.readUtf(MAX_NAME_LENGTH);
        boolean mixed = buf.readBoolean();
        boolean stale = buf.readBoolean();
        int usable = buf.readVarInt();
        int housing = buf.readVarInt();
        int residents = buf.readVarInt();
        int jobs = buf.readVarInt();
        int workers = buf.readVarInt();
        int omitted = buf.readVarInt();

        int count = buf.readVarInt();
        if (count < 0 || count > MAX_FLOOR_ROWS) {
            // A peer claiming more rows than the protocol allows is malformed, not merely verbose.
            throw new IllegalArgumentException("Floor row count out of range: " + count);
        }
        List<FloorRow> rows = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            rows.add(new FloorRow(
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(),
                    buf.readUtf(MAX_ZONE_ID_LENGTH), buf.readUtf(128),
                    buf.readVarInt(), buf.readVarInt()));
        }
        return new BuildingDetailPayload(id, name, mixed, stale, usable, housing, residents, jobs, workers,
                omitted, rows);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
