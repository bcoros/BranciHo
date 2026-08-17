package com.branciho.citiesinlife.client.screen;

import com.branciho.citiesinlife.net.ClientCityCache;
import com.branciho.citiesinlife.net.payload.CitySyncPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The city panel: what the city is worth, who lives in it, and the way through to its land.
 *
 * <p>Read-only apart from the buttons. Everything shown comes from the last server sync, so nothing
 * here can disagree with what the server believes.
 */
public class CityScreen extends Screen {

    static final int COLOUR_PANEL = 0xD00B0F16;
    static final int COLOUR_BORDER = 0x66FFFFFF;
    static final int COLOUR_ACCENT = 0xFF16E0D0;
    static final int COLOUR_TEXT = 0xFFE6ECF2;
    static final int COLOUR_DIM = 0xFF8C97A3;
    static final int COLOUR_GOOD = 0xFF66E576;
    static final int COLOUR_BAD = 0xFFFF6B6B;

    private static final int PANEL_WIDTH = 244;
    // Two more rows than it started with: power gained a water twin, and both need room for the
    // line that appears underneath when they fall short.
    private static final int PANEL_HEIGHT = 234;

    private int left;
    private int top;

    public CityScreen() {
        super(Component.translatable("screen.citiesinlife.city"));
    }

    @Override
    protected void init() {
        left = (this.width - PANEL_WIDTH) / 2;
        top = (this.height - PANEL_HEIGHT) / 2;

        addRenderableWidget(Button.builder(
                        Component.translatable("screen.citiesinlife.land"),
                        button -> this.minecraft.setScreen(new LandMapScreen()))
                .bounds(left + 12, top + PANEL_HEIGHT - 30, 104, 20)
                .build());

        addRenderableWidget(Button.builder(
                        Component.translatable("gui.done"), button -> this.onClose())
                .bounds(left + PANEL_WIDTH - 116, top + PANEL_HEIGHT - 30, 104, 20)
                .build());
    }

    /**
     * The panel is painted as part of the background rather than in {@code render}, because
     * {@link Screen#render} draws the background first and the widgets afterwards. Drawing the panel
     * in {@code render} would put it on top of its own buttons.
     */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        softDim(graphics, this);
        panel(graphics, left, top, PANEL_WIDTH, PANEL_HEIGHT);

        CitySyncPayload city = ClientCityCache.city();
        if (city == null || !city.hasCity()) {
            graphics.drawCenteredString(this.font,
                    Component.translatable("screen.citiesinlife.no_city"),
                    left + PANEL_WIDTH / 2, top + 60, COLOUR_TEXT);
            graphics.drawCenteredString(this.font,
                    Component.translatable("screen.citiesinlife.no_city_hint"),
                    left + PANEL_WIDTH / 2, top + 76, COLOUR_DIM);
            return;
        }

        graphics.drawString(this.font, Component.literal(city.cityName()),
                left + 12, top + 10, COLOUR_TEXT, false);
        graphics.fill(left + 12, top + 22, left + PANEL_WIDTH - 12, top + 23, COLOUR_ACCENT);

        int y = top + 32;
        y = row(graphics, y, "screen.citiesinlife.treasury",
                Component.literal(format(city.treasury())),
                city.treasury() >= 0 ? COLOUR_GOOD : COLOUR_BAD);

        // Population against housing, because "44" alone looks broken next to a tower you just
        // registered. Seeing 44 of 370 says plainly that the block is filling up.
        y = row(graphics, y, "screen.citiesinlife.population",
                Component.literal(format(city.population()) + " / " + format(city.housing())),
                COLOUR_TEXT);
        y = row(graphics, y, "screen.citiesinlife.employed",
                Component.literal(format(city.employed()) + " / " + format(city.jobs())), COLOUR_TEXT);

        int unemployed = Math.max(0, city.population() - city.employed());
        y = row(graphics, y, "screen.citiesinlife.unemployed",
                Component.literal(format(unemployed)), unemployed > 0 ? COLOUR_BAD : COLOUR_GOOD);

        boolean powered = city.powerNeeded() == 0 || city.powerProduced() >= city.powerNeeded();
        y = row(graphics, y, "screen.citiesinlife.power",
                Component.literal(format(city.powerProduced()) + " / " + format(city.powerNeeded())),
                powered ? COLOUR_GOOD : COLOUR_BAD);
        if (!powered) {
            graphics.drawString(this.font,
                    Component.translatable("screen.citiesinlife.power_short"),
                    left + 12, y, COLOUR_BAD, false);
        }
        y += 14;

        boolean watered = city.waterNeeded() == 0 || city.waterSupplied() >= city.waterNeeded();
        y = row(graphics, y, "screen.citiesinlife.water",
                Component.literal(format(city.waterSupplied()) + " / " + format(city.waterNeeded())),
                watered ? COLOUR_GOOD : COLOUR_BAD);
        if (!watered) {
            graphics.drawString(this.font,
                    Component.translatable("screen.citiesinlife.water_short"),
                    left + 12, y, COLOUR_BAD, false);
        }
        y += 14;

        y = row(graphics, y, "screen.citiesinlife.territory",
                Component.translatable("screen.citiesinlife.chunks", ClientCityCache.claimedCount()),
                COLOUR_TEXT);
        row(graphics, y, "screen.citiesinlife.next_claim",
                Component.literal(format(city.nextClaimCost())), COLOUR_DIM);
    }

    private int row(GuiGraphics graphics, int y, String key, Component value, int valueColour) {
        graphics.drawString(this.font, Component.translatable(key), left + 12, y, COLOUR_DIM, false);
        int width = this.font.width(value);
        graphics.drawString(this.font, value, left + PANEL_WIDTH - 12 - width, y, valueColour, false);
        return y + 14;
    }

    /**
     * A light wash over the world instead of the vanilla blur.
     *
     * <p>These screens are meant to sit over the city you are looking at, not hide it — you claim
     * land while looking at the land. Just enough darkening to keep the text readable.
     */
    static void softDim(GuiGraphics graphics, Screen screen) {
        graphics.fill(0, 0, screen.width, screen.height, 0x50000000);
    }

    /** The shared panel look, reused by the other screens in this package. */
    static void panel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, COLOUR_PANEL);
        graphics.fill(x, y, x + width, y + 1, COLOUR_BORDER);
        graphics.fill(x, y + height - 1, x + width, y + height, COLOUR_BORDER);
        graphics.fill(x, y, x + 1, y + height, COLOUR_BORDER);
        graphics.fill(x + width - 1, y, x + width, y + height, COLOUR_BORDER);
    }

    /** Thousands separators, because a treasury is unreadable without them. */
    static String format(long value) {
        return String.format("%,d", value);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
