package com.branciho.citiesinlife.net;

import com.branciho.citiesinlife.net.payload.EditorPayload;
import net.minecraft.core.BlockPos;
import com.branciho.citiesinlife.net.payload.CityHallPayload;
import com.branciho.citiesinlife.net.payload.HologramPayload;
import com.branciho.citiesinlife.net.payload.CitySyncPayload;
import com.branciho.citiesinlife.net.payload.MeetingInvitePayload;
import com.branciho.citiesinlife.net.payload.ReactorSyncPayload;
import com.branciho.citiesinlife.net.payload.CallToArmsPayload;
import com.branciho.citiesinlife.net.payload.ModSettingsPayload;
import com.branciho.citiesinlife.net.payload.PeaceOfferPayload;
import com.branciho.citiesinlife.net.payload.RadiationPayload;
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
import com.branciho.citiesinlife.net.payload.MissileMapPayload;

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
    private static @Nullable CallToArmsPayload pendingCallToArms;
    private static @Nullable PeaceOfferPayload pendingPeaceOffer;
    private static @Nullable ModSettingsPayload settings;
    private static int radiation;

    /**
     * The strategic picture, or null until the missile map has asked for one.
     *
     * <p>Kept whole rather than unpacked into indexes, because unlike the land map's chunk lookups
     * every part of this is walked once per frame anyway - there are a handful of silos, a handful
     * of cities and almost never a missile in the air.
     */
    private static @Nullable MissileMapPayload missileMap;

    /**
     * The city hall panel's state, and a counter bumped on every packet.
     *
     * <p>The counter is what lets an open panel notice that a meeting filled up or that the player
     * walked out of the hall: the screen compares it each tick and rebuilds its buttons when it
     * moves, the same way the military screen already tracks its army.
     */
    private static CityHallPayload cityHall = CityHallPayload.none();
    private static int cityHallRevision;

    private static HologramPayload hologram = HologramPayload.none();
    private static int hologramRevision;

    /**
     * The editor's list, and a counter that ticks every time a fresh one lands.
     *
     * <p>The counter is what an open editor watches: the list is replaced silently by the packet
     * handler, so without it a screen that asked for a refresh has no way of noticing the answer.
     */
    private static EditorPayload editor = EditorPayload.none();
    private static int editorRevision;

    /** A meeting invitation, waiting to be turned into a question on screen. */
    private static @Nullable MeetingInvitePayload pendingMeetingInvite;

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

    /** Consume-once, like the monitor's: the client tick picks it up and opens the panel. */
    private static boolean hologramWanted;

    public static void openHologram() {
        hologramWanted = true;
    }

    public static boolean takeHologram() {
        boolean wanted = hologramWanted;
        hologramWanted = false;
        return wanted;
    }

    /** The same consume-once hand-off for the launch button in the hall. */
    private static boolean launchAllWanted;

    public static void openLaunchAll() {
        launchAllWanted = true;
    }

    public static boolean takeLaunchAll() {
        boolean wanted = launchAllWanted;
        launchAllWanted = false;
        return wanted;
    }

    /** And the same again for the editor, which Shift+V asks the server to open. */
    private static boolean editorWanted;

    public static void openEditor() {
        editorWanted = true;
    }

    public static boolean takeEditor() {
        boolean wanted = editorWanted;
        editorWanted = false;
        return wanted;
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

    public static void accept(ModSettingsPayload payload) {
        settings = payload;
    }

    /** How much fallout the player is standing in. Held here so the tick and the HUD agree. */
    public static void accept(RadiationPayload payload) {
        radiation = payload.strength();
    }

    public static void accept(MissileMapPayload payload) {
        missileMap = payload;
    }

    public static @Nullable MissileMapPayload missileMap() {
        return missileMap;
    }

    public static void accept(CityHallPayload payload) {
        // Only counted as a change when something ACTUALLY changed. The city hall screen rebuilds
        // its widgets whenever this number moves, and rebuilding re-runs its init, which asks the
        // server again - so bumping on every packet made the screen and the server chase each other
        // round a loop as fast as the connection allowed, and tore the half-typed announcement out
        // of the text box several times a second.
        if (payload.equals(cityHall)) {
            return;
        }
        cityHall = payload;
        cityHallRevision++;
    }

    /** Same guard as the city hall's: only a real change bumps the revision. */
    public static void accept(HologramPayload payload) {
        if (payload.equals(hologram)) {
            return;
        }
        hologram = payload;
        hologramRevision++;
    }

    public static HologramPayload hologram() {
        return hologram;
    }

    public static void accept(EditorPayload payload) {
        editor = payload;
        editorRevision++;
    }

    public static EditorPayload editor() {
        return editor;
    }

    public static int editorRevision() {
        return editorRevision;
    }

    public static int hologramRevision() {
        return hologramRevision;
    }

    public static CityHallPayload cityHall() {
        return cityHall;
    }

    public static int cityHallRevision() {
        return cityHallRevision;
    }

    /** A meeting somewhere, arriving as a question. Same hand-off as the call to arms. */
    public static void accept(MeetingInvitePayload payload) {
        pendingMeetingInvite = payload;
    }

    public static @Nullable MeetingInvitePayload takeMeetingInvite() {
        MeetingInvitePayload pending = pendingMeetingInvite;
        pendingMeetingInvite = null;
        return pending;
    }

    public static int radiation() {
        return radiation;
    }

    /** The mod's settings as the server last described them, or null before they have arrived. */
    public static @Nullable ModSettingsPayload settings() {
        return settings;
    }

    /** An ally's war, arriving as a question. Same hand-off as the delete confirmation. */
    public static void accept(CallToArmsPayload payload) {
        pendingCallToArms = payload;
    }

    /** A treaty on the table, arriving as a question. */
    public static void accept(PeaceOfferPayload payload) {
        pendingPeaceOffer = payload;
    }

    public static @Nullable PeaceOfferPayload takePeaceOffer() {
        PeaceOfferPayload pending = pendingPeaceOffer;
        pendingPeaceOffer = null;
        return pending;
    }

    public static @Nullable CallToArmsPayload takeCallToArms() {
        CallToArmsPayload pending = pendingCallToArms;
        pendingCallToArms = null;
        return pending;
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
        pendingCallToArms = null;
        pendingPeaceOffer = null;
        settings = null;
        radiation = 0;
        missileMap = null;
        cityHall = CityHallPayload.none();
        hologram = HologramPayload.none();
        cityHallRevision++;
        pendingMeetingInvite = null;
    }
}
