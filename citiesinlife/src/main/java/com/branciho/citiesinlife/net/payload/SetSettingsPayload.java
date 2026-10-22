package com.branciho.citiesinlife.net.payload;

import com.branciho.citiesinlife.CitiesInLife;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * "Change the settings to these."
 *
 * <p>Its own type rather than sending {@link ModSettingsPayload} back the other way, because one
 * payload registered in both directions is a payload whose handler has to ask which side it is on.
 * Two types with two handlers cannot get that wrong, and the asymmetry is real anyway: what the
 * server sends includes whether you are allowed to edit it, and what the client sends must not.
 */
public record SetSettingsPayload(int citizensPerCity, boolean carsEnabled, int carDistance,
                                 int nuclearBlastPercent, int steamPlumePercent,
                                 boolean opsIgnoreBorders) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SetSettingsPayload> TYPE =
            new CustomPacketPayload.Type<>(CitiesInLife.id("set_settings"));

    public static final StreamCodec<FriendlyByteBuf, SetSettingsPayload> STREAM_CODEC =
            StreamCodec.ofMember(SetSettingsPayload::write, SetSettingsPayload::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(citizensPerCity);
        buf.writeBoolean(carsEnabled);
        buf.writeVarInt(carDistance);
        buf.writeVarInt(nuclearBlastPercent);
        buf.writeVarInt(steamPlumePercent);
        buf.writeBoolean(opsIgnoreBorders);
    }

    private static SetSettingsPayload read(FriendlyByteBuf buf) {
        return new SetSettingsPayload(buf.readVarInt(), buf.readBoolean(), buf.readVarInt(),
                buf.readVarInt(), buf.readVarInt(), buf.readBoolean());
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
