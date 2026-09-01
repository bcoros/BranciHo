package com.branciho.citiesinlife.net.payload;

import com.branciho.citiesinlife.CitiesInLife;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** "Upgrade that machine." One position; everything else the server works out for itself. */
public record UpgradePayload(BlockPos pos) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<UpgradePayload> TYPE =
            new CustomPacketPayload.Type<>(CitiesInLife.id("upgrade"));

    public static final StreamCodec<FriendlyByteBuf, UpgradePayload> STREAM_CODEC =
            StreamCodec.ofMember(UpgradePayload::write, UpgradePayload::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
    }

    private static UpgradePayload read(FriendlyByteBuf buf) {
        return new UpgradePayload(buf.readBlockPos());
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
