package com.branciho.citiesinlife.client.screen;

import com.branciho.citiesinlife.net.CitiesInLifeNetwork;
import com.branciho.citiesinlife.net.ClientCityCache;
import com.branciho.citiesinlife.net.payload.HologramPayload;
import com.branciho.citiesinlife.net.payload.RequestHologramPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;

import java.util.List;

/**
 * Who is standing on your ground.
 *
 * <p>Everything shown here was chosen by the server: the payload only ever contains players inside
 * chunks this city has claimed. There is no filtering on this side to get wrong, and nothing here
 * that a modified client could unfilter.
 *
 * <p>Re-asked on a timer rather than pushed, because people walk. A second-old position on a map
 * you are reading is fine; a position from when you opened the panel is not.
 */
public class HologramScreen extends Screen {

    private static final int PANEL_WIDTH = 320;
    private static final int HEADER = 44;
    private static final int ROW_HEIGHT = 12;
    private static final int ROWS = 6;
    private static final int FOOTER = 34;

    /**
     * How tall the map itself is.
     *
     * <p>The block is a table with a globe of light over it and it was showing a list of names with
     * coordinates beside them. Coordinates are not a map: reading one meant subtracting your own
     * position in your head to work out whether somebody was near the gate or out by the farms.
     * A square of your own territory with a dot per person answers that at a glance, which is what
     * the thing looked like it did all along.
     */
    private static final int MAP_HEIGHT = 116;

    /** Smallest a chunk may be drawn, and largest, so one chunk is not the whole table. */
    private static final int MIN_CHUNK_PIXELS = 2;
    private static final int MAX_CHUNK_PIXELS = 14;

    private static final int COLOUR_GROUND = 0x5516E0D0;
    private static final int COLOUR_GRID = 0x2216E0D0;
    private static final int COLOUR_YOU = 0xFFFFE066;

    /** Half a second. People walk, and a map that lags behind them is worse than no map. */
    private static final int REFRESH_TICKS = 10;

    private int left;
    private int top;
    private int seenRevision = -1;
    private int sinceAsked;
    private int scroll;

    public HologramScreen() {
        super(Component.translatable("screen.citiesinlife.hologram"));
    }

    private static int panelHeight() {
        return HEADER + MAP_HEIGHT + 10 + ROWS * ROW_HEIGHT + FOOTER;
    }

    @Override
    protected void init() {
        left = (this.width - PANEL_WIDTH) / 2;
        top = (this.height - panelHeight()) / 2;
        seenRevision = ClientCityCache.hologramRevision();
        CitiesInLifeNetwork.sendToServer(RequestHologramPayload.INSTANCE);

        addRenderableWidget(Button.builder(Component.translatable("gui.done"),
                        press -> this.onClose())
                .bounds(left + PANEL_WIDTH / 2 - 45, top + panelHeight() - 26, 90, 20)
                .build());
    }

    @Override
    public void tick() {
        super.tick();
        if (seenRevision != ClientCityCache.hologramRevision()) {
            seenRevision = ClientCityCache.hologramRevision();
        }
        if (++sinceAsked >= REFRESH_TICKS) {
            sinceAsked = 0;
            CitiesInLifeNetwork.sendToServer(RequestHologramPayload.INSTANCE);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int hidden = Math.max(0, ClientCityCache.hologram().seen().size() - ROWS);
        if (hidden > 0) {
            scroll = Mth.clamp(scroll - (int) Math.signum(scrollY), 0, hidden);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        CityScreen.softDim(graphics, this);
        CityScreen.panel(graphics, left, top, PANEL_WIDTH, panelHeight());

        graphics.drawCenteredString(this.font, this.title, left + PANEL_WIDTH / 2, top + 10,
                CityScreen.COLOUR_TEXT);
        graphics.fill(left + 12, top + 24, left + PANEL_WIDTH - 12, top + 25,
                CityScreen.COLOUR_ACCENT);

        HologramPayload hologram = ClientCityCache.hologram();
        if (!hologram.usable()) {
            graphics.drawCenteredString(this.font,
                    Component.translatable("screen.citiesinlife.hologram_locked"),
                    left + PANEL_WIDTH / 2, top + 54, CityScreen.COLOUR_BAD);
            graphics.drawCenteredString(this.font,
                    Component.translatable("screen.citiesinlife.hologram_locked_hint"),
                    left + PANEL_WIDTH / 2, top + 70, CityScreen.COLOUR_DIM);
            return;
        }

        List<HologramPayload.Sighting> seen = hologram.seen();
        graphics.drawString(this.font,
                Component.translatable("screen.citiesinlife.hologram_count", seen.size()),
                left + 12, top + 30, CityScreen.COLOUR_TEXT, false);

        if (seen.isEmpty()) {
            drawMap(graphics, hologram, seen);
            graphics.drawCenteredString(this.font,
                    Component.translatable("screen.citiesinlife.hologram_empty"),
                    left + PANEL_WIDTH / 2, top + HEADER + MAP_HEIGHT + 18,
                    CityScreen.COLOUR_DIM);
            return;
        }

        drawMap(graphics, hologram, seen);

        scroll = Mth.clamp(scroll, 0, Math.max(0, seen.size() - ROWS));
        int y = top + HEADER + MAP_HEIGHT + 10;
        for (int i = scroll; i < seen.size() && i < scroll + ROWS; i++) {
            HologramPayload.Sighting sighting = seen.get(i);
            // The city's owner is picked out from everybody else standing on their ground, which is
            // the one distinction the table exists to draw.
            graphics.drawString(this.font, sighting.name(), left + 14, y,
                    sighting.own() ? CityScreen.COLOUR_GOOD : CityScreen.COLOUR_TEXT, false);
            Component where = Component.translatable("screen.citiesinlife.hologram_at",
                    sighting.x(), sighting.y(), sighting.z());
            graphics.drawString(this.font, where,
                    left + PANEL_WIDTH - 14 - this.font.width(where), y,
                    CityScreen.COLOUR_DIM, false);
            y += ROW_HEIGHT;
        }
    }

    /**
     * The city, from above, with a dot on everybody standing in it.
     *
     * <p>Scaled to whatever the territory happens to be rather than to a fixed zoom: a city of four
     * chunks fills the frame and so does a city of four hundred, because the question the table
     * answers — where in <em>my</em> city is this person — is the same question at either size.
     *
     * <p>Drawn from the claimed list the packet carried, so the squares and the dots always agree
     * about what the city is. A dot outside the shaded ground cannot happen: the server only sends
     * people standing on it.
     */
    private void drawMap(GuiGraphics graphics, HologramPayload hologram,
                         List<HologramPayload.Sighting> seen) {
        int mapLeft = left + 12;
        int mapTop = top + HEADER;
        int mapWidth = PANEL_WIDTH - 24;
        graphics.fill(mapLeft, mapTop, mapLeft + mapWidth, mapTop + MAP_HEIGHT, 0x66000814);

        long[] claimed = hologram.claimed();
        if (claimed.length == 0) {
            graphics.drawCenteredString(this.font,
                    Component.translatable("screen.citiesinlife.hologram_no_land"),
                    left + PANEL_WIDTH / 2, mapTop + MAP_HEIGHT / 2 - 4, CityScreen.COLOUR_DIM);
            return;
        }

        int minChunkX = Integer.MAX_VALUE;
        int maxChunkX = Integer.MIN_VALUE;
        int minChunkZ = Integer.MAX_VALUE;
        int maxChunkZ = Integer.MIN_VALUE;
        for (long key : claimed) {
            int cx = ChunkPos.getX(key);
            int cz = ChunkPos.getZ(key);
            minChunkX = Math.min(minChunkX, cx);
            maxChunkX = Math.max(maxChunkX, cx);
            minChunkZ = Math.min(minChunkZ, cz);
            maxChunkZ = Math.max(maxChunkZ, cz);
        }
        int spanX = maxChunkX - minChunkX + 1;
        int spanZ = maxChunkZ - minChunkZ + 1;
        // A chunk never shrinks below two pixels, so a territory wider than half the frame no
        // longer fits in it. That is not a hypothetical: two claims a thousand chunks apart span a
        // thousand even though the city is two chunks big. Everything drawn is clipped to the frame
        // rather than allowed to spill across the panel and out of the screen.
        int scale = Mth.clamp(Math.min(mapWidth / spanX, MAP_HEIGHT / spanZ),
                MIN_CHUNK_PIXELS, MAX_CHUNK_PIXELS);

        // Centred on whatever it came out at, so a long thin city is not pinned to one corner. When
        // it does not fit, the centring keeps the middle of the territory in the middle of the map
        // and the far edges are what falls off.
        int originX = mapLeft + (mapWidth - spanX * scale) / 2;
        int originZ = mapTop + (MAP_HEIGHT - spanZ * scale) / 2;

        for (long key : claimed) {
            int x = originX + (ChunkPos.getX(key) - minChunkX) * scale;
            int z = originZ + (ChunkPos.getZ(key) - minChunkZ) * scale;
            if (!inside(x, z, mapLeft, mapTop, mapWidth, scale)) {
                continue;
            }
            graphics.fill(x, z, x + scale, z + scale, COLOUR_GROUND);
            if (scale >= 4) {
                // A chunk grid, but only once a chunk is big enough for the lines not to be the
                // whole square.
                graphics.fill(x, z, x + scale, z + 1, COLOUR_GRID);
                graphics.fill(x, z, x + 1, z + scale, COLOUR_GRID);
            }
        }

        // People last, over the ground rather than under it. Drawn from the back of the list
        // forward so the nearest person - who the server sorted to the front - ends up on top.
        for (int i = seen.size() - 1; i >= 0; i--) {
            HologramPayload.Sighting sighting = seen.get(i);
            // Block position to a point inside its chunk, so two people in one chunk are two dots
            // rather than one. The sixteenth is the fraction across the chunk they are standing at.
            double acrossX = (Math.floorMod(sighting.x(), 16)) / 16.0D;
            double acrossZ = (Math.floorMod(sighting.z(), 16)) / 16.0D;
            int x = originX + (int) (((sighting.x() >> 4) - minChunkX + acrossX) * scale);
            int z = originZ + (int) (((sighting.z() >> 4) - minChunkZ + acrossZ) * scale);
            if (!inside(x - 2, z - 2, mapLeft, mapTop, mapWidth, 4)) {
                continue;
            }
            int colour = sighting.own() ? COLOUR_YOU : CityScreen.COLOUR_BAD;
            graphics.fill(x - 2, z - 2, x + 2, z + 2, 0xFF000814);
            graphics.fill(x - 1, z - 1, x + 1, z + 1, colour);
        }
    }

    /** Whether a square of this size at this corner lies wholly within the map frame. */
    private static boolean inside(int x, int z, int mapLeft, int mapTop, int mapWidth, int size) {
        return x >= mapLeft && z >= mapTop
                && x + size <= mapLeft + mapWidth && z + size <= mapTop + MAP_HEIGHT;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
