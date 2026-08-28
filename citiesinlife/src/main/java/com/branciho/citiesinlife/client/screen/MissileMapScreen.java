package com.branciho.citiesinlife.client.screen;

import com.branciho.citiesinlife.missile.MissileKind;
import com.branciho.citiesinlife.net.CitiesInLifeNetwork;
import com.branciho.citiesinlife.net.ClientCityCache;
import com.branciho.citiesinlife.net.payload.LaunchMissilePayload;
import com.branciho.citiesinlife.net.payload.MissileMapPayload;
import com.branciho.citiesinlife.net.payload.RequestMissileMapPayload;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;
import net.minecraft.util.Mth;

/**
 * The missile map: whose land is whose, where your silos are, and what is currently in the air.
 *
 * <p>Deliberately not the land map. That one is about the ground under your feet — it follows the
 * player, it stops at twelve chunks, and everything past that is fog because the client is never
 * sent any more than that. A weapon whose entire point is reaching somewhere you are <em>not</em>
 * is useless on a map like that, so this one gets its own packet with the strategic picture in it
 * and <b>pans</b> rather than following you around.
 *
 * <p>Three columns. Your silos down the left, because a launch starts with choosing which one; the
 * map in the middle; the cities you know about down the right, so reaching an enemy two thousand
 * blocks away is one click rather than a hundred presses of an arrow key.
 *
 * <p>Terrain is drawn wherever the client happens to have the chunk and left dark where it does
 * not. Territory is drawn either way — that comes from the server and is the part that matters
 * when you are aiming at somewhere you have never been.
 */
public class MissileMapScreen extends Screen {

    /**
     * The panel is measured to the screen rather than declared.
     *
     * <p>It used to be fixed at four hundred and ninety-three pixels wide, which is a perfectly
     * ordinary number until you remember that Minecraft's GUI is drawn at a scaled resolution:
     * at the default GUI scale on a 1080p monitor the whole screen is six hundred and forty wide,
     * and at the next scale up it is four hundred and eighty. So the panel was wider than the
     * screen, and the silo list — which lives in the left margin — was off the edge of it
     * entirely. That is why a silo appeared to be holding nothing: the numbers were there, just
     * not on the monitor.
     *
     * <p>Everything below is now derived in {@link #init()} from {@code this.width} and
     * {@code this.height}, so it fits at any scale and simply shows fewer chunks when there is
     * less room.
     */
    private static final int MAX_WIDTH = 420;
    private static final int MAX_HEIGHT = 262;

    /** How many tiles across the map aims for, before the space available has its say. */
    private static final int TARGET_TILES = 25;

    private static final int PADDING = 8;
    private static final int HEADER = 26;
    private static final int FOOTER = 50;

    private int radius;
    private int tile;
    private int grid;
    private int sidebar;

    /** How far one press of an arrow key or one drag step moves the view. */
    private static final int PAN_STEP = 4;

    /** Re-asked while the screen is open, because a track in the air goes stale in seconds. */
    private static final int REFRESH_TICKS = 20;

    private static final int COLOUR_UNLOADED = 0xFF11151C;
    private static final int COLOUR_GRID = 0x22000000;
    private static final int COLOUR_HOVER = 0x66FFFFFF;
    private static final int COLOUR_SILO = 0xFFFFD86A;
    private static final int COLOUR_SELECTED = 0xFFFFFFFF;
    private static final int COLOUR_TRACK = 0xFFE0452F;
    private static final int COLOUR_TRACK_MINE = 0xFF59A6FF;

    /** Mine, neutral, allied, at war — in the order the payload's bytes use. */
    private static final int[] RELATION_COLOUR = {0xFF66E576, 0xFF9AA3AF, 0xFF4DD9E6, 0xFFE0452F};

    private final Long2IntOpenHashMap terrainCache = new Long2IntOpenHashMap();

    /** Territory, indexed once when the packet lands rather than searched per tile per frame. */
    private Long2ByteOpenHashMap territory = new Long2ByteOpenHashMap();

    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;
    private int gridLeft;
    private int gridTop;

    private int centreX;
    private int centreZ;
    private boolean centred;

    private int selectedSilo;
    private MissileKind kind = MissileKind.BALLISTIC;

    private int untilRefresh;
    private @Nullable Component status;

    public MissileMapScreen() {
        super(Component.translatable("screen.citiesinlife.missile_map"));
        terrainCache.defaultReturnValue(0);
        territory.defaultReturnValue((byte) -1);
    }

    @Override
    protected void init() {
        measure();
        panelLeft = (this.width - panelWidth) / 2;
        panelTop = (this.height - panelHeight) / 2;
        gridLeft = panelLeft + PADDING + sidebar;
        gridTop = panelTop + HEADER;

        if (!centred) {
            follow();
            centred = true;
        }
        CitiesInLifeNetwork.sendToServer(new RequestMissileMapPayload());
        index();

        int y = panelTop + panelHeight - 46;
        addRenderableWidget(Button.builder(
                        kind.displayName(),
                        button -> {
                            kind = kind == MissileKind.BALLISTIC
                                    ? MissileKind.NUCLEAR : MissileKind.BALLISTIC;
                            rebuildWidgets();
                        })
                .bounds(panelLeft + PADDING, y, sidebar, 18)
                .build());

        addRenderableWidget(Button.builder(
                        Component.translatable("screen.citiesinlife.back"),
                        button -> this.minecraft.setScreen(new CityScreen()))
                .bounds(panelLeft + PADDING, panelTop + panelHeight - 26, sidebar, 18)
                .build());

        addRenderableWidget(Button.builder(
                        Component.translatable("gui.done"), button -> this.onClose())
                .bounds(panelLeft + panelWidth - PADDING - sidebar, panelTop + panelHeight - 26,
                        sidebar, 18)
                .build());
    }

    /**
     * Work out how big everything can be on the screen we actually have.
     *
     * <p>The two sidebars get a share of the width and are clamped so a name is still readable;
     * whatever is left over, in the smaller of the two directions, is the map. The tile size then
     * falls out of how many chunks will fit, floored at four pixels — below that a chunk is not a
     * thing you can click.
     */
    private void measure() {
        int roomWidth = Math.min(this.width - 16, MAX_WIDTH);
        int roomHeight = Math.min(this.height - 16, MAX_HEIGHT);
        sidebar = Mth.clamp((roomWidth - PADDING * 2) / 5, 52, 92);

        int acrossWidth = roomWidth - PADDING * 2 - sidebar * 2;
        int acrossHeight = roomHeight - HEADER - FOOTER;
        int across = Math.max(40, Math.min(acrossWidth, acrossHeight));

        tile = Mth.clamp(across / TARGET_TILES, 4, 9);
        // An odd number of tiles, so there is a middle one for the view to be centred on.
        radius = Math.max(4, (across / tile - 1) / 2);
        grid = radius * 2 + 1;

        panelWidth = grid * tile + PADDING * 2 + sidebar * 2;
        panelHeight = grid * tile + HEADER + FOOTER;
    }

    private void follow() {
        if (this.minecraft != null && this.minecraft.player != null) {
            centreX = this.minecraft.player.chunkPosition().x;
            centreZ = this.minecraft.player.chunkPosition().z;
        }
    }

    /** Flatten the territory list into something a per-tile lookup can afford. */
    private void index() {
        MissileMapPayload map = ClientCityCache.missileMap();
        Long2ByteOpenHashMap next = new Long2ByteOpenHashMap();
        next.defaultReturnValue((byte) -1);
        if (map != null) {
            for (MissileMapPayload.Land land : map.land()) {
                next.put(land.chunkKey(), land.relation());
            }
        }
        territory = next;
    }

    @Override
    public void tick() {
        super.tick();
        if (--untilRefresh > 0) {
            return;
        }
        untilRefresh = REFRESH_TICKS;
        CitiesInLifeNetwork.sendToServer(new RequestMissileMapPayload());
        index();
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        CityScreen.softDim(graphics, this);
        CityScreen.panel(graphics, panelLeft, panelTop, panelWidth, panelHeight);

        graphics.drawString(this.font, this.title, panelLeft + PADDING, panelTop + 10,
                CityScreen.COLOUR_TEXT, false);

        MissileMapPayload map = ClientCityCache.missileMap();
        drawSilos(graphics, map, mouseX, mouseY);
        drawGrid(graphics, map, mouseX, mouseY);
        drawPlaces(graphics, map, mouseX, mouseY);
        drawFooter(graphics, map);
    }

    private void drawSilos(GuiGraphics graphics, @Nullable MissileMapPayload map,
                          int mouseX, int mouseY) {
        int x = panelLeft + PADDING;
        int y = panelTop + HEADER;
        graphics.drawString(this.font, Component.translatable("screen.citiesinlife.your_silos"),
                x, y, CityScreen.COLOUR_ACCENT, false);
        y += 12;
        if (map == null || map.silos().isEmpty()) {
            graphics.drawString(this.font, Component.translatable("screen.citiesinlife.no_silos"),
                    x, y, CityScreen.COLOUR_DIM, false);
            return;
        }
        for (int i = 0; i < map.silos().size(); i++) {
            MissileMapPayload.Silo silo = map.silos().get(i);
            boolean chosen = i == selectedSilo;
            if (chosen) {
                graphics.fill(x - 2, y - 2, x + sidebar - 2, y + 20, 0x33FFFFFF);
            }
            if (isOver(mouseX, mouseY, x - 2, y - 2, sidebar, 22)) {
                graphics.fill(x - 2, y - 2, x + sidebar - 2, y + 20, 0x22FFFFFF);
            }
            graphics.drawString(this.font, Component.literal(silo.name()), x, y,
                    chosen ? CityScreen.COLOUR_ACCENT : CityScreen.COLOUR_TEXT, false);
            // What is standing in it. This is the whole reason the panel exists: a silo you can
            // see is empty is a silo you do not click launch on and then wonder about. A negative
            // count means the box is too big to be read at all, which is a different thing from
            // empty and has to say so - reporting nought there sent people looking for a fault in
            // a missile that was standing right where they left it.
            boolean blind = silo.ballistic() < 0;
            Component stock = blind
                    ? Component.translatable("screen.citiesinlife.silo_box_too_large")
                    : Component.literal(silo.ballistic() + "B  " + silo.nuclear() + "N  "
                            + silo.interceptors() + "I");
            graphics.drawString(this.font, stock, x, y + 10,
                    blind || silo.busy() ? CityScreen.COLOUR_BAD : CityScreen.COLOUR_DIM, false);
            y += 24;
        }
    }

    private void drawGrid(GuiGraphics graphics, @Nullable MissileMapPayload map,
                          int mouseX, int mouseY) {
        Level level = this.minecraft == null ? null : this.minecraft.level;
        for (int row = 0; row < grid; row++) {
            for (int column = 0; column < grid; column++) {
                int chunkX = centreX - radius + column;
                int chunkZ = centreZ - radius + row;
                int x = gridLeft + column * tile;
                int y = gridTop + row * tile;

                graphics.fill(x, y, x + tile, y + tile, terrainColour(level, chunkX, chunkZ));

                byte relation = territory.get(ChunkPos.asLong(chunkX, chunkZ));
                if (relation >= 0 && relation < RELATION_COLOUR.length) {
                    int colour = RELATION_COLOUR[relation];
                    graphics.fill(x, y, x + tile, y + tile, 0x55000000 | (colour & 0xFFFFFF));
                    graphics.fill(x, y, x + tile, y + 1, colour);
                    graphics.fill(x, y, x + 1, y + tile, colour);
                } else {
                    graphics.fill(x, y, x + tile, y + 1, COLOUR_GRID);
                    graphics.fill(x, y, x + 1, y + tile, COLOUR_GRID);
                }
                if (isOver(mouseX, mouseY, x, y, tile, tile)) {
                    graphics.fill(x, y, x + tile, y + tile, COLOUR_HOVER);
                }
            }
        }
        if (map == null) {
            return;
        }
        // Your silos, on the ground they actually stand on.
        for (int i = 0; i < map.silos().size(); i++) {
            MissileMapPayload.Silo silo = map.silos().get(i);
            mark(graphics, silo.blockX() >> 4, silo.blockZ() >> 4,
                    i == selectedSilo ? COLOUR_SELECTED : COLOUR_SILO);
        }
        // And whatever is in the air, from where it left to where it is going.
        for (MissileMapPayload.Track track : map.tracks()) {
            int colour = track.mine() ? COLOUR_TRACK_MINE : COLOUR_TRACK;
            mark(graphics, track.toX() >> 4, track.toZ() >> 4, colour);
            mark(graphics, track.fromX() >> 4, track.fromZ() >> 4, 0xFF000000 | (colour & 0x7F7F7F));
        }
    }

    /** A dot on the grid, if that chunk is currently on screen. */
    private void mark(GuiGraphics graphics, int chunkX, int chunkZ, int colour) {
        int column = chunkX - (centreX - radius);
        int row = chunkZ - (centreZ - radius);
        if (column < 0 || row < 0 || column >= grid || row >= grid) {
            return;
        }
        int x = gridLeft + column * tile;
        int y = gridTop + row * tile;
        graphics.fill(x + 2, y + 2, x + tile - 2, y + tile - 2, colour);
    }

    private void drawPlaces(GuiGraphics graphics, @Nullable MissileMapPayload map,
                            int mouseX, int mouseY) {
        int x = panelLeft + panelWidth - PADDING - sidebar;
        int y = panelTop + HEADER;
        graphics.drawString(this.font, Component.translatable("screen.citiesinlife.known_cities"),
                x, y, CityScreen.COLOUR_ACCENT, false);
        y += 12;
        if (map == null) {
            return;
        }
        for (MissileMapPayload.Place place : map.places()) {
            if (y > gridTop + grid * tile - 10) {
                break;
            }
            if (isOver(mouseX, mouseY, x - 2, y - 2, sidebar, 12)) {
                graphics.fill(x - 2, y - 2, x + sidebar - 2, y + 10, 0x22FFFFFF);
            }
            graphics.drawString(this.font, Component.literal(place.name()), x, y,
                    RELATION_COLOUR[Math.min(place.relation(), RELATION_COLOUR.length - 1)], false);
            y += 12;
        }
    }

    private void drawFooter(GuiGraphics graphics, @Nullable MissileMapPayload map) {
        int x = gridLeft;
        int y = panelTop + panelHeight - 46;
        graphics.drawString(this.font,
                Component.translatable("screen.citiesinlife.missile_map_hint"),
                x, y, CityScreen.COLOUR_DIM, false);
        if (status != null) {
            graphics.drawString(this.font, status, x, y + 12, CityScreen.COLOUR_ACCENT, false);
        }
        if (map != null && !map.tracks().isEmpty()) {
            MissileMapPayload.Track first = map.tracks().get(0);
            graphics.drawString(this.font, Component.translatable(
                            "screen.citiesinlife.missile_in_flight",
                            MissileKind.values()[Math.min(first.kind(),
                                    MissileKind.values().length - 1)].displayName(),
                            first.seconds()),
                    x, y + 24, CityScreen.COLOUR_BAD, false);
        }
    }

    /**
     * The colour of a chunk, sampled from the blocks the client actually has.
     *
     * <p>Dark where it has none, which on a strategic map is most of it — and that is fine, because
     * the territory drawn on top comes from the server and is the part you are aiming with.
     */
    private int terrainColour(@Nullable Level level, int chunkX, int chunkZ) {
        if (level == null) {
            return COLOUR_UNLOADED;
        }
        long key = ChunkPos.asLong(chunkX, chunkZ);
        int cached = terrainCache.get(key);
        if (cached != 0) {
            return cached;
        }
        if (!level.hasChunk(chunkX, chunkZ)) {
            return COLOUR_UNLOADED;
        }
        ChunkAccess chunk = level.getChunk(chunkX, chunkZ);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        long red = 0;
        long green = 0;
        long blue = 0;
        int samples = 0;
        for (int localX = 4; localX < 16; localX += 8) {
            for (int localZ = 4; localZ < 16; localZ += 8) {
                int worldX = (chunkX << 4) + localX;
                int worldZ = (chunkZ << 4) + localZ;
                int height = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, worldX, worldZ);
                cursor.set(worldX, height - 1, worldZ);
                int colour = level.getBlockState(cursor).getMapColor(level, cursor).col;
                red += (colour >> 16) & 0xFF;
                green += (colour >> 8) & 0xFF;
                blue += colour & 0xFF;
                samples++;
            }
        }
        if (samples == 0) {
            return COLOUR_UNLOADED;
        }
        int packed = 0xFF000000 | (int) (red / samples) << 16
                | (int) (green / samples) << 8 | (int) (blue / samples);
        terrainCache.put(key, packed);
        return packed;
    }

    private static boolean isOver(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    // ------------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        MissileMapPayload map = ClientCityCache.missileMap();
        if (map == null) {
            return false;
        }

        // The silo list.
        int listY = panelTop + HEADER + 12;
        for (int i = 0; i < map.silos().size(); i++) {
            if (isOver((int) mouseX, (int) mouseY, panelLeft + PADDING - 2, listY - 2 + i * 24,
                    sidebar, 22)) {
                selectedSilo = i;
                return true;
            }
        }

        // The city list, which jumps the view rather than doing anything to them.
        int placeX = panelLeft + panelWidth - PADDING - sidebar;
        int placeY = panelTop + HEADER + 12;
        for (int i = 0; i < map.places().size(); i++) {
            if (isOver((int) mouseX, (int) mouseY, placeX - 2, placeY - 2 + i * 12, sidebar, 12)) {
                centreX = map.places().get(i).chunkX();
                centreZ = map.places().get(i).chunkZ();
                return true;
            }
        }

        // The map itself.
        int column = (int) ((mouseX - gridLeft) / tile);
        int row = (int) ((mouseY - gridTop) / tile);
        if (mouseX < gridLeft || mouseY < gridTop || column < 0 || row < 0
                || column >= grid || row >= grid) {
            return false;
        }
        if (selectedSilo < 0 || selectedSilo >= map.silos().size()) {
            status = Component.translatable("screen.citiesinlife.pick_a_silo");
            return true;
        }
        int chunkX = centreX - radius + column;
        int chunkZ = centreZ - radius + row;
        // Every rule is the server's. This only stops the two mistakes it can see, so the common
        // case is a rocket rather than a line of red chat.
        byte relation = territory.get(ChunkPos.asLong(chunkX, chunkZ));
        if (relation == MissileMapPayload.MINE) {
            status = Component.translatable("message.citiesinlife.missile_not_yourself");
            return true;
        }
        if (relation == MissileMapPayload.NEUTRAL || relation == MissileMapPayload.ALLIED) {
            status = Component.translatable("message.citiesinlife.missile_not_at_war");
            return true;
        }
        CitiesInLifeNetwork.sendToServer(new LaunchMissilePayload(
                map.silos().get(selectedSilo).id(), chunkX, chunkZ, kind.id()));
        status = null;
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        switch (key) {
            case 263 -> centreX -= PAN_STEP;
            case 262 -> centreX += PAN_STEP;
            case 265 -> centreZ -= PAN_STEP;
            case 264 -> centreZ += PAN_STEP;
            default -> {
                return super.keyPressed(key, scan, modifiers);
            }
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX,
                                double dragY) {
        if (button == 1) {
            centreX -= (int) (dragX / tile);
            centreZ -= (int) (dragY / tile);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
