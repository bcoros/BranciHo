package com.branciho.citiesinlife.client.screen;

import com.branciho.citiesinlife.city.AlertLevel;
import com.branciho.citiesinlife.city.City;
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
import net.minecraft.util.Mth;

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
    private static final int BUTTON_ROWS = 5;
    private static final int ROLL_HEIGHT = 24;
    private static final int LEDGER_LABEL = 14;

    /**
     * How many lines of history are on screen at once, and the bounds on that.
     *
     * <p>Worked out from the actual window rather than fixed, because this panel has grown a row
     * of buttons in every update it has ever had and a fixed height ran off the bottom of the
     * screen at GUI scale 4 — where 1080p leaves only 270 pixels to draw in. The ledger is the one
     * part of the panel that can honestly be shorter: it scrolls.
     */
    private static final int MIN_LEDGER_ROWS = 3;
    private static final int MAX_LEDGER_ROWS = 8;
    private static final int LEDGER_ROW_HEIGHT = 11;
    private static final int FOOTER = 30;

    /** Re-asked on a timer, so a meeting filling up shows without reopening the panel. */
    private static final int REFRESH_TICKS = 20;

    private int left;
    private int top;
    private int seenRevision = -1;
    private int sinceAsked;
    private EditBox addressBox;

    /**
     * How far back through the ledger the reader has scrolled, in rows.
     *
     * <p>Counted from the NEWEST end rather than the oldest, which is what makes a live panel
     * behave. At rest it is zero and the newest lines are on screen; a line arriving while you sit
     * there pushes the view along with it instead of shunting the whole history up by one under
     * your eyes. Scroll back and it holds the older lines steady, because the distance from the
     * end only changes when you change it.
     */
    private int scrollBack;

    /**
     * What the player has typed so far.
     *
     * <p>Held outside the widget because a rebuild throws the {@link EditBox} away and makes a new
     * empty one. A meeting filling up while you are composing an announcement is exactly when a
     * rebuild happens, and losing the sentence to it would be maddening.
     */
    private String draft = "";

    public CityHallScreen() {
        super(Component.translatable("screen.citiesinlife.city_hall"));
    }

    /** Everything on the panel that is not history. */
    private static int fixedHeight() {
        return HEADER + BUTTON_ROWS * ROW + ROLL_HEIGHT + LEDGER_LABEL + FOOTER;
    }

    private int ledgerRows() {
        int room = (this.height - 8 - fixedHeight()) / LEDGER_ROW_HEIGHT;
        return Mth.clamp(room, MIN_LEDGER_ROWS, MAX_LEDGER_ROWS);
    }

    private int panelHeight() {
        return fixedHeight() + ledgerRows() * LEDGER_ROW_HEIGHT;
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
        // The detail. Hire on the left, let one go on the right, and the count between them, so
        // there is never a moment where you have to guess how many you are paying for.
        int wide = (PANEL_WIDTH - 24 - 8) * 2 / 5;
        Button hire = Button.builder(
                        Component.translatable("screen.citiesinlife.hire_guard",
                                City.HIRE_GUARD_COST),
                        press -> send("hire_guard", ""))
                .bounds(left + 12, y, wide, 20)
                .build();
        hire.active = open && hall.guards() < City.MAX_GUARDS;
        addRenderableWidget(hire);

        Button dismissGuard = Button.builder(
                        Component.translatable("screen.citiesinlife.dismiss_guard"),
                        press -> send("dismiss_guard", ""))
                .bounds(left + PANEL_WIDTH - 12 - wide, y, wide, 20)
                .build();
        dismissGuard.active = open && hall.guards() > 0;
        addRenderableWidget(dismissGuard);

        y += ROW;
        // The mute gets a row to itself rather than sharing one. It is the only control here that
        // can hide a genuine emergency, and a button that does that should not be one of a pair a
        // player might hit by accident.
        Button hush = Button.builder(
                        Component.translatable(hall.hushed()
                                ? "screen.citiesinlife.hush_off"
                                : "screen.citiesinlife.hush_on"),
                        press -> send("hush", ""))
                .bounds(left + 12, y, PANEL_WIDTH - 24, 20)
                .build();
        hush.active = open;
        addRenderableWidget(hush);

        y += ROW;
        addressBox = new EditBox(this.font, left + 12, y, PANEL_WIDTH - 24 - third - 4, 20,
                Component.translatable("screen.citiesinlife.address_hint"));
        addressBox.setMaxLength(CityHallActionPayload.MAX_DETAIL);
        addressBox.setEditable(open);
        addressBox.setValue(draft);
        addressBox.setResponder(typed -> draft = typed);
        addRenderableWidget(addressBox);

        Button announce = Button.builder(
                        Component.translatable("screen.citiesinlife.address_send"),
                        press -> {
                            send("address", addressBox.getValue());
                            draft = "";
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
        if (hall.hushed()) {
            Component muted = Component.translatable("screen.citiesinlife.hushed");
            graphics.drawString(this.font, muted,
                    left + PANEL_WIDTH - 12 - this.font.width(muted), top + 29,
                    CityScreen.COLOUR_BAD, false);
        } else if (!hall.inHall()) {
            Component warn = Component.translatable("screen.citiesinlife.not_in_hall");
            graphics.drawString(this.font, warn,
                    left + PANEL_WIDTH - 12 - this.font.width(warn), top + 29,
                    CityScreen.COLOUR_BAD, false);
        }

        // Numbers only. The two buttons either side of it leave sixty-six pixels between them,
        // and a full sentence would be drawn straight over both of them.
        Component detail = Component.translatable("screen.citiesinlife.guards_on",
                hall.guards(), City.MAX_GUARDS);
        graphics.drawCenteredString(this.font, detail, left + PANEL_WIDTH / 2,
                top + HEADER + ROW * 2 + 6,
                hall.guards() > 0 ? CityScreen.COLOUR_GOOD : CityScreen.COLOUR_DIM);

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
            // Sixteen names do not fit on a 320px panel, and an unwrapped line would simply run off
            // both sides of it. One wrapped line's worth, with the rest implied by the count above.
            List<net.minecraft.util.FormattedCharSequence> names =
                    this.font.split(Component.literal(String.join(", ", roll)), PANEL_WIDTH - 24);
            if (!names.isEmpty()) {
                graphics.drawString(this.font, names.get(0), left + 12, y + 11,
                        CityScreen.COLOUR_DIM, false);
            }
        }

        graphics.drawString(this.font, Component.translatable("screen.citiesinlife.ledger"),
                left + 12, top + HEADER + BUTTON_ROWS * ROW + ROLL_HEIGHT,
                CityScreen.COLOUR_TEXT, false);
        y = ledgerTop();

        List<LedgerEntry> ledger = hall.ledger();
        if (ledger.isEmpty()) {
            graphics.drawString(this.font,
                    Component.translatable("screen.citiesinlife.ledger_empty"),
                    left + 12, y, CityScreen.COLOUR_DIM, false);
            return;
        }
        // Clamped here rather than where the wheel is read, because the list this is an offset into
        // arrives from the server and can shrink between one frame and the next.
        scrollBack = Mth.clamp(scrollBack, 0, hiddenRows(ledger.size()));
        int rows = ledgerRows();
        int last = ledger.size() - scrollBack;
        int from = Math.max(0, last - rows);
        for (int i = from; i < last; i++) {
            graphics.drawString(this.font, ledger.get(i).describe(), left + 12, y,
                    CityScreen.COLOUR_DIM, false);
            y += LEDGER_ROW_HEIGHT;
        }
        drawScrollbar(graphics, ledger.size(), from);
    }

    /** How many rows of history do not fit on screen, and so have to be scrolled to. */
    private int hiddenRows(int entries) {
        return Math.max(0, entries - ledgerRows());
    }

    /**
     * A bar down the side of the ledger saying how much more there is.
     *
     * <p>Only drawn when there is more, so a young city with four lines of history does not get a
     * scrollbar telling it that four lines is all four lines.
     */
    private void drawScrollbar(GuiGraphics graphics, int entries, int from) {
        int hidden = hiddenRows(entries);
        if (hidden <= 0) {
            return;
        }
        int top = ledgerTop();
        int rows = ledgerRows();
        int height = rows * LEDGER_ROW_HEIGHT;
        int x = left + PANEL_WIDTH - 16;
        graphics.fill(x, top, x + 3, top + height, CityScreen.COLOUR_ACCENT & 0x40FFFFFF);
        int grip = Math.max(8, height * rows / entries);
        int travel = height - grip;
        int at = top + (travel * from) / hidden;
        graphics.fill(x, at, x + 3, at + grip, CityScreen.COLOUR_ACCENT);
    }

    /** The y the first line of history is drawn at. */
    private int ledgerTop() {
        return top + HEADER + BUTTON_ROWS * ROW + ROLL_HEIGHT + LEDGER_LABEL;
    }

    /**
     * The wheel walks the ledger.
     *
     * <p>Taken unconditionally rather than only over the ledger's own rectangle: there is nothing
     * else on this panel a wheel could mean, and asking a player to find the exact strip of pixels
     * that accepts scrolling is the kind of thing that reads as the feature not working.
     */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        List<LedgerEntry> ledger = ClientCityCache.cityHall().ledger();
        int hidden = hiddenRows(ledger.size());
        if (hidden > 0) {
            scrollBack = Mth.clamp(scrollBack + (int) Math.signum(scrollY), 0, hidden);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
