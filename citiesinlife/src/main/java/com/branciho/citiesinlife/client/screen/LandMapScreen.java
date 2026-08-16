package com.branciho.citiesinlife.client.screen;

import com.branciho.citiesinlife.net.CitiesInLifeNetwork;
import com.branciho.citiesinlife.net.ClientCityCache;
import com.branciho.citiesinlife.net.payload.ClaimChunkPayload;
import com.branciho.citiesinlife.net.payload.CitySyncPayload;
import com.branciho.citiesinlife.net.payload.RequestCityPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;

/**
 * The land map: the city's territory as a grid of chunks, claimed by clicking them.
 *
 * <p>Territory is otherwise invisible and abstract — "chunk 41, -17" means nothing to anybody. A grid
 * you can point at turns claiming land into a decision about shape rather than a guessing game with
 * coordinates.
 */
public class LandMapScreen extends Screen {

    /** Chunks either side of centre. 8 gives a 17x17 grid, about a screen's worth. */
    private static final int RADIUS = 8;
    private static final int TILE = 13;
    private static final int GRID = RADIUS * 2 + 1;

    private static final int PANEL_PADDING = 12;
    private static final int HEADER = 30;
    private static final int FOOTER = 46;

    private static final int COLOUR_UNCLAIMED = 0xFF161C26;
    private static final int COLOUR_OWNED = 0xFF2E7D4F;
    private static final int COLOUR_OWNED_EDGE = 0xFF66E576;
    private static final int COLOUR_HOVER = 0x66FFFFFF;
    private static final int COLOUR_PLAYER = 0xFFFFD86A;

    private int panelLeft;
    private int panelTop;
    private int gridLeft;
    private int gridTop;

    /** Centre of the view, in chunk coordinates. Starts where the player is standing. */
    private int centreX;
    private int centreZ;

    public LandMapScreen() {
        super(Component.translatable("screen.citiesinlife.land"));
    }

    @Override
    protected void init() {
        final int panelWidth = GRID * TILE + PANEL_PADDING * 2;
        final int panelHeight = GRID * TILE + HEADER + FOOTER;
        panelLeft = (this.width - panelWidth) / 2;
        panelTop = (this.height - panelHeight) / 2;
        gridLeft = panelLeft + PANEL_PADDING;
        gridTop = panelTop + HEADER;

        if (this.minecraft != null && this.minecraft.player != null) {
            centreX = this.minecraft.player.chunkPosition().x;
            centreZ = this.minecraft.player.chunkPosition().z;
        }

        addRenderableWidget(Button.builder(
                        Component.translatable("screen.citiesinlife.back"),
                        button -> this.minecraft.setScreen(new CityScreen()))
                .bounds(panelLeft + PANEL_PADDING, panelTop + panelHeight - 30, 90, 20)
                .build());

        addRenderableWidget(Button.builder(
                        Component.translatable("screen.citiesinlife.recentre"),
                        button -> recentre())
                .bounds(panelLeft + panelWidth - PANEL_PADDING - 90, panelTop + panelHeight - 30, 90, 20)
                .build());
    }

    private void recentre() {
        if (this.minecraft != null && this.minecraft.player != null) {
            centreX = this.minecraft.player.chunkPosition().x;
            centreZ = this.minecraft.player.chunkPosition().z;
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);

        final int panelWidth = GRID * TILE + PANEL_PADDING * 2;
        final int panelHeight = GRID * TILE + HEADER + FOOTER;
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
        drawLegendAndHint(graphics, mouseX, mouseY, panelWidth);
    }

    private void drawGrid(GuiGraphics graphics, int mouseX, int mouseY) {
        final int playerChunkX = this.minecraft != null && this.minecraft.player != null
                ? this.minecraft.player.chunkPosition().x : centreX;
        final int playerChunkZ = this.minecraft != null && this.minecraft.player != null
                ? this.minecraft.player.chunkPosition().z : centreZ;

        for (int row = 0; row < GRID; row++) {
            for (int column = 0; column < GRID; column++) {
                int chunkX = centreX - RADIUS + column;
                int chunkZ = centreZ - RADIUS + row;
                int x = gridLeft + column * TILE;
                int y = gridTop + row * TILE;

                boolean owned = ClientCityCache.claims(ChunkPos.asLong(chunkX, chunkZ));
                graphics.fill(x, y, x + TILE - 1, y + TILE - 1, owned ? COLOUR_OWNED : COLOUR_UNCLAIMED);

                if (owned) {
                    // Only the outward edges, so a solid claim reads as one shape instead of a grid.
                    edge(graphics, x, y, chunkX, chunkZ);
                }
                if (chunkX == playerChunkX && chunkZ == playerChunkZ) {
                    graphics.fill(x + TILE / 2 - 1, y + TILE / 2 - 1,
                            x + TILE / 2 + 1, y + TILE / 2 + 1, COLOUR_PLAYER);
                }
                if (isOver(mouseX, mouseY, x, y)) {
                    graphics.fill(x, y, x + TILE - 1, y + TILE - 1, COLOUR_HOVER);
                }
            }
        }
    }

    private void edge(GuiGraphics graphics, int x, int y, int chunkX, int chunkZ) {
        if (!ClientCityCache.claims(ChunkPos.asLong(chunkX, chunkZ - 1))) {
            graphics.fill(x, y, x + TILE - 1, y + 1, COLOUR_OWNED_EDGE);
        }
        if (!ClientCityCache.claims(ChunkPos.asLong(chunkX, chunkZ + 1))) {
            graphics.fill(x, y + TILE - 2, x + TILE - 1, y + TILE - 1, COLOUR_OWNED_EDGE);
        }
        if (!ClientCityCache.claims(ChunkPos.asLong(chunkX - 1, chunkZ))) {
            graphics.fill(x, y, x + 1, y + TILE - 1, COLOUR_OWNED_EDGE);
        }
        if (!ClientCityCache.claims(ChunkPos.asLong(chunkX + 1, chunkZ))) {
            graphics.fill(x + TILE - 2, y, x + TILE - 1, y + TILE - 1, COLOUR_OWNED_EDGE);
        }
    }

    private void drawLegendAndHint(GuiGraphics graphics, int mouseX, int mouseY, int panelWidth) {
        int y = gridTop + GRID * TILE + 6;

        Component hint = hoveredChunk(mouseX, mouseY);
        graphics.drawString(this.font, hint, panelLeft + PANEL_PADDING, y, CityScreen.COLOUR_DIM, false);

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
                chunkX, chunkZ);
    }

    private boolean isOver(int mouseX, int mouseY, int x, int y) {
        return mouseX >= x && mouseX < x + TILE - 1 && mouseY >= y && mouseY < y + TILE - 1;
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

        // Left click claims, right click releases. The server checks cost, adjacency and ownership
        // again regardless of what this screen believed.
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
        // Leave with fresh numbers rather than whatever the last claim left behind.
        CitiesInLifeNetwork.sendToServer(new RequestCityPayload());
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
