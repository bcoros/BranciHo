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

    private static final int PANEL_WIDTH = 300;
    // Two more rows than it started with: power gained a water twin, and both need room for the
    // line that appears underneath when they fall short. Refuse added a third of the same shape.
    private static final int PANEL_HEIGHT = 276;

    private int left;
    private int top;

    public CityScreen() {
        super(Component.translatable("screen.citiesinlife.city"));
    }

    @Override
    protected void init() {
        left = (this.width - PANEL_WIDTH) / 2;
        top = (this.height - PANEL_HEIGHT) / 2;

        // Five across the bottom now, and the panel widened to fit them at the width they already
        // were rather than squeezing five into four's worth of room. The four-pixel gaps are baked
        // into the x offsets below, so any change to the count means re-deriving all of them.
        int buttonWidth = (PANEL_WIDTH - 24 - 16) / 5;
        addRenderableWidget(Button.builder(
                        Component.translatable("screen.citiesinlife.settings"),
                        button -> this.minecraft.setScreen(new SettingsScreen(new CityScreen())))
                .bounds(left + PANEL_WIDTH - 84, top + 8, 72, 16)
                .build());

        addRenderableWidget(Button.builder(
                        Component.translatable("screen.citiesinlife.land"),
                        button -> this.minecraft.setScreen(new LandMapScreen()))
                .bounds(left + 12, top + PANEL_HEIGHT - 30, buttonWidth, 20)
                .build());

        addRenderableWidget(Button.builder(
                        Component.translatable("screen.citiesinlife.neighbours"),
                        button -> this.minecraft.setScreen(new NeighboursScreen()))
                .bounds(left + 16 + buttonWidth, top + PANEL_HEIGHT - 30, buttonWidth, 20)
                .build());

        addRenderableWidget(Button.builder(
                        Component.translatable("screen.citiesinlife.flag"),
                        button -> this.minecraft.setScreen(new FlagScreen(
                                ClientCityCache.city() == null
                                        ? com.branciho.citiesinlife.city.CityFlag.blank()
                                        : ClientCityCache.city().flag(),
                                new CityScreen())))
                .bounds(left + 20 + buttonWidth * 2, top + PANEL_HEIGHT - 30, buttonWidth, 20)
                .build());

        addRenderableWidget(Button.builder(
                        Component.translatable("screen.citiesinlife.missiles"),
                        button -> this.minecraft.setScreen(new MissileMapScreen()))
                .bounds(left + 24 + buttonWidth * 3, top + PANEL_HEIGHT - 30, buttonWidth, 20)
                .build());

        addRenderableWidget(Button.builder(
                        Component.translatable("gui.done"), button -> this.onClose())
                .bounds(left + PANEL_WIDTH - 12 - buttonWidth, top + PANEL_HEIGHT - 30, buttonWidth, 20)
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
        // A creative treasury is not a number worth reading. Saying so outright is more use than a
        // billion with commas in it, and it explains why nothing has a price at the moment.
        y = row(graphics, y, "screen.citiesinlife.treasury",
                city.creativeFunded()
                        ? Component.translatable("screen.citiesinlife.creative_money")
                        : Component.literal(format(city.treasury())),
                city.creativeFunded() || city.treasury() >= 0 ? COLOUR_GOOD : COLOUR_BAD);

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
        // Imported power counts towards the total above, so without this line a city can be fully
        // powered with no generator anywhere in it and no explanation on the screen.
        if (city.powerImported() > 0) {
            graphics.drawString(this.font,
                    Component.translatable("screen.citiesinlife.imported",
                            format(city.powerImported())),
                    left + 12, y, COLOUR_ACCENT, false);
        } else if (!powered) {
            graphics.drawString(this.font,
                    Component.translatable("screen.citiesinlife.power_short"),
                    left + 12, y, COLOUR_BAD, false);
        }
        y += 14;

        boolean watered = city.waterNeeded() == 0 || city.waterSupplied() >= city.waterNeeded();
        boolean tainted = city.waterTainted() > 0;
        y = row(graphics, y, "screen.citiesinlife.water",
                Component.literal(format(city.waterSupplied()) + " / " + format(city.waterNeeded())),
                watered && !tainted ? COLOUR_GOOD : COLOUR_BAD);
        // Ahead of "not enough water", because a city being poisoned by its own mains has a bigger
        // problem than a city that is merely thirsty, and the two can be true at once.
        if (tainted) {
            graphics.drawString(this.font,
                    Component.translatable("screen.citiesinlife.water_tainted", city.waterTainted()),
                    left + 12, y, COLOUR_BAD, false);
        } else if (city.waterImported() > 0) {
            graphics.drawString(this.font,
                    Component.translatable("screen.citiesinlife.imported",
                            format(city.waterImported())),
                    left + 12, y, COLOUR_ACCENT, false);
        } else if (!watered) {
            graphics.drawString(this.font,
                    Component.translatable("screen.citiesinlife.water_short"),
                    left + 12, y, COLOUR_BAD, false);
        }
        y += 14;

        // Sewage reads the same way round as water on purpose - handled over produced - even though
        // it is the one utility you would rather have less of. Two figures on the same panel that
        // mean opposite things is how a player misreads their own city.
        boolean drained = city.sewageProduced() == 0
                || city.sewageHandled() >= city.sewageProduced();
        y = row(graphics, y, "screen.citiesinlife.sewage",
                Component.literal(format(city.sewageHandled()) + " / " + format(city.sewageProduced())),
                drained ? COLOUR_GOOD : COLOUR_BAD);
        if (!drained) {
            graphics.drawString(this.font,
                    Component.translatable("screen.citiesinlife.sewage_short"),
                    left + 12, y, COLOUR_BAD, false);
        }
        y += 14;

        // Refuse is the one utility that runs backwards - lower is better - so it is shown as a
        // fraction of what the city can stand rather than as a bare number nobody could read.
        boolean clean = city.refuse() <= city.refuseTolerance();
        y = row(graphics, y, "screen.citiesinlife.refuse",
                Component.literal(format(city.refuse()) + " / " + format(city.refuseTolerance())),
                clean ? COLOUR_GOOD : COLOUR_BAD);
        if (!clean) {
            graphics.drawString(this.font,
                    Component.translatable("screen.citiesinlife.refuse_high"),
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
