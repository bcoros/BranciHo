package com.branciho.citiesinlife.net.payload;

import com.branciho.citiesinlife.CitiesInLife;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Everything the control-room screen shows, in one packet.
 *
 * <p>Sent only when somebody clicks a monitor, and again while that screen is open. A reactor's
 * gauges move once every ten seconds, so streaming them to every player in the world would be a
 * great deal of traffic for a number almost nobody is looking at.
 *
 * @param history 8 temperature samples then 8 pressure samples, oldest first
 */
public record ReactorSyncPayload(
        boolean present,
        String name,
        int temperature,
        int targetTemperature,
        int pressure,
        int fuelPercent,
        int minutesLeft,
        int output,
        int dial,
        boolean cooler,
        boolean heat,
        boolean vent,
        int insertion,
        int loopPercent,
        int turbines,
        int columnHeight,
        int rodBlocks,
        boolean melting,
        int fault,
        int faultCount,
        int[] clog,
        int[] history
) implements CustomPacketPayload {

    public static final int TRACE = 16;
    public static final int PORTS = 4;

    public static ReactorSyncPayload none() {
        return new ReactorSyncPayload(false, "", 0, 0, 0, 0, 0, 0, 0, false, false, false, 0, 0,
                0, 0, 0, false, -1, 0, new int[PORTS], new int[TRACE]);
    }

    public static final CustomPacketPayload.Type<ReactorSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(CitiesInLife.id("reactor_sync"));

    public static final StreamCodec<FriendlyByteBuf, ReactorSyncPayload> STREAM_CODEC =
            StreamCodec.ofMember(ReactorSyncPayload::write, ReactorSyncPayload::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeBoolean(present);
        buf.writeUtf(name, 64);
        buf.writeVarInt(temperature);
        buf.writeVarInt(targetTemperature);
        buf.writeVarInt(pressure);
        buf.writeVarInt(fuelPercent);
        buf.writeVarInt(minutesLeft);
        buf.writeVarInt(output);
        buf.writeVarInt(dial);
        buf.writeBoolean(cooler);
        buf.writeBoolean(heat);
        buf.writeBoolean(vent);
        buf.writeVarInt(insertion);
        buf.writeVarInt(loopPercent);
        buf.writeVarInt(turbines);
        buf.writeVarInt(columnHeight);
        buf.writeVarInt(rodBlocks);
        buf.writeBoolean(melting);
        buf.writeVarInt(fault + 1);
        buf.writeVarInt(faultCount);
        for (int i = 0; i < PORTS; i++) {
            buf.writeVarInt(i < clog.length ? clog[i] : 0);
        }
        for (int i = 0; i < TRACE; i++) {
            buf.writeVarInt(i < history.length ? Math.max(0, history[i]) : 0);
        }
    }

    private static ReactorSyncPayload read(FriendlyByteBuf buf) {
        boolean present = buf.readBoolean();
        String name = buf.readUtf(64);
        int temperature = buf.readVarInt();
        int target = buf.readVarInt();
        int pressure = buf.readVarInt();
        int fuel = buf.readVarInt();
        int minutes = buf.readVarInt();
        int output = buf.readVarInt();
        int dial = buf.readVarInt();
        boolean cooler = buf.readBoolean();
        boolean heat = buf.readBoolean();
        boolean vent = buf.readBoolean();
        int insertion = buf.readVarInt();
        int loop = buf.readVarInt();
        int turbines = buf.readVarInt();
        int height = buf.readVarInt();
        int rods = buf.readVarInt();
        boolean melting = buf.readBoolean();
        int fault = buf.readVarInt() - 1;
        int faultCount = buf.readVarInt();
        int[] clog = new int[PORTS];
        for (int i = 0; i < PORTS; i++) {
            clog[i] = buf.readVarInt();
        }
        int[] history = new int[TRACE];
        for (int i = 0; i < TRACE; i++) {
            history[i] = buf.readVarInt();
        }
        return new ReactorSyncPayload(present, name, temperature, target, pressure, fuel, minutes,
                output, dial, cooler, heat, vent, insertion, loop, turbines, height, rods,
                melting, fault, faultCount, clog, history);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
