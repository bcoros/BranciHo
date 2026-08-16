package com.branciho.citiesinlife.client.screen;

import com.branciho.citiesinlife.net.CitiesInLifeNetwork;
import com.branciho.citiesinlife.net.ClientCityCache;
import com.branciho.citiesinlife.net.payload.ClaimChunkPayload;
import com.branciho.citiesinlife.net.payload.CitySyncPayload;
import com.branciho.citiesinlife.net.payload.RequestCityPayload;
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

/**
 * The land map: real terrain seen from above, one tile per chunk, claimed by clicking.
 *
 * <p>A flat grid of coloured squares told you nothing about where you were — "chunk 41, -17" means
 * nothing to anybody. Sampling the actual world means you claim land while looking at the lake or the
 * ridge you are claiming around, which is the decision the screen exists to support.
 *
 * <p>It follows the player rather than the city hall. Walk a thousand blocks and the map is centred
 * on you when you open it, so a city can grow in any direction for as long as its claims stay joined
 * up.
 */
public class LandMapScreen extends Screen {

    /** Chunks either side of centre. 10 gives a 21x21 view, about a screen's worth. */
    private static final int RADIUS = 10;
    private static final int TILE = 12;
    private static final int GRID = RADIUS * 2 + 1;

    private static final int PANEL_PADDING = 12;
    private static final int HEADER = 30;
    private static final int FOOTER = 46;

    /** Chunks the client has not loaded cannot be sampled, so they read as fog. */
    private static final int COLOUR_UNLOADED = 0xFF11151C;

    private static final int COLOUR_OWNED_TINT = 0x5533FF77;
    private static final int COLOUR_OWNED_EDGE = 0xFF66E576;
    private static final int COLOUR_HOVER = 0x66FFFFFF;
    private static final int COLOUR_PLAYER = 0xFFFFD86A;
    private static final int COLOUR_GRID = 0x22000000;

    /**
     * Terrain colour per chunk, kept for the life of the screen.
     *
     * <p>Sampling sixteen columns per chunk across four hundred chunks every frame would be a
     * needless few hundred thousand lookups a second; the ground does not change while a map is open.
     */
    private final Long2IntOpenHashMap terrainCache = new Long2IntOpenHashMap();

    private int panelLeft;
    private int panelTop;
    private int gridLeft;
    private int gridTop;
    private int panelWidth;
    private int panelHeight;

    private int centreX;
    private int centreZ;

    public LandMapScreen() {
        super(Component.translatable("screen.citiesinlife.land"));
        terrainCache.defaultReturnValue(0);
    }

    @Override
    protected void init() {
        panelWidth = GRID * TILE + PANEL_PADDING * 2;
        panelHeight = GRID * TILE + HEADER + FOOTER;
        panelLeft = (this.width - panelWidth) / 2;
        panelTop = (this.height - panelHeight) / 2;
        gridLeft = panelLeft + PANEL_PADDING;
        gridTop = panelTop + HEADER;

        follow();

        addRenderableWidget(Button.builder(
                        Component.translatable("screen.citiesinlife.back"),
                        button -> this.minecraft.setScreen(new CityScreen()))
                .bounds(panelLeft + PANEL_PADDING, panelTop + panelHeight - 30, 90, 20)
                .build());

        addRenderableWidget(Button.builder(
                        Component.translatable("gui.done"), button -> this.onClose())
                .bounds(panelLeft + panelWidth - PANEL_PADDING - 90, panelTop + panelHeight - 30, 90, 20)
                .build());
    }

    /** Keep the view on the player, so walking somewhere new is all it takes to claim there. */
    private void follow() {
        if (this.minecraft != null && this.minecraft.player != null) {
            centreX = this.minecraft.player.chunkPosition().x;
            centreZ = this.minecraft.player.chunkPosition().z;
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        CityScreen.softDim(graphics, this);
        follow();

        CityScreen.panel(graphics, panelLeft, panelTop, panelWidth, panelHeight);

        graphics.drawString(this.font, Component.translatable("screen.citiesinlife.land"),
                panelLeft + PANEL_PADDING, panelTop + 10, CityScreen.COLOUR_TEXT, false);

        CitySyncPayload city = ClientCityCache.city();
        if (city != null && city.hasCity()) {
            Component cost = Component.translatable("screen.citiesinlife.claim_cost",
                    CityScreen.format(city.nextClaimCost()), CityScreen.format(city.treasury()));
            int width = this.font.width(cost);
            graphics.drawString(this.font, cost,
                    panelLeft + panelWidth - PANEL_PADDING - width, panelTop + 10,
                    CityScreen.COLOUR_DIM, false);
        }

        drawGrid(graphics, mouseX, mouseY);
        drawFooter(graphics, mouseX, mouseY);
    }

    private void drawGrid(GuiGraphics graphics, int mouseX, int mouseY) {
        final Level level = this.minecraft == null ? null : this.minecraft.level;
        final int playerChunkX = centreX;
        final int playerChunkZ = centreZ;

        for (int row = 0; row < GRID; row++) {
            for (int column = 0; column < GRID; column++) {
                int chunkX = centreX - RADIUS + column;
                int chunkZ = centreZ - RADIUS + row;
                int x = gridLeft + column * TILE;
                int y = gridTop + row * TILE;

                graphics.fill(x, y, x + TILE, y + TILE, terrainColour(level, chunkX, chunkZ));

                boolean owned = ClientCityCache.claims(ChunkPos.asLong(chunkX, chunkZ));
                if (owned) {
                    // A wash rather than a solid fill, so the terrain underneath still reads.
                    graphics.fill(x, y, x + TILE, y + TILE, COLOUR_OWNED_TINT);
                    edge(graphics, x, y, chunkX, chunkZ);
                } else {
                    graphics.fill(x, y, x + TILE, y + 1, COLOUR_GRID);
                    graphics.fill(x, y, x + 1, y + TILE, COLOUR_GRID);
                }

                if (chunkX == playerChunkX && chunkZ == playerChunkZ) {
                    graphics.fill(x + TILE / 2 - 1, y + TILE / 2 - 1,
                            x + TILE / 2 + 2, y + TILE / 2 + 2, COLOUR_PLAYER);
                }
                if (isOver(mouseX, mouseY, x, y)) {
                    graphics.fill(x, y, x + TILE, y + TILE, COLOUR_HOVER);
                }
            }
        }
    }

    /**
     * The colour of a chunk, sampled from the blocks the client actually has.
     *
     * <p>Sixteen columns spread across the chunk, averaged. Fewer looks blotchy on a coastline;
     * more costs time for no visible gain at twelve pixels a tile.
     */
    private int terrainColour(Level level, int chunkX, int chunkZ) {
        if (level == null) {
            return COLOUR_UNLOADED;
        }
        long key = ChunkPos.asLong(chunkX, chunkZ);
        int cached = terrainCache.get(key);
        if (cached != 0) {
            return cached;
        }
        if (!level.hasChunk(chunkX, chunkZ)) {
            // Not cached: it may load while the screen is open.
            return COLOUR_UNLOADED;
        }

        ChunkAccess chunk = level.getChunk(chunkX, chunkZ);
        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        long red = 0;
        long green = 0;
        long blue = 0;
        int samples = 0;

        for (int localX = 2; localX < 16; localX += 4) {
            for (int localZ = 2; localZ < 16; localZ += 4) {
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
        int packed = 0xFF000000
                | (int) (red / samples) << 16
                | (int) (green / samples) << 8
                | (int) (blue / samples);
        terrainCache.put(key, packed);
        return packed;
    }

    private void edge(GuiGraphics graphics, int x, int y, int chunkX, int chunkZ) {
        if (!ClientCityCache.claims(ChunkPos.asLong(chunkX, chunkZ - 1))) {
            graphics.fill(x, y, x + TILE, y + 1, COLOUR_OWNED_EDGE);
        }
        if (!ClientCityCache.claims(ChunkPos.asLong(chunkX, chunkZ + 1))) {
            graphics.fill(x, y + TILE - 1, x + TILE, y + TILE, COLOUR_OWNED_EDGE);
        }
        if (!ClientCityCache.claims(ChunkPos.asLong(chunkX - 1, chunkZ))) {
            graphics.fill(x, y, x + 1, y + TILE, COLOUR_OWNED_EDGE);
        }
        if (!ClientCityCache.claims(ChunkPos.asLong(chunkX + 1, chunkZ))) {
            graphics.fill(x + TILE - 1, y, x + TILE, y + TILE, COLOUR_OWNED_EDGE);
        }
    }

    private void drawFooter(GuiGraphics graphics, int mouseX, int mouseY) {
        int y = gridTop + GRID * TILE + 6;
        graphics.drawString(this.font, hoveredChunk(mouseX, mouseY),
                panelLeft + PANEL_PADDING, y, CityScreen.COLOUR_DIM, false);

        Component controls = Component.translatable("screen.citiesinlife.map_controls");
        int width = this.font.width(controls);
        graphics.drawString(this.font, controls,
                panelLeft + panelWidth - PANEL_PADDING - width, y, CityScreen.COLOUR_DIM, false);
    }

    private Component hoveredChunk(int mouseX, int mouseY) {
        int column = (mouseX - gridLeft) / TILE;
        int row = (mouseY - gridTop) / TILE;
        if (mouseX < gridLeft || mouseY < gridTop || column < 0 || row < 0 || column >= GRID || row >= GRID) {
            return Component.translatable("screen.citiesinlife.map_hint");
        }
        int chunkX = centreX - RADIUS + column;
        int chunkZ = centreZ - RADIUS + row;
        boolean owned = ClientCityCache.claims(ChunkPos.asLong(chunkX, chunkZ));
        return Component.translatable(owned
                        ? "screen.citiesinlife.chunk_owned"
                        : "screen.citiesinlife.chunk_free",
                chunkX * 16, chunkZ * 16);
    }

    private boolean isOver(int mouseX, int mouseY, int x, int y) {
        return mouseX >= x && mouseX < x + TILE && mouseY >= y && mouseY < y + TILE;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        int column = (int) ((mouseX - gridLeft) / TILE);
        int row = (int) ((mouseY - gridTop) / TILE);
        if (mouseX < gridLeft || mouseY < gridTop || column < 0 || row < 0 || column >= GRID || row >= GRID) {
            return false;
        }

        int chunkX = centreX - RADIUS + column;
        int chunkZ = centreZ - RADIUS + row;
        boolean owned = ClientCityCache.claims(ChunkPos.asLong(chunkX, chunkZ));

        // Left click claims, right click releases. The server re-checks cost, adjacency and ownership
        // regardless of what this screen believed.
        if (button == 0 && !owned) {
            CitiesInLifeNetwork.sendToServer(new ClaimChunkPayload(chunkX, chunkZ, true));
            return true;
        }
        if (button == 1 && owned) {
            CitiesInLifeNetwork.sendToServer(new ClaimChunkPayload(chunkX, chunkZ, false));
            return true;
        }
        return false;
    }

    @Override
    public void onClose() {
        CitiesInLifeNetwork.sendToServer(new RequestCityPayload());
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
