package com.branciho.citiesinlife.net;

import net.minecraft.core.BlockPos;
import com.branciho.citiesinlife.net.payload.CitySyncPayload;
import com.branciho.citiesinlife.net.payload.ReactorSyncPayload;
import com.branciho.citiesinlife.net.payload.ConfirmDeleteCityPayload;
import com.branciho.citiesinlife.net.payload.ForeignLandPayload;
import com.branciho.citiesinlife.net.payload.NeighbourCitiesPayload;
import com.branciho.citiesinlife.net.payload.PathSyncPayload;
import com.branciho.citiesinlife.net.payload.RoadSyncPayload;
import com.branciho.citiesinlife.net.payload.PowerLinesPayload;
import com.branciho.citiesinlife.net.payload.StructureSyncPayload;
import com.branciho.citiesinlife.net.payload.WaterLinesPayload;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
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
    private static List<long[]> powerLines = List.of();
    private static List<long[]> waterLines = List.of();
    private static LongSet claimedChunks = new LongOpenHashSet();
    private static long[] paths = new long[0];
    private static long[] roadTiles = new long[0];
    private static int[] roadFlags = new int[0];
    private static List<NeighbourCitiesPayload.Entry> neighbours = List.of();
    private static Long2ByteOpenHashMap foreignLand = new Long2ByteOpenHashMap();
    private static @Nullable ConfirmDeleteCityPayload pendingDeleteConfirm;

    private ClientCityCache() {
    }

    /**
     * The reactor the open monitor is showing.
     *
     * <p>Kept here with everything else the client is told rather than on the screen, so a screen
     * that is closed and reopened does not flash empty while it waits for the first packet.
     */
    private static ReactorSyncPayload reactor = ReactorSyncPayload.none();

    /** Set when the server wants a control-room screen opened, cleared by the client tick. */
    private static @Nullable BlockPos pendingMonitor;

    public static void accept(ReactorSyncPayload payload) {
        reactor = payload;
    }

    public static ReactorSyncPayload reactor() {
        return reactor;
    }

    public static void openMonitor(BlockPos at) {
        pendingMonitor = at;
    }

    public static @Nullable BlockPos takeMonitor() {
        BlockPos taken = pendingMonitor;
        pendingMonitor = null;
        return taken;
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

    public static void accept(PowerLinesPayload payload) {
        powerLines = payload.lines();
    }

    public static void accept(WaterLinesPayload payload) {
        waterLines = payload.lines();
    }

    public static void accept(PathSyncPayload payload) {
        paths = payload.marked();
    }

    /** Road and its direction flags, kept as two parallel arrays exactly as they arrived. */
    public static void accept(RoadSyncPayload payload) {
        roadTiles = payload.tiles();
        roadFlags = payload.flags();
    }

    public static void accept(NeighbourCitiesPayload payload) {
        neighbours = payload.cities();
    }

    /**
     * Foreign territory, indexed rather than searched.
     *
     * <p>The map paints four hundred tiles a frame and asks about each one. A list would turn that
     * into a scan of every claimed chunk on the server, per tile, per frame.
     */
    public static void accept(ForeignLandPayload payload) {
        Long2ByteOpenHashMap land = new Long2ByteOpenHashMap(payload.chunks().length);
        land.defaultReturnValue((byte) -1);
        for (int i = 0; i < payload.chunks().length && i < payload.stances().length; i++) {
            land.put(payload.chunks()[i], payload.stances()[i]);
        }
        foreignLand = land;
    }

    public static List<NeighbourCitiesPayload.Entry> neighbours() {
        return neighbours;
    }

    /** The stance of whoever owns this chunk towards you, or -1 if nobody foreign owns it. */
    public static int foreignStance(long chunkKey) {
        return foreignLand.get(chunkKey);
    }

    /** The pavement near the player, drawn only in structure mode or with the path tool in hand. */
    public static long[] paths() {
        return paths;
    }

    /** Pipe links near the player. Drawn only while the pipe tool is held; invisible otherwise. */
    public static List<long[]> waterLines() {
        return waterLines;
    }

    /**
     * Park a confirmation request until the client can act on it.
     *
     * <p>Opening a screen means touching client-only classes, and this class is deliberately free of
     * them so that registering its handlers does not drag the client onto a dedicated server. So the
     * request is left here and the client tick picks it up.
     */
    public static void accept(ConfirmDeleteCityPayload payload) {
        pendingDeleteConfirm = payload;
    }

    /** Take the pending confirmation request, if there is one. */
    public static @Nullable ConfirmDeleteCityPayload takeDeleteConfirm() {
        ConfirmDeleteCityPayload pending = pendingDeleteConfirm;
        pendingDeleteConfirm = null;
        return pending;
    }

    /** Power lines near the player, as pairs of packed positions. */
    public static List<long[]> powerLines() {
        return powerLines;
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

    public static long[] roadTiles() {
        return roadTiles;
    }

    public static int[] roadFlags() {
        return roadFlags;
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
        powerLines = List.of();
        waterLines = List.of();
        claimedChunks = new LongOpenHashSet();
        paths = new long[0];
        roadTiles = new long[0];
        roadFlags = new int[0];
        neighbours = List.of();
        foreignLand = new Long2ByteOpenHashMap();
        pendingDeleteConfirm = null;
    }
}
