package com.branciho.citiesinlife.client.screen;

import com.branciho.citiesinlife.net.CitiesInLifeNetwork;
import com.branciho.citiesinlife.net.ClientCityCache;
import com.branciho.citiesinlife.net.payload.ReactorSyncPayload;
import com.branciho.citiesinlife.net.payload.RequestReactorPayload;
import com.branciho.citiesinlife.nuclear.CoolingPort;
import com.branciho.citiesinlife.nuclear.ReactorFault;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * The control room, drawn as a computer terminal rather than as the mod's usual panel.
 *
 * <p>That difference is deliberate. Every other window in this mod is a city management panel and
 * looks like one; this is the one place you are reading instruments while something is going wrong,
 * and it should feel like sitting at a console — dark, monospaced, scanlined, with the numbers big
 * enough to read from across a room.
 *
 * <p>Two things on it do the real work. CORE is shown beside TARGET, so moving a lever tells you
 * where the core is heading <em>this step</em> while it starts walking there — you never have to
 * discover a threshold by dying at it. And the risk line names the lever to move rather than the
 * symptom, which is the whole difference between a monitor and a thermometer.
 */
public class ReactorScreen extends Screen {

    private static final int PANEL_WIDTH = 340;
    private static final int PANEL_HEIGHT = 232;

    /** How often the screen re-asks the server. Half a second: smooth without being chatty. */
    private static final int REFRESH_TICKS = 10;

    // A phosphor palette. Everything is a shade of the same green except the two alarm states.
    private static final int SCREEN_BG = 0xF2060B08;
    private static final int FRAME = 0xFF1E3A26;
    private static final int TEXT = 0xFF7CF0A8;
    private static final int DIM = 0xFF3E8A5C;
    private static final int LABEL = 0xFF58B47C;
    private static final int WARN = 0xFFFFB03A;
    private static final int BAD = 0xFFFF5540;
    private static final int SCANLINE = 0x14000000;

    private final BlockPos monitor;
    private int left;
    private int top;
    private int ticks;

    public ReactorScreen(BlockPos monitor) {
        super(Component.translatable("screen.citiesinlife.reactor"));
        this.monitor = monitor;
    }

    @Override
    protected void init() {
        left = (this.width - PANEL_WIDTH) / 2;
        top = (this.height - PANEL_HEIGHT) / 2;
        ask();
    }

    private void ask() {
        CitiesInLifeNetwork.sendToServer(new RequestReactorPayload(monitor));
    }

    @Override
    public void tick() {
        // Live, because a reactor that only updated when you reopened the window would be a
        // photograph rather than an instrument.
        if (++ticks % REFRESH_TICKS == 0) {
            ask();
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        CityScreen.softDim(graphics, this);
        ReactorSyncPayload r = ClientCityCache.reactor();

        graphics.fill(left - 2, top - 2, left + PANEL_WIDTH + 2, top + PANEL_HEIGHT + 2, FRAME);
        graphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, SCREEN_BG);
        // Scanlines. Cheap, and the single thing that turns a dark box into a CRT.
        for (int y = top; y < top + PANEL_HEIGHT; y += 2) {
            graphics.fill(left, y, left + PANEL_WIDTH, y + 1, SCANLINE);
        }

        if (!r.present()) {
            graphics.drawString(this.font, Component.translatable("screen.citiesinlife.reactor_none"),
                    left + 12, top + 16, BAD, false);
            return;
        }

        int y = top + 8;
        graphics.drawString(this.font, "CITIES IN LIFE // REACTOR CONTROL", left + 10, y, DIM, false);
        y += 10;
        graphics.drawString(this.font, r.name().toUpperCase(java.util.Locale.ROOT),
                left + 10, y, TEXT, false);
        graphics.fill(left + 10, y + 10, left + PANEL_WIDTH - 10, y + 11, FRAME);
        y += 18;

        if (r.melting()) {
            // Nothing else on the screen matters now, and pretending otherwise would be cruel.
            graphics.fill(left + 10, y, left + PANEL_WIDTH - 10, y + 34, 0x40FF2020);
            graphics.drawString(this.font, "*** MELTDOWN IN PROGRESS ***", left + 18, y + 6, BAD, false);
            graphics.drawString(this.font,
                    Component.translatable("screen.citiesinlife.reactor_melting"),
                    left + 18, y + 20, WARN, false);
            return;
        }

        // ---- the two gauges that kill you --------------------------------
        y = gauge(graphics, y, "CORE", r.temperature(), 1000, 720, 850, "C",
                "TARGET " + r.targetTemperature() + "C");
        y = sparkline(graphics, y, r.history(), 0, 1000);
        y += 4;
        y = gauge(graphics, y, "PRESSURE", r.pressure(), 300, 170, 220, "bar", null);
        y = sparkline(graphics, y, r.history(), 8, 320);
        y += 6;

        // ---- everything else ---------------------------------------------
        int col = left + 10;
        int right = left + PANEL_WIDTH / 2 + 6;
        int rowY = y;
        rowY = row(graphics, col, rowY, "OUTPUT", r.output() + " units/step", TEXT);
        rowY = row(graphics, col, rowY, "TURBINE", r.dial() == 0
                ? "OFF" : (r.dial() * 25) + "%", r.dial() == 0 ? WARN : TEXT);
        rowY = row(graphics, col, rowY, "RODS IN", r.insertion() + " / 4", TEXT);
        row(graphics, col, rowY, "FUEL", r.fuelPercent() + "%  (~" + r.minutesLeft() + " min)",
                r.fuelPercent() < 15 ? WARN : TEXT);

        int rightY = y;
        rightY = row(graphics, right, rightY, "LOOP", r.loopPercent() + "%",
                r.loopPercent() < 100 ? WARN : TEXT);
        rightY = row(graphics, right, rightY, "COOLER", r.cooler() ? "ON" : "off",
                r.cooler() ? TEXT : DIM);
        rightY = row(graphics, right, rightY, "HEAT", r.heat() ? "ON" : "off",
                r.heat() ? WARN : DIM);
        row(graphics, right, rightY, "RELIEF", r.vent() ? "OPEN" : "shut", r.vent() ? TEXT : DIM);
        y = rowY + 16;

        // ---- the four ports, so a clog has somewhere to point --------------
        graphics.drawString(this.font, "COOLING PORTS", left + 10, y, LABEL, false);
        y += 11;
        int x = left + 10;
        for (CoolingPort port : CoolingPort.values()) {
            int clog = r.clog()[port.ordinal()];
            int colour = clog >= 100 ? BAD : (clog > 0 ? WARN : DIM);
            graphics.fill(x, y, x + 72, y + 6, 0xFF10221A);
            graphics.fill(x, y, x + Math.min(72, 72 * clog / 100), y + 6, colour);
            graphics.drawString(this.font, port.id().substring(0, Math.min(9, port.id().length())),
                    x, y + 8, colour, false);
            x += 80;
        }
        y += 24;

        // ---- the risk line -------------------------------------------------
        graphics.fill(left + 10, y, left + PANEL_WIDTH - 10, y + 1, FRAME);
        y += 6;
        if (r.fault() < 0) {
            graphics.drawString(this.font, "> ALL SYSTEMS NOMINAL", left + 10, y, TEXT, false);
        } else {
            ReactorFault fault = ReactorFault.values()[
                    Math.min(r.fault(), ReactorFault.values().length - 1)];
            int colour = fault.kind() == ReactorFault.Kind.NOTICE ? DIM
                    : (fault.stopsTheCore() ? BAD : WARN);
            graphics.drawString(this.font, Component.literal("> ").append(fault.describe()),
                    left + 10, y, colour, false);
            if (r.faultCount() > 0) {
                graphics.drawString(this.font,
                        Component.translatable("screen.citiesinlife.reactor_and_more",
                                r.faultCount()),
                        left + 10, y + 11, DIM, false);
            }
        }
    }

    /** One instrument: a label, a big number, a bar, and the two lines it must not cross. */
    private int gauge(GuiGraphics graphics, int y, String label, int value, int scale,
                      int warnAt, int badAt, String unit, String note) {
        int colour = value >= badAt ? BAD : (value >= warnAt ? WARN : TEXT);
        graphics.drawString(this.font, label, left + 10, y, LABEL, false);
        String read = value + unit;
        graphics.drawString(this.font, read, left + 78, y, colour, false);
        if (note != null) {
            int w = this.font.width(note);
            graphics.drawString(this.font, note, left + PANEL_WIDTH - 10 - w, y, DIM, false);
        }
        y += 11;

        int barLeft = left + 10;
        int barRight = left + PANEL_WIDTH - 10;
        int span = barRight - barLeft;
        graphics.fill(barLeft, y, barRight, y + 6, 0xFF10221A);
        graphics.fill(barLeft, y, barLeft + Math.min(span, span * value / scale), y + 6, colour);
        // The two thresholds, marked on the bar itself, so they are learnable without a manual.
        graphics.fill(barLeft + span * warnAt / scale, y - 1, barLeft + span * warnAt / scale + 1,
                y + 7, WARN);
        graphics.fill(barLeft + span * badAt / scale, y - 1, barLeft + span * badAt / scale + 1,
                y + 7, BAD);
        return y + 10;
    }

    /**
     * Eighty seconds of trend, drawn as bars.
     *
     * <p>The cheapest thing on the screen and the one that teaches the most: you see the climb
     * before it is a problem, which is what turns watching into understanding.
     */
    private int sparkline(GuiGraphics graphics, int y, int[] history, int from, int scale) {
        int barLeft = left + 10;
        int width = (PANEL_WIDTH - 20) / 8;
        for (int i = 0; i < 8; i++) {
            int value = history[from + i];
            int h = Math.max(1, Math.min(12, 12 * value / scale));
            int x = barLeft + i * width;
            graphics.fill(x, y + 12 - h, x + width - 2, y + 12, i == 7 ? LABEL : FRAME);
        }
        return y + 14;
    }

    private int row(GuiGraphics graphics, int x, int y, String label, String value, int colour) {
        graphics.drawString(this.font, label, x, y, LABEL, false);
        graphics.drawString(this.font, value, x + 64, y, colour, false);
        return y + 11;
    }

    @Override
    public boolean isPauseScreen() {
        // A reactor does not stop because you opened a window at it. Pausing here in single player
        // would let a player freeze a rising core and think about it, which is not the game.
        return false;
    }
}
