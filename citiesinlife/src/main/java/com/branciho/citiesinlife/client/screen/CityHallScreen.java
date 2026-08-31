package com.branciho.citiesinlife.client.screen;

import com.branciho.citiesinlife.city.AlertLevel;
import com.branciho.citiesinlife.city.LedgerEntry;
import com.branciho.citiesinlife.net.CitiesInLifeNetwork;
import com.branciho.citiesinlife.net.ClientCityCache;
import com.branciho.citiesinlife.net.payload.CityHallActionPayload;
import com.branciho.citiesinlife.net.payload.CityHallPayload;
import com.branciho.citiesinlife.net.payload.RequestCityHallPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * The city hall: the room where a city does the things only a city hall can do.
 *
 * <p>Founding a city was, until now, the last time anybody had a reason to stand in their own city
 * hall. This is the panel that gives it the rest of its job — declaring an alert, calling a meeting,
 * speaking to everyone on your ground, and firing everything you have.
 *
 * <p>Every button is greyed out unless the server says you are standing inside the building. That
 * is a courtesy rather than a defence: the server re-checks on every press, because a player can
 * walk out of the hall with this screen still open. Greying them here means a player finds out by
 * reading a line rather than by pressing something that silently does nothing.
 */
public class CityHallScreen extends Screen {

    private static final int PANEL_WIDTH = 320;

    private static final int HEADER = 40;
    private static final int ROW = 24;
    private static final int BUTTON_ROWS = 3;
    private static final int ROLL_HEIGHT = 24;
    private static final int LEDGER_LABEL = 14;

    /** As much history as fits without the panel needing to scroll or the screen to overflow. */
    private static final int LEDGER_ROWS = 6;
    private static final int LEDGER_ROW_HEIGHT = 11;
    private static final int FOOTER = 30;

    /** Re-asked on a timer, so a meeting filling up shows without reopening the panel. */
    private static final int REFRESH_TICKS = 20;

    private int left;
    private int top;
    private int seenRevision = -1;
    private int sinceAsked;
    private EditBox addressBox;

    public CityHallScreen() {
        super(Component.translatable("screen.citiesinlife.city_hall"));
    }

    private static int panelHeight() {
        return HEADER + BUTTON_ROWS * ROW + ROLL_HEIGHT + LEDGER_LABEL
                + LEDGER_ROWS * LEDGER_ROW_HEIGHT + FOOTER;
    }

    @Override
    protected void init() {
        left = (this.width - PANEL_WIDTH) / 2;
        top = (this.height - panelHeight()) / 2;
        seenRevision = ClientCityCache.cityHallRevision();
        CitiesInLifeNetwork.sendToServer(new RequestCityHallPayload());

        CityHallPayload hall = ClientCityCache.cityHall();
        boolean open = hall.hasCity() && hall.inHall();

        int third = (PANEL_WIDTH - 24 - 8) / 3;
        int y = top + HEADER;

        // Three levels as three buttons rather than one that cycles. A cycle hides where you are
        // going next, and this is a control whose whole job is to be unambiguous in a hurry.
        for (AlertLevel level : AlertLevel.values()) {
            Button button = Button.builder(level.displayName(),
                            press -> send("alert", level.id()))
                    .bounds(left + 12 + level.ordinal() * (third + 4), y, third, 20)
                    .build();
            button.active = open && !level.id().equals(hall.alert());
            addRenderableWidget(button);
        }

        y += ROW;
        Button meeting = Button.builder(
                        Component.translatable(hall.meeting()
                                ? "screen.citiesinlife.meeting_end"
                                : "screen.citiesinlife.meeting_start"),
                        press -> send(hall.meeting() ? "meeting_end" : "meeting_start", ""))
                .bounds(left + 12, y, third * 2 + 4, 20)
                .build();
        meeting.active = open;
        addRenderableWidget(meeting);

        Button launch = Button.builder(
                        Component.translatable("screen.citiesinlife.launch_all"),
                        press -> this.minecraft.setScreen(new MissileMapScreen(true)))
                .bounds(left + 12 + (third + 4) * 2, y, third, 20)
                .build();
        launch.active = open;
        addRenderableWidget(launch);

        y += ROW;
        addressBox = new EditBox(this.font, left + 12, y, PANEL_WIDTH - 24 - third - 4, 20,
                Component.translatable("screen.citiesinlife.address_hint"));
        addressBox.setMaxLength(CityHallActionPayload.MAX_DETAIL);
        addressBox.setEditable(open);
        addRenderableWidget(addressBox);

        Button announce = Button.builder(
                        Component.translatable("screen.citiesinlife.address_send"),
                        press -> {
                            send("address", addressBox.getValue());
                            addressBox.setValue("");
                        })
                .bounds(left + PANEL_WIDTH - 12 - third, y, third, 20)
                .build();
        announce.active = open;
        addRenderableWidget(announce);

        addRenderableWidget(Button.builder(
                        Component.translatable("gui.back"),
                        press -> this.minecraft.setScreen(new CityScreen()))
                .bounds(left + PANEL_WIDTH - 12 - 90, top + panelHeight() - 26, 90, 20)
                .build());
    }

    private void send(String action, String detail) {
        CitiesInLifeNetwork.sendToServer(new CityHallActionPayload(action, detail));
        CitiesInLifeNetwork.sendToServer(new RequestCityHallPayload());
    }

    @Override
    public void tick() {
        super.tick();
        if (seenRevision != ClientCityCache.cityHallRevision()) {
            rebuildWidgets();
        }
        // Nothing on this panel is pushed when it changes - a guest arriving at somebody else's
        // meeting does not notify the host's client - so it is asked for again on a timer. One
        // packet a second while a single screen is open is not worth being clever about.
        if (++sinceAsked >= REFRESH_TICKS) {
            sinceAsked = 0;
            CitiesInLifeNetwork.sendToServer(new RequestCityHallPayload());
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        CityScreen.softDim(graphics, this);
        CityScreen.panel(graphics, left, top, PANEL_WIDTH, panelHeight());

        graphics.drawCenteredString(this.font, this.title, left + PANEL_WIDTH / 2, top + 10,
                CityScreen.COLOUR_TEXT);
        graphics.fill(left + 12, top + 24, left + PANEL_WIDTH - 12, top + 25,
                CityScreen.COLOUR_ACCENT);

        CityHallPayload hall = ClientCityCache.cityHall();
        if (!hall.hasCity()) {
            graphics.drawCenteredString(this.font,
                    Component.translatable("screen.citiesinlife.no_city"),
                    left + PANEL_WIDTH / 2, top + 60, CityScreen.COLOUR_TEXT);
            graphics.drawCenteredString(this.font,
                    Component.translatable("screen.citiesinlife.no_city_hint"),
                    left + PANEL_WIDTH / 2, top + 76, CityScreen.COLOUR_DIM);
            return;
        }

        AlertLevel level = AlertLevel.byId(hall.alert(), AlertLevel.PEACE);
        graphics.drawString(this.font,
                Component.translatable("screen.citiesinlife.alert_level", level.displayName()),
                left + 12, top + 29, level.colour(), false);
        if (!hall.inHall()) {
            Component warn = Component.translatable("screen.citiesinlife.not_in_hall");
            graphics.drawString(this.font, warn,
                    left + PANEL_WIDTH - 12 - this.font.width(warn), top + 29,
                    CityScreen.COLOUR_BAD, false);
        }

        int y = top + HEADER + BUTTON_ROWS * ROW + 2;
        List<String> roll = hall.roll();
        if (roll.isEmpty()) {
            graphics.drawString(this.font,
                    Component.translatable("screen.citiesinlife.meeting_none"),
                    left + 12, y, CityScreen.COLOUR_DIM, false);
        } else {
            graphics.drawString(this.font,
                    Component.translatable("screen.citiesinlife.meeting_room", roll.size()),
                    left + 12, y, CityScreen.COLOUR_GOOD, false);
            graphics.drawString(this.font, Component.literal(String.join(", ", roll)),
                    left + 12, y + 11, CityScreen.COLOUR_DIM, false);
        }

        y = top + HEADER + BUTTON_ROWS * ROW + ROLL_HEIGHT;
        graphics.drawString(this.font, Component.translatable("screen.citiesinlife.ledger"),
                left + 12, y, CityScreen.COLOUR_TEXT, false);
        y += LEDGER_LABEL;

        List<LedgerEntry> ledger = hall.ledger();
        if (ledger.isEmpty()) {
            graphics.drawString(this.font,
                    Component.translatable("screen.citiesinlife.ledger_empty"),
                    left + 12, y, CityScreen.COLOUR_DIM, false);
            return;
        }
        // Newest last is how a history reads, but only the last few fit - so the window is the TAIL
        // of the list and a city that has done a lot shows what it did recently.
        int from = Math.max(0, ledger.size() - LEDGER_ROWS);
        for (int i = from; i < ledger.size(); i++) {
            graphics.drawString(this.font, ledger.get(i).describe(), left + 12, y,
                    CityScreen.COLOUR_DIM, false);
            y += LEDGER_ROW_HEIGHT;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
