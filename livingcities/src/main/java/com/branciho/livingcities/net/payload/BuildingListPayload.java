package com.branciho.livingcities.net.payload;

import com.branciho.livingcities.LivingCities;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One page of a city's buildings, for the management screen's Buildings tab.
 *
 * <p>Paged rather than sent whole: a mature city is meant to reach hundreds of buildings, and the
 * screen can only show a dozen rows at a time. Sending the rest would be a large packet nobody reads.
 */
public record BuildingListPayload(
        int page,
        int pageCount,
        int totalBuildings,
        List<Row> rows
) implements CustomPacketPayload {

    /** Rows per page. Matches what the screen draws, so paging never shows a partial screen. */
    public static final int PAGE_SIZE = 12;

    public record Row(
            UUID id,
            String name,
            String zoneId,
            boolean mixedUse,
            boolean needsRescan,
            int residents,
            int housingCapacity,
            int workers,
            int jobCapacity,
            int x, int y, int z
    ) {
    }

    public static final CustomPacketPayload.Type<BuildingListPayload> TYPE =
            new CustomPacketPayload.Type<>(LivingCities.id("building_list"));

    public static final StreamCodec<FriendlyByteBuf, BuildingListPayload> STREAM_CODEC =
            StreamCodec.ofMember(BuildingListPayload::write, BuildingListPayload::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(page);
        buf.writeVarInt(pageCount);
        buf.writeVarInt(totalBuildings);

        int count = Math.min(rows.size(), PAGE_SIZE);
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            Row row = rows.get(i);
            buf.writeUUID(row.id());
            buf.writeUtf(row.name(), 32);
            buf.writeUtf(row.zoneId(), 32);
            buf.writeBoolean(row.mixedUse());
            buf.writeBoolean(row.needsRescan());
            buf.writeVarInt(row.residents());
            buf.writeVarInt(row.housingCapacity());
            buf.writeVarInt(row.workers());
            buf.writeVarInt(row.jobCapacity());
            buf.writeVarInt(row.x());
            buf.writeVarInt(row.y());
            buf.writeVarInt(row.z());
        }
    }

    private static BuildingListPayload read(FriendlyByteBuf buf) {
        int page = buf.readVarInt();
        int pageCount = buf.readVarInt();
        int total = buf.readVarInt();

        int count = buf.readVarInt();
        if (count < 0 || count > PAGE_SIZE) {
            throw new IllegalArgumentException("Building list row count out of range: " + count);
        }
        List<Row> rows = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            rows.add(new Row(
                    buf.readUUID(), buf.readUtf(32), buf.readUtf(32),
                    buf.readBoolean(), buf.readBoolean(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
        }
        return new BuildingListPayload(page, pageCount, total, rows);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
