package com.branciho.livingcities.net.payload;

import com.branciho.livingcities.LivingCities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server tells the client which city screen to open.
 *
 * <p>The client never decides on its own that a city exists; it opens what the server tells it to.
 */
public record OpenCityScreenPayload(Screen screen, BlockPos context) implements CustomPacketPayload {

    public enum Screen {
        /** No city here yet: offer the "found a city" prompt. */
        CREATE_CITY,
        /** A city exists and the player may manage it. */
        MANAGEMENT
    }

    private static final Screen[] SCREENS = Screen.values();

    public static final CustomPacketPayload.Type<OpenCityScreenPayload> TYPE =
            new CustomPacketPayload.Type<>(LivingCities.id("open_city_screen"));

    public static final StreamCodec<FriendlyByteBuf, OpenCityScreenPayload> STREAM_CODEC =
            StreamCodec.ofMember(OpenCityScreenPayload::write, OpenCityScreenPayload::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(screen.ordinal());
        buf.writeBlockPos(context);
    }

    private static OpenCityScreenPayload read(FriendlyByteBuf buf) {
        int ordinal = buf.readVarInt();
        Screen screen = ordinal >= 0 && ordinal < SCREENS.length ? SCREENS[ordinal] : Screen.MANAGEMENT;
        return new OpenCityScreenPayload(screen, buf.readBlockPos());
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
