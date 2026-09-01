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

    private static final int PANEL_WIDTH = 300;
    private static final int HEADER = 44;
    private static final int ROW_HEIGHT = 12;
    private static final int ROWS = 9;
    private static final int FOOTER = 34;

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
        return HEADER + ROWS * ROW_HEIGHT + FOOTER;
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
            graphics.drawCenteredString(this.font,
                    Component.translatable("screen.citiesinlife.hologram_empty"),
                    left + PANEL_WIDTH / 2, top + HEADER + 16, CityScreen.COLOUR_DIM);
            return;
        }

        scroll = Mth.clamp(scroll, 0, Math.max(0, seen.size() - ROWS));
        int y = top + HEADER;
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

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
