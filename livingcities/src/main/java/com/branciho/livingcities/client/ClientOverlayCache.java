package com.branciho.livingcities.client;

import com.branciho.livingcities.net.payload.CityOverlayPayload;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.jetbrains.annotations.Nullable;

/**
 * The last overlay snapshot the server sent, plus the lookup structure the renderer needs.
 *
 * <p>The claimed chunks arrive as a list but the renderer asks "is my neighbour claimed?" four times
 * per chunk, every frame. Against a list that is quadratic - about four million comparisons a frame at
 * the packet's cap - so the set is built once here, when the data changes, rather than per frame.
 *
 * <p>Cleared on disconnect: a previous world's borders would otherwise be drawn over the next one.
 */
public final class ClientOverlayCache {

    private static @Nullable CityOverlayPayload overlay;
    private static LongSet claimed = new LongOpenHashSet();

    private ClientOverlayCache() {
    }

    public static @Nullable CityOverlayPayload overlay() {
        return overlay;
    }

    /** True if the player's own city owns this chunk, by {@code ChunkPos.toLong} key. */
    public static boolean claims(long chunkKey) {
        return claimed.contains(chunkKey);
    }

    public static void accept(CityOverlayPayload payload) {
        overlay = payload;
        LongSet rebuilt = new LongOpenHashSet(payload.claimedChunks().size());
        for (long key : payload.claimedChunks()) {
            rebuilt.add(key);
        }
        claimed = rebuilt;
    }

    public static void clear() {
        overlay = null;
        claimed = new LongOpenHashSet();
    }
}
