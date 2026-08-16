package com.branciho.citiesinlife.net;

import com.branciho.citiesinlife.net.payload.CitySyncPayload;
import com.branciho.citiesinlife.net.payload.StructureSyncPayload;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The last city and structure snapshot the server sent, for the screens and the world overlay to
 * read.
 *
 * <p>Deliberately imports nothing from {@code net.minecraft.client}. The payload handlers that fill
 * it are registered from common code, so this class gets loaded on a dedicated server too — where it
 * simply sits empty. One client import here and that server would crash on startup.
 *
 * <p>The chunk set is built once per packet rather than searched per frame. Scanning a list of a few
 * thousand claimed chunks for every tile of a map screen is the kind of thing that looks fine until
 * somebody has a real city.
 */
public final class ClientCityCache {

    private static @Nullable CitySyncPayload city;
    private static List<StructureSyncPayload.Entry> structures = List.of();
    private static LongSet claimedChunks = new LongOpenHashSet();

    private ClientCityCache() {
    }

    public static void accept(CitySyncPayload payload) {
        city = payload;
        LongSet chunks = new LongOpenHashSet(payload.claimedChunks().length);
        for (long key : payload.claimedChunks()) {
            chunks.add(key);
        }
        claimedChunks = chunks;
    }

    public static void accept(StructureSyncPayload payload) {
        structures = payload.structures();
    }

    public static @Nullable CitySyncPayload city() {
        return city;
    }

    public static boolean hasCity() {
        return city != null && city.hasCity();
    }

    public static List<StructureSyncPayload.Entry> structures() {
        return structures;
    }

    public static boolean claims(long chunkKey) {
        return claimedChunks.contains(chunkKey);
    }

    public static int claimedCount() {
        return claimedChunks.size();
    }

    /**
     * Clear everything on disconnect.
     *
     * <p>In single player the JVM outlives the world, so without this a player who quits to the menu
     * and opens a different world sees the previous world's city until the first packet arrives.
     */
    public static void clear() {
        city = null;
        structures = List.of();
        claimedChunks = new LongOpenHashSet();
    }
}
