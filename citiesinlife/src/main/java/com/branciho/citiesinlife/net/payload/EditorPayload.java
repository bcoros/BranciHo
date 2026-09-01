package com.branciho.citiesinlife.net.payload;

import com.branciho.citiesinlife.CitiesInLife;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Every building the editor may edit.
 *
 * <p>The player's own city, whole, rather than the radius the outline renderer works from. Editor
 * mode is a list you scroll rather than something you walk around, and a building you cannot see
 * from where you are standing is exactly the one you opened this to find.
 *
 * @param usable whether the editor answered at all — creative mode, and a city of your own. Sent
 *               as a field rather than as an empty list, because "no buildings yet" and "not for
 *               you" are different things the screen says differently.
 */
public record EditorPayload(boolean usable, List<Entry> buildings) implements CustomPacketPayload {

    /**
     * One building, flattened to what the editor shows and edits.
     *
     * <p>{@code residentOverride} and {@code jobOverride} are -1 when the box is still doing the
     * measuring, which is what lets the screen show the measured figure greyed out rather than
     * pretending the player typed it.
     */
    public record Entry(
            UUID id,
            String name,
            String typeId,
            int x, int y, int z,
            int usableCells,
            int residents,
            int jobs,
            int residentOverride,
            int jobOverride,
            int health,
            int maxHealth
    ) {
    }

    /** More than this and the list has stopped being something a person scrolls. */
    public static final int MAX_BUILDINGS = 512;

    public static final int MAX_NAME = 48;

    public static final CustomPacketPayload.Type<EditorPayload> TYPE =
            new CustomPacketPayload.Type<>(CitiesInLife.id("editor"));

    public static final StreamCodec<FriendlyByteBuf, EditorPayload> STREAM_CODEC =
            StreamCodec.ofMember(EditorPayload::write, EditorPayload::read);

    /** What the editor shows before the first packet lands, and when it is not yours to open. */
    public static EditorPayload none() {
        return new EditorPayload(false, List.of());
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeBoolean(usable);
        int count = Math.min(buildings.size(), MAX_BUILDINGS);
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            Entry entry = buildings.get(i);
            buf.writeUUID(entry.id());
            buf.writeUtf(entry.name(), MAX_NAME);
            buf.writeUtf(entry.typeId(), 32);
            buf.writeVarInt(entry.x());
            buf.writeVarInt(entry.y());
            buf.writeVarInt(entry.z());
            buf.writeVarInt(Math.max(0, entry.usableCells()));
            buf.writeVarInt(Math.max(0, entry.residents()));
            buf.writeVarInt(Math.max(0, entry.jobs()));
            buf.writeVarInt(entry.residentOverride());
            buf.writeVarInt(entry.jobOverride());
            buf.writeVarInt(Math.max(0, entry.health()));
            buf.writeVarInt(Math.max(1, entry.maxHealth()));
        }
    }

    private static EditorPayload read(FriendlyByteBuf buf) {
        boolean usable = buf.readBoolean();
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_BUILDINGS) {
            throw new IllegalArgumentException("Bad editor building count: " + count);
        }
        List<Entry> buildings = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            buildings.add(new Entry(
                    buf.readUUID(), buf.readUtf(MAX_NAME), buf.readUtf(32),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt()));
        }
        return new EditorPayload(usable, buildings);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
