package com.branciho.citiesinlife.net.payload;

import com.branciho.citiesinlife.CitiesInLife;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

/**
 * "Fire that one, at there."
 *
 * <p>A request, like every other packet the client sends. Which silo, which chunk and which
 * warhead is all it says; whether any of that is allowed is decided entirely on the server, which
 * is where it has to be decided — the map greys out illegal targets as a courtesy, and a courtesy
 * is not a rule.
 */
public record LaunchMissilePayload(UUID siloId, int chunkX, int chunkZ, String kindId)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<LaunchMissilePayload> TYPE =
            new CustomPacketPayload.Type<>(CitiesInLife.id("launch_missile"));

    public static final StreamCodec<FriendlyByteBuf, LaunchMissilePayload> STREAM_CODEC =
            StreamCodec.ofMember(LaunchMissilePayload::write, LaunchMissilePayload::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeUUID(siloId);
        buf.writeVarInt(chunkX);
        buf.writeVarInt(chunkZ);
        buf.writeUtf(kindId, 32);
    }

    private static LaunchMissilePayload read(FriendlyByteBuf buf) {
        return new LaunchMissilePayload(buf.readUUID(), buf.readVarInt(), buf.readVarInt(),
                buf.readUtf(32));
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
