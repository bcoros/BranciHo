package com.branciho.citiesinlife.net.payload;

import com.branciho.citiesinlife.CitiesInLife;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

/**
 * Empty every silo in the city over an enemy's territory.
 *
 * <p>A city is named rather than a chunk, and that is the whole of the change. Aiming the volley at
 * one chunk meant every rocket you owned arrived in the same crater, which is a waste of eight
 * missiles and looks like a bug — so the target is a <em>country</em> now and each silo picks its
 * own square of it. A strategic strike should land across a city, not on one house in it.
 *
 * <p>No silo is named either, for the reason it never was: the point of the button is that the
 * player is not choosing, so the server walks the city's own silos. A list of ids from the client
 * would let a modified one nominate somebody else's.
 *
 * @param cityId the city being struck — checked against the war table on arrival
 * @param kindId which missile to send, by string id
 */
public record LaunchAllPayload(UUID cityId, String kindId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<LaunchAllPayload> TYPE =
            new CustomPacketPayload.Type<>(CitiesInLife.id("launch_all"));

    public static final StreamCodec<FriendlyByteBuf, LaunchAllPayload> STREAM_CODEC =
            StreamCodec.ofMember(LaunchAllPayload::write, LaunchAllPayload::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeUUID(cityId);
        buf.writeUtf(kindId, 32);
    }

    private static LaunchAllPayload read(FriendlyByteBuf buf) {
        return new LaunchAllPayload(buf.readUUID(), buf.readUtf(32));
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
