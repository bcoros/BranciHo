package com.branciho.citiesinlife.net.payload;

import com.branciho.citiesinlife.CitiesInLife;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The city's army as the Military Tool needs to show it.
 *
 * <p>Weapons come across as a display name rather than as an item, because the whole point of
 * letting a soldier carry anything is that it might be from a mod this build knows nothing about.
 * A name always renders; a missing item does not.
 */
public record ArmySyncPayload(
        boolean hasBase,
        long treasury,
        long hireCost,
        long trainCost,
        int maxArmy,
        List<Entry> soldiers
) implements CustomPacketPayload {

    /** @param secondsLeft how long is left on a course, or 0 if they are not on one */
    public record Entry(UUID id, String name, int training, String weapon, int secondsLeft) {
    }

    private static final int MAX_SOLDIERS = 64;

    public static ArmySyncPayload none() {
        return new ArmySyncPayload(false, 0L, 0L, 0L, 0, List.of());
    }

    public static final CustomPacketPayload.Type<ArmySyncPayload> TYPE =
            new CustomPacketPayload.Type<>(CitiesInLife.id("army_sync"));

    public static final StreamCodec<FriendlyByteBuf, ArmySyncPayload> STREAM_CODEC =
            StreamCodec.ofMember(ArmySyncPayload::write, ArmySyncPayload::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeBoolean(hasBase);
        buf.writeLong(treasury);
        buf.writeLong(hireCost);
        buf.writeLong(trainCost);
        buf.writeVarInt(maxArmy);
        int count = Math.min(soldiers.size(), MAX_SOLDIERS);
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            Entry entry = soldiers.get(i);
            buf.writeUUID(entry.id());
            buf.writeUtf(entry.name(), 32);
            buf.writeVarInt(entry.training());
            buf.writeUtf(entry.weapon(), 64);
            buf.writeVarInt(entry.secondsLeft());
        }
    }

    private static ArmySyncPayload read(FriendlyByteBuf buf) {
        boolean hasBase = buf.readBoolean();
        long treasury = buf.readLong();
        long hireCost = buf.readLong();
        long trainCost = buf.readLong();
        int maxArmy = buf.readVarInt();
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_SOLDIERS) {
            throw new IllegalArgumentException("Army size out of range: " + count);
        }
        List<Entry> soldiers = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            soldiers.add(new Entry(buf.readUUID(), buf.readUtf(32), buf.readVarInt(),
                    buf.readUtf(64), buf.readVarInt()));
        }
        return new ArmySyncPayload(hasBase, treasury, hireCost, trainCost, maxArmy, soldiers);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
