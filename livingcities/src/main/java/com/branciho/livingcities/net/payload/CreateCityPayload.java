package com.branciho.livingcities.net.payload;

import com.branciho.livingcities.LivingCities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client asks the server to found a city at a city hall core.
 *
 * <p>Everything here is a <em>request</em>. The server re-validates the position, the block, the
 * player's reach, the name, ownership and cost before anything happens.
 */
public record CreateCityPayload(BlockPos corePos, String cityName) implements CustomPacketPayload {

    public static final int MAX_NAME_LENGTH = 32;

    public static final CustomPacketPayload.Type<CreateCityPayload> TYPE =
            new CustomPacketPayload.Type<>(LivingCities.id("create_city"));

    public static final StreamCodec<FriendlyByteBuf, CreateCityPayload> STREAM_CODEC =
            StreamCodec.ofMember(CreateCityPayload::write, CreateCityPayload::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(corePos);
        buf.writeUtf(cityName, MAX_NAME_LENGTH);
    }

    private static CreateCityPayload read(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        String name = buf.readUtf(MAX_NAME_LENGTH);
        return new CreateCityPayload(pos, name);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
