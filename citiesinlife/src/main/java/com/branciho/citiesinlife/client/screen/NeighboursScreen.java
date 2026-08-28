package com.branciho.citiesinlife.client.screen;

import com.branciho.citiesinlife.city.Pact;
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
 * <p>One city at a time rather than four squeezed into a list. The list version had a name, an
 * owner, two stances and a distance all competing for the same 320 pixels as two buttons, and every
 * one of those is a variable-length string — a long city name ran straight under the buttons, the
 * standing line collided with the distance, and the page arrows sat on top of the second row. There
 * is now genuinely more to show per city than a row can hold, so the screen shows one and pages
 * between them.
 *
 * <p>The two kinds of arrangement are kept visibly apart, because they behave differently and
 * confusing them is how somebody ends up thinking they have agreed to something they have not. A
 * grant is one way and takes effect the moment you give it. A pact takes two, and says so: the
 * button reads Propose until they have asked as well, and then Accept.
 */
public class NeighboursScreen extends Screen {

    private static final int PANEL_WIDTH = 340;
    private static final int PANEL_HEIGHT = 246;

    /** Room for the buttons on the right, which is what the text must never grow into. */
    private static final int BUTTON_WIDTH = 104;
    private static final int TEXT_RIGHT_MARGIN = BUTTON_WIDTH + 24;

    private int left;
    private int top;
    private int index;

    public NeighboursScreen() {
        super(Component.translatable("screen.citiesinlife.neighbours"));
    }

    @Override
    protected void init() {
        left = (this.width - PANEL_WIDTH) / 2;
        top = (this.height - PANEL_HEIGHT) / 2;
        rebuild();
    }

    private List<NeighbourCitiesPayload.Entry> cities() {
        return ClientCityCache.neighbours();
    }

    private NeighbourCitiesPayload.Entry current() {
        List<NeighbourCitiesPayload.Entry> cities = cities();
        if (cities.isEmpty()) {
            return null;
        }
        index = Math.max(0, Math.min(index, cities.size() - 1));
        return cities.get(index);
    }

    /**
     * Buttons are rebuilt whenever anything changes.
     *
     * <p>Every button here says something different depending on the state it is looking at, so
     * there is no stable set of widgets worth keeping — rebuilding is both simpler and impossible
     * to get out of step with what is drawn.
     */
    private void rebuild() {
        clearWidgets();

        NeighbourCitiesPayload.Entry entry = current();
        if (entry != null) {
            buildFor(entry);
        }

        if (cities().size() > 1) {
            addRenderableWidget(Button.builder(Component.literal("<"), button -> page(-1))
                    .bounds(left + PANEL_WIDTH - 56, top + 10, 20, 16).build());
            addRenderableWidget(Button.builder(Component.literal(">"), button -> page(1))
                    .bounds(left + PANEL_WIDTH - 32, top + 10, 20, 16).build());
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

    private void buildFor(NeighbourCitiesPayload.Entry entry) {
        Relation yours = Relation.byOrdinal(entry.yourStance());
        boolean atWar = yours == Relation.WAR
                || Relation.byOrdinal(entry.theirStance()) == Relation.WAR;

        int x = left + PANEL_WIDTH - 12 - BUTTON_WIDTH;
        int y = top + 40;

        // Building rights: one way, immediate, and nothing to negotiate.
        if (!atWar) {
            boolean granted = yours == Relation.ALLIED;
            addRenderableWidget(Button.builder(
                            Component.translatable(granted
                                    ? "screen.citiesinlife.revoke"
                                    : "screen.citiesinlife.grant"),
                            button -> send(entry, granted
                                    ? DiplomacyPayload.ACTION_REVOKE
                                    : DiplomacyPayload.ACTION_GRANT))
                    .bounds(x, y, BUTTON_WIDTH, 18)
                    .build());
        }
        y += 22;

        // The three pacts, each with the one button its current state calls for.
        if (!atWar) {
            for (Pact pact : Pact.values()) {
                Pact.State state = stateOf(entry, pact);
                addRenderableWidget(Button.builder(labelFor(pact, state),
                                button -> send(entry, state == Pact.State.ACTIVE
                                                || state == Pact.State.OFFERED
                                                ? DiplomacyPayload.ACTION_PACT_CANCEL
                                                : DiplomacyPayload.ACTION_PACT_OFFER,
                                        pact.ordinal(), 0))
                        .bounds(x, y, BUTTON_WIDTH, 18)
                        .build());
                y += 22;
            }
        }

        // War sits at the bottom, away from everything else, and asks before it does anything.
        addRenderableWidget(Button.builder(
                        Component.translatable(atWar
                                ? "screen.citiesinlife.stand_down"
                                : "screen.citiesinlife.declare_war"),
                        button -> {
                            if (atWar) {
                                send(entry, DiplomacyPayload.ACTION_MAKE_PEACE);
                            } else {
                                this.minecraft.setScreen(new ConfirmWarScreen(
                                        entry.cityId(), entry.name(), this));
                            }
                        })
                .bounds(x, top + PANEL_HEIGHT - 58, BUTTON_WIDTH, 18)
                .build());

        // Pricing is only worth showing once there is a supply to price.
        if (stateOf(entry, Pact.UTILITIES) == Pact.State.ACTIVE) {
            addRenderableWidget(Button.builder(
                            Component.translatable("screen.citiesinlife.set_prices"),
                            button -> this.minecraft.setScreen(new UtilityPriceScreen(entry, this)))
                    .bounds(left + 12, top + PANEL_HEIGHT - 58, 120, 18)
                    .build());
        }
    }

    private static Pact.State stateOf(NeighbourCitiesPayload.Entry entry, Pact pact) {
        boolean mine = (entry.yourPacts() & pact.bit()) != 0;
        boolean theirs = (entry.theirPacts() & pact.bit()) != 0;
        if (mine && theirs) {
            return Pact.State.ACTIVE;
        }
        if (mine) {
            return Pact.State.OFFERED;
        }
        return theirs ? Pact.State.INVITED : Pact.State.NONE;
    }

    /**
     * What the one button for a pact should say.
     *
     * <p>Four states and four different words, because "toggle" is not a thing a player can reason
     * about here: withdrawing an offer nobody has answered and tearing up a live agreement are the
     * same click but very much not the same act.
     */
    private static Component labelFor(Pact pact, Pact.State state) {
        String key = switch (state) {
            case NONE -> "screen.citiesinlife.pact_propose";
            case OFFERED -> "screen.citiesinlife.pact_withdraw";
            case INVITED -> "screen.citiesinlife.pact_accept";
            case ACTIVE -> "screen.citiesinlife.pact_end";
        };
        return Component.translatable(key, pact.displayName());
    }

    private void page(int direction) {
        int size = cities().size();
        if (size == 0) {
            return;
        }
        index = Math.floorMod(index + direction, size);
        rebuild();
    }

    private void send(NeighbourCitiesPayload.Entry entry, int action) {
        send(entry, action, 0, 0);
    }

    private void send(NeighbourCitiesPayload.Entry entry, int action, int a, int b) {
        CitiesInLifeNetwork.sendToServer(new DiplomacyPayload(entry.cityId(), action, a, b));
        // The server answers with a fresh list; asking for it here means the screen updates without
        // the player having to close it and open it again.
        CitiesInLifeNetwork.sendToServer(new RequestCityPayload());
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        CityScreen.softDim(graphics, this);
        CityScreen.panel(graphics, left, top, PANEL_WIDTH, PANEL_HEIGHT);

        graphics.drawString(this.font, Component.translatable("screen.citiesinlife.neighbours"),
                left + 12, top + 10, CityScreen.COLOUR_TEXT, false);
        graphics.fill(left + 12, top + 28, left + PANEL_WIDTH - 12, top + 29,
                CityScreen.COLOUR_ACCENT);

        List<NeighbourCitiesPayload.Entry> cities = cities();
        if (cities.isEmpty()) {
            graphics.drawCenteredString(this.font,
                    Component.translatable("screen.citiesinlife.no_neighbours"),
                    left + PANEL_WIDTH / 2, top + 80, CityScreen.COLOUR_DIM);
            graphics.drawCenteredString(this.font,
                    Component.translatable("screen.citiesinlife.no_neighbours_hint"),
                    left + PANEL_WIDTH / 2, top + 96, CityScreen.COLOUR_DIM);
            return;
        }

        NeighbourCitiesPayload.Entry entry = current();
        if (entry == null) {
            return;
        }

        // "2 / 5", so paging does not feel like falling off the end of something.
        Component counter = Component.literal((index + 1) + " / " + cities.size());
        graphics.drawString(this.font, counter,
                left + PANEL_WIDTH - 60 - this.font.width(counter), top + 12,
                CityScreen.COLOUR_DIM, false);

        detail(graphics, entry);
    }

    private void detail(GuiGraphics graphics, NeighbourCitiesPayload.Entry entry) {
        Relation theirs = Relation.byOrdinal(entry.theirStance());
        Relation yours = Relation.byOrdinal(entry.yourStance());

        int textLeft = left + 20;
        // Everything below is clipped to this, which is the whole fix: the buttons own the right
        // hand side of the panel and no string is allowed to reach them however long it is.
        int textWidth = PANEL_WIDTH - TEXT_RIGHT_MARGIN - 28;

        graphics.fill(left + 12, top + 36, left + PANEL_WIDTH - 12, top + PANEL_HEIGHT - 66,
                0x30000000);
        graphics.fill(left + 12, top + 36, left + 15, top + PANEL_HEIGHT - 66,
                0xFF000000 | theirs.colour());

        int y = top + 44;
        graphics.drawString(this.font, clip(entry.name(), textWidth),
                textLeft, y, CityScreen.COLOUR_TEXT, false);
        y += 12;
        graphics.drawString(this.font,
                clip(Component.translatable("screen.citiesinlife.owned_by", entry.ownerName()),
                        textWidth),
                textLeft, y, CityScreen.COLOUR_DIM, false);
        y += 16;

        Component where = entry.distance() < 0
                ? Component.translatable("screen.citiesinlife.no_land")
                : Component.translatable("screen.citiesinlife.chunks_away",
                        entry.distance(), entry.chunks());
        graphics.drawString(this.font, clip(where, textWidth), textLeft, y,
                CityScreen.COLOUR_DIM, false);
        y += 16;

        // Two stances on two lines rather than one crowded one. They are genuinely different facts
        // — whether you may build there, and whether they may build in yours — and the old single
        // line was the string most likely to run into the buttons.
        graphics.drawString(this.font,
                Component.translatable("screen.citiesinlife.they_regard_you", theirs.displayName()),
                textLeft, y, 0xFF000000 | theirs.colour(), false);
        y += 12;
        graphics.drawString(this.font,
                Component.translatable("screen.citiesinlife.you_regard_them", yours.displayName()),
                textLeft, y, 0xFF000000 | yours.colour(), false);
        y += 16;

        for (Pact pact : Pact.values()) {
            Pact.State state = stateOf(entry, pact);
            graphics.drawString(this.font,
                    Component.translatable("screen.citiesinlife.pact_line", pact.displayName(),
                            Component.translatable("pact_state.citiesinlife."
                                    + state.name().toLowerCase(java.util.Locale.ROOT))),
                    textLeft, y, colourFor(state), false);
            y += 12;
        }

        if (stateOf(entry, Pact.UTILITIES) == Pact.State.ACTIVE) {
            y += 4;
            graphics.drawString(this.font,
                    Component.translatable("screen.citiesinlife.you_charge",
                            entry.yourPowerPrice(), entry.yourWaterPrice()),
                    textLeft, y, CityScreen.COLOUR_DIM, false);
            y += 12;
            graphics.drawString(this.font,
                    Component.translatable("screen.citiesinlife.they_charge",
                            entry.theirPowerPrice(), entry.theirWaterPrice()),
                    textLeft, y, CityScreen.COLOUR_DIM, false);
        }
    }

    private static int colourFor(Pact.State state) {
        return switch (state) {
            case ACTIVE -> CityScreen.COLOUR_GOOD;
            case OFFERED, INVITED -> CityScreen.COLOUR_ACCENT;
            case NONE -> CityScreen.COLOUR_DIM;
        };
    }

    /** Cut a line to the space it has, with an ellipsis, rather than letting it run over a button. */
    private Component clip(Component text, int width) {
        return clip(text.getString(), width);
    }

    private Component clip(String text, int width) {
        if (this.font.width(text) <= width) {
            return Component.literal(text);
        }
        return Component.literal(this.font.plainSubstrByWidth(text, width - this.font.width("..."))
                + "...");
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
