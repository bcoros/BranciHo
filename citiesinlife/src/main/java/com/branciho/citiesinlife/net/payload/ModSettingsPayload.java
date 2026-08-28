package com.branciho.citiesinlife.net.payload;

import com.branciho.citiesinlife.CitiesInLife;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The mod's settings, in both directions.
 *
 * <p>Sent to a player when they join so their settings screen can show the real values, and sent
 * back by the world's owner when they change one. The same record either way, because a settings
 * screen that shows one set of numbers and edits another is how you end up turning something off
 * twice.
 *
 * @param editable whether the receiving player is allowed to change any of this. Decided by the
 *                 server and sent rather than worked out on the client, because the client does not
 *                 know who owns the world and must not be the thing that decides.
 */
public record ModSettingsPayload(int citizensPerCity, boolean carsEnabled, int carDistance,
                                 int nuclearBlastPercent, int steamPlumePercent,
                                 boolean opsIgnoreBorders, boolean editable)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ModSettingsPayload> TYPE =
            new CustomPacketPayload.Type<>(CitiesInLife.id("mod_settings"));

    public static final StreamCodec<FriendlyByteBuf, ModSettingsPayload> STREAM_CODEC =
            StreamCodec.ofMember(ModSettingsPayload::write, ModSettingsPayload::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(citizensPerCity);
        buf.writeBoolean(carsEnabled);
        buf.writeVarInt(carDistance);
        buf.writeVarInt(nuclearBlastPercent);
        buf.writeVarInt(steamPlumePercent);
        buf.writeBoolean(opsIgnoreBorders);
        buf.writeBoolean(editable);
    }

    private static ModSettingsPayload read(FriendlyByteBuf buf) {
        return new ModSettingsPayload(buf.readVarInt(), buf.readBoolean(), buf.readVarInt(),
                buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readBoolean());
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
