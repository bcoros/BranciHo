package com.branciho.livingcities.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.AABB;

import java.util.Optional;

/**
 * The two-corner selection stored on a City Planner Tool.
 *
 * <p>Lives as an item data component so the selection travels with the tool, survives relogging, and
 * is naturally per-player without any extra bookkeeping.
 */
public record SelectionData(Optional<BlockPos> pointA, Optional<BlockPos> pointB) {

    public static final SelectionData EMPTY = new SelectionData(Optional.empty(), Optional.empty());

    public static final Codec<SelectionData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BlockPos.CODEC.optionalFieldOf("a").forGetter(SelectionData::pointA),
            BlockPos.CODEC.optionalFieldOf("b").forGetter(SelectionData::pointB)
    ).apply(instance, SelectionData::new));

    public static final StreamCodec<FriendlyByteBuf, SelectionData> STREAM_CODEC =
            StreamCodec.ofMember(SelectionData::write, SelectionData::read);

    private void write(FriendlyByteBuf buf) {
        writeOptional(buf, pointA);
        writeOptional(buf, pointB);
    }

    private static SelectionData read(FriendlyByteBuf buf) {
        return new SelectionData(readOptional(buf), readOptional(buf));
    }

    private static void writeOptional(FriendlyByteBuf buf, Optional<BlockPos> pos) {
        buf.writeBoolean(pos.isPresent());
        pos.ifPresent(buf::writeBlockPos);
    }

    private static Optional<BlockPos> readOptional(FriendlyByteBuf buf) {
        return buf.readBoolean() ? Optional.of(buf.readBlockPos()) : Optional.empty();
    }

    public boolean isComplete() {
        return pointA.isPresent() && pointB.isPresent();
    }

    public SelectionData withA(BlockPos pos) {
        return new SelectionData(Optional.of(pos.immutable()), pointB);
    }

    public SelectionData withB(BlockPos pos) {
        return new SelectionData(pointA, Optional.of(pos.immutable()));
    }

    /** Inclusive block volume of the selection, or 0 when incomplete. */
    public long volume() {
        if (!isComplete()) {
            return 0L;
        }
        BlockPos a = pointA.orElseThrow();
        BlockPos b = pointB.orElseThrow();
        long dx = Math.abs(a.getX() - b.getX()) + 1L;
        long dy = Math.abs(a.getY() - b.getY()) + 1L;
        long dz = Math.abs(a.getZ() - b.getZ()) + 1L;
        return dx * dy * dz;
    }

    public int height() {
        if (!isComplete()) {
            return 0;
        }
        return Math.abs(pointA.orElseThrow().getY() - pointB.orElseThrow().getY()) + 1;
    }

    /** Render/collision box covering the full selected blocks, or null when incomplete. */
    public AABB toBox() {
        if (!isComplete()) {
            return null;
        }
        BlockPos a = pointA.orElseThrow();
        BlockPos b = pointB.orElseThrow();
        return new AABB(
                Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()),
                Math.max(a.getX(), b.getX()) + 1.0D, Math.max(a.getY(), b.getY()) + 1.0D, Math.max(a.getZ(), b.getZ()) + 1.0D);
    }
}
