package com.branciho.citiesinlife.client.screen;

import com.branciho.citiesinlife.city.City;
import com.branciho.citiesinlife.net.CitiesInLifeNetwork;
import com.branciho.citiesinlife.net.payload.DiplomacyPayload;
import com.branciho.citiesinlife.net.payload.NeighbourCitiesPayload;
import com.branciho.citiesinlife.net.payload.RequestCityPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * What you charge one neighbour for your surplus.
 *
 * <p>Steppers rather than a text box, and per unit per step rather than a lump sum. A nuclear plant
 * makes several hundred units a step and the city tick runs every ten seconds, so the difference
 * between charging one and charging three is the difference between a favour and an industry — a
 * number that sensitive wants to be nudged and watched, not typed and guessed at.
 *
 * <p>Zero is allowed and means exactly what it says. Giving a neighbour free power is a legitimate
 * thing to want to do, and making it impossible would only mean everyone charged one and ignored it.
 */
public class UtilityPriceScreen extends Screen {

    private static final int WIDTH = 280;
    private static final int HEIGHT = 170;

    private final NeighbourCitiesPayload.Entry entry;
    private final Screen parent;

    private int power;
    private int water;

    public UtilityPriceScreen(NeighbourCitiesPayload.Entry entry, Screen parent) {
        super(Component.translatable("screen.citiesinlife.set_prices"));
        this.entry = entry;
        this.parent = parent;
        this.power = entry.yourPowerPrice();
        this.water = entry.yourWaterPrice();
    }

    @Override
    protected void init() {
        int left = (this.width - WIDTH) / 2;
        int top = (this.height - HEIGHT) / 2;

        stepper(left, top + 56, true);
        stepper(left, top + 88, false);

        addRenderableWidget(Button.builder(
                        Component.translatable("screen.citiesinlife.back"),
                        button -> this.minecraft.setScreen(parent))
                .bounds(left + 16, top + HEIGHT - 30, 110, 20)
                .build());

        addRenderableWidget(Button.builder(
                        Component.translatable("screen.citiesinlife.save_prices"),
                        button -> {
                            CitiesInLifeNetwork.sendToServer(new DiplomacyPayload(entry.cityId(),
                                    DiplomacyPayload.ACTION_SET_PRICES, power, water));
                            CitiesInLifeNetwork.sendToServer(new RequestCityPayload());
                            this.minecraft.setScreen(parent);
                        })
                .bounds(left + WIDTH - 126, top + HEIGHT - 30, 110, 20)
                .build());
    }

    private void stepper(int left, int y, boolean forPower) {
        int x = left + WIDTH - 16 - 130;
        addStep(x, y, -10, forPower, "-10");
        addStep(x + 34, y, -1, forPower, "-1");
        addStep(x + 68, y, 1, forPower, "+1");
        addStep(x + 102, y, 10, forPower, "+10");
    }

    private void addStep(int x, int y, int by, boolean forPower, String label) {
        addRenderableWidget(Button.builder(Component.literal(label), button -> {
                    if (forPower) {
                        power = Mth.clamp(power + by, 0, City.MAX_PRICE);
                    } else {
                        water = Mth.clamp(water + by, 0, City.MAX_PRICE);
                    }
                })
                .bounds(x, y, 30, 18)
                .build());
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int left = (this.width - WIDTH) / 2;
        int top = (this.height - HEIGHT) / 2;

        CityScreen.softDim(graphics, this);
        CityScreen.panel(graphics, left, top, WIDTH, HEIGHT);

        graphics.drawString(this.font, this.title, left + 16, top + 12,
                CityScreen.COLOUR_TEXT, false);
        graphics.fill(left + 16, top + 26, left + WIDTH - 16, top + 27, CityScreen.COLOUR_ACCENT);

        graphics.drawString(this.font,
                Component.translatable("screen.citiesinlife.prices_for", entry.name()),
                left + 16, top + 34, CityScreen.COLOUR_DIM, false);

        graphics.drawString(this.font,
                Component.translatable("screen.citiesinlife.price_power", power),
                left + 16, top + 60, CityScreen.COLOUR_TEXT, false);
        graphics.drawString(this.font,
                Component.translatable("screen.citiesinlife.price_water", water),
                left + 16, top + 92, CityScreen.COLOUR_TEXT, false);

        graphics.drawString(this.font,
                Component.translatable("screen.citiesinlife.price_hint"),
                left + 16, top + 118, CityScreen.COLOUR_DIM, false);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
