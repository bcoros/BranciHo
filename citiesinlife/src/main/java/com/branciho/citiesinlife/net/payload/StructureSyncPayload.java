package com.branciho.citiesinlife.net.payload;

import com.branciho.citiesinlife.CitiesInLife;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Registered structures near the player, so structure mode has something to outline.
 *
 * <p>Scoped to a radius rather than sent whole: a mature city has hundreds of these, and where a
 * rival's buildings are is something you should have to walk over and look at.
 */
public record StructureSyncPayload(List<Entry> structures) implements CustomPacketPayload {

    public static final int MAX_STRUCTURES = 256;

    /** One structure, flattened to exactly what the outline and its label need. */
    public record Entry(
            UUID id,
            String name,
            String typeId,
            int minX, int minY, int minZ,
            int maxX, int maxY, int maxZ,
            int floors,
            int usableCells,
            int residents,
            int jobs
    ) {
    }

    public static final CustomPacketPayload.Type<StructureSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(CitiesInLife.id("structure_sync"));

    public static final StreamCodec<FriendlyByteBuf, StructureSyncPayload> STREAM_CODEC =
            StreamCodec.ofMember(StructureSyncPayload::write, StructureSyncPayload::read);

    private void write(FriendlyByteBuf buf) {
        int count = Math.min(structures.size(), MAX_STRUCTURES);
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            Entry entry = structures.get(i);
            buf.writeUUID(entry.id());
            buf.writeUtf(entry.name(), 48);
            buf.writeUtf(entry.typeId(), 32);
            buf.writeVarInt(entry.minX());
            buf.writeVarInt(entry.minY());
            buf.writeVarInt(entry.minZ());
            buf.writeVarInt(entry.maxX());
            buf.writeVarInt(entry.maxY());
            buf.writeVarInt(entry.maxZ());
            buf.writeVarInt(entry.floors());
            buf.writeVarInt(entry.usableCells());
            buf.writeVarInt(entry.residents());
            buf.writeVarInt(entry.jobs());
        }
    }

    private static StructureSyncPayload read(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_STRUCTURES) {
            throw new IllegalArgumentException("Structure count out of range: " + count);
        }
        List<Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            entries.add(new Entry(
                    buf.readUUID(), buf.readUtf(48), buf.readUtf(32),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
        }
        return new StructureSyncPayload(entries);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
