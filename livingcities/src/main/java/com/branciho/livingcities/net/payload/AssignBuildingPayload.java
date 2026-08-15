package com.branciho.livingcities.net.payload;

import com.branciho.livingcities.LivingCities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client asks the server to register the current planner selection as a building.
 *
 * <p>The corners come from the client because that is where the selection lives, but they are only a
 * request: the server re-checks reach, territory ownership, volume limits and overlap before anything
 * is created. An empty name means "pick one for me".
 */
public record AssignBuildingPayload(BlockPos cornerA, BlockPos cornerB, String name) implements CustomPacketPayload {

    public static final int MAX_NAME_LENGTH = 32;

    public static final CustomPacketPayload.Type<AssignBuildingPayload> TYPE =
            new CustomPacketPayload.Type<>(LivingCities.id("assign_building"));

    public static final StreamCodec<FriendlyByteBuf, AssignBuildingPayload> STREAM_CODEC =
            StreamCodec.ofMember(AssignBuildingPayload::write, AssignBuildingPayload::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(cornerA);
        buf.writeBlockPos(cornerB);
        buf.writeUtf(name, MAX_NAME_LENGTH);
    }

    private static AssignBuildingPayload read(FriendlyByteBuf buf) {
        return new AssignBuildingPayload(buf.readBlockPos(), buf.readBlockPos(), buf.readUtf(MAX_NAME_LENGTH));
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
