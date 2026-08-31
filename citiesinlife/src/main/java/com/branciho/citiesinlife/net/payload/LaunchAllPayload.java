package com.branciho.citiesinlife.net.payload;

import com.branciho.citiesinlife.CitiesInLife;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Empty every silo in the city onto one chunk.
 *
 * <p>No silo is named, because the point of the button is that the player is not choosing: the
 * server walks the city's own silos. Sending a list of ids from the client would let a modified one
 * nominate somebody else's silo, and the server would have to check every id against ownership
 * anyway — so it may as well be the one deciding which silos exist.
 *
 * @param chunkX the target chunk
 * @param chunkZ the target chunk
 * @param kindId which missile to send, by string id
 */
public record LaunchAllPayload(int chunkX, int chunkZ, String kindId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<LaunchAllPayload> TYPE =
            new CustomPacketPayload.Type<>(CitiesInLife.id("launch_all"));

    public static final StreamCodec<FriendlyByteBuf, LaunchAllPayload> STREAM_CODEC =
            StreamCodec.ofMember(LaunchAllPayload::write, LaunchAllPayload::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(chunkX);
        buf.writeVarInt(chunkZ);
        buf.writeUtf(kindId, 32);
    }

    private static LaunchAllPayload read(FriendlyByteBuf buf) {
        return new LaunchAllPayload(buf.readVarInt(), buf.readVarInt(), buf.readUtf(32));
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
