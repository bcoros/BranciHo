package com.branciho.citiesinlife.client.screen;

import com.branciho.citiesinlife.city.Relation;
import com.branciho.citiesinlife.net.CitiesInLifeNetwork;
import com.branciho.citiesinlife.net.ClientCityCache;
import com.branciho.citiesinlife.net.payload.DiplomacyPayload;
import com.branciho.citiesinlife.net.payload.NeighbourCitiesPayload;
import com.branciho.citiesinlife.net.payload.RequestCityPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Everybody else.
 *
 * <p>The whole of multiplayer in one list: who else has a city here, how far away it is, and the two
 * things you can do about it. Giving somebody the run of your land and declaring war on them are the
 * same size of button on purpose — they are the two ends of the same decision, and the game should
 * not editorialise about which one you pick.
 *
 * <p>Peace deliberately takes two. Standing down here only clears your own hostility; the war ends
 * when they have done the same. Otherwise being attacked would come with a button that cancels it.
 */
public class NeighboursScreen extends Screen {

    private static final int PANEL_WIDTH = 320;
    private static final int PANEL_HEIGHT = 232;
    private static final int ROW_HEIGHT = 40;
    private static final int VISIBLE_ROWS = 4;

    private int left;
    private int top;
    private int scroll;

    public NeighboursScreen() {
        super(Component.translatable("screen.citiesinlife.neighbours"));
    }

    @Override
    protected void init() {
        left = (this.width - PANEL_WIDTH) / 2;
        top = (this.height - PANEL_HEIGHT) / 2;
        rebuild();
    }

    /**
     * Buttons are rebuilt whenever the list moves.
     *
     * <p>A row's buttons say different things depending on the relation, so there is no stable set of
     * widgets to keep around — rebuilding is both simpler and impossible to get out of step with what
     * is drawn.
     */
    private void rebuild() {
        clearWidgets();

        List<NeighbourCitiesPayload.Entry> cities = ClientCityCache.neighbours();
        int shown = Math.min(VISIBLE_ROWS, Math.max(0, cities.size() - scroll));

        for (int i = 0; i < shown; i++) {
            NeighbourCitiesPayload.Entry entry = cities.get(scroll + i);
            int rowY = top + 34 + i * ROW_HEIGHT;

            Relation yours = Relation.byOrdinal(entry.yourStance());
            boolean atWar = yours == Relation.WAR
                    || Relation.byOrdinal(entry.theirStance()) == Relation.WAR;

            if (!atWar) {
                boolean granted = yours == Relation.ALLIED;
                addRenderableWidget(Button.builder(
                                Component.translatable(granted
                                        ? "screen.citiesinlife.revoke"
                                        : "screen.citiesinlife.grant"),
                                button -> send(entry, granted
                                        ? DiplomacyPayload.ACTION_REVOKE
                                        : DiplomacyPayload.ACTION_GRANT))
                        .bounds(left + PANEL_WIDTH - 190, rowY + 14, 88, 18)
                        .build());
            }

            addRenderableWidget(Button.builder(
                            Component.translatable(atWar
                                    ? "screen.citiesinlife.stand_down"
                                    : "screen.citiesinlife.declare_war"),
                            button -> send(entry, atWar
                                    ? DiplomacyPayload.ACTION_MAKE_PEACE
                                    : DiplomacyPayload.ACTION_DECLARE_WAR))
                    .bounds(left + PANEL_WIDTH - 96, rowY + 14, 84, 18)
                    .build());
        }

        if (cities.size() > VISIBLE_ROWS) {
            addRenderableWidget(Button.builder(Component.literal("▲"), button -> page(-1))
                    .bounds(left + PANEL_WIDTH - 30, top + 34, 18, 18).build());
            addRenderableWidget(Button.builder(Component.literal("▼"), button -> page(1))
                    .bounds(left + PANEL_WIDTH - 30, top + 34 + ROW_HEIGHT, 18, 18).build());
        }

        addRenderableWidget(Button.builder(
                        Component.translatable("screen.citiesinlife.back"),
                        button -> this.minecraft.setScreen(new CityScreen()))
                .bounds(left + 12, top + PANEL_HEIGHT - 30, 100, 20)
                .build());

        addRenderableWidget(Button.builder(
                        Component.translatable("gui.done"), button -> this.onClose())
                .bounds(left + PANEL_WIDTH - 112, top + PANEL_HEIGHT - 30, 100, 20)
                .build());
    }

    private void page(int direction) {
        int max = Math.max(0, ClientCityCache.neighbours().size() - VISIBLE_ROWS);
        scroll = Math.max(0, Math.min(max, scroll + direction));
        rebuild();
    }

    private void send(NeighbourCitiesPayload.Entry entry, int action) {
        CitiesInLifeNetwork.sendToServer(new DiplomacyPayload(entry.cityId(), action));
        // The server answers with a fresh list; asking for it here means the row updates without the
        // player having to close the screen and open it again.
        CitiesInLifeNetwork.sendToServer(new RequestCityPayload());
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        CityScreen.softDim(graphics, this);
        CityScreen.panel(graphics, left, top, PANEL_WIDTH, PANEL_HEIGHT);

        graphics.drawString(this.font, Component.translatable("screen.citiesinlife.neighbours"),
                left + 12, top + 10, CityScreen.COLOUR_TEXT, false);
        graphics.fill(left + 12, top + 22, left + PANEL_WIDTH - 12, top + 23, CityScreen.COLOUR_ACCENT);

        List<NeighbourCitiesPayload.Entry> cities = ClientCityCache.neighbours();
        if (cities.isEmpty()) {
            graphics.drawCenteredString(this.font,
                    Component.translatable("screen.citiesinlife.no_neighbours"),
                    left + PANEL_WIDTH / 2, top + 70, CityScreen.COLOUR_DIM);
            graphics.drawCenteredString(this.font,
                    Component.translatable("screen.citiesinlife.no_neighbours_hint"),
                    left + PANEL_WIDTH / 2, top + 86, CityScreen.COLOUR_DIM);
            return;
        }

        int shown = Math.min(VISIBLE_ROWS, cities.size() - scroll);
        for (int i = 0; i < shown; i++) {
            row(graphics, cities.get(scroll + i), top + 34 + i * ROW_HEIGHT);
        }
    }

    private void row(GuiGraphics graphics, NeighbourCitiesPayload.Entry entry, int y) {
        Relation theirs = Relation.byOrdinal(entry.theirStance());
        Relation yours = Relation.byOrdinal(entry.yourStance());

        graphics.fill(left + 12, y, left + PANEL_WIDTH - 12, y + ROW_HEIGHT - 6, 0x30000000);
        graphics.fill(left + 12, y, left + 14, y + ROW_HEIGHT - 6, 0xFF000000 | theirs.colour());

        graphics.drawString(this.font, Component.literal(entry.name()),
                left + 20, y + 5, CityScreen.COLOUR_TEXT, false);
        graphics.drawString(this.font,
                Component.translatable("screen.citiesinlife.owned_by", entry.ownerName()),
                left + 20, y + 17, CityScreen.COLOUR_DIM, false);

        // Two stances, because they are genuinely different facts: whether you may build there, and
        // whether they may build in yours. Showing one would leave half of it a mystery.
        Component standing = Component.translatable("screen.citiesinlife.standing",
                theirs.displayName(), yours.displayName());
        graphics.drawString(this.font, standing,
                left + 20, y + 28, 0xFF000000 | theirs.colour(), false);

        Component where = entry.distance() < 0
                ? Component.translatable("screen.citiesinlife.no_land")
                : Component.translatable("screen.citiesinlife.chunks_away",
                        entry.distance(), entry.chunks());
        int width = this.font.width(where);
        graphics.drawString(this.font, where,
                left + PANEL_WIDTH - 24 - width, y + 5, CityScreen.COLOUR_DIM, false);
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
