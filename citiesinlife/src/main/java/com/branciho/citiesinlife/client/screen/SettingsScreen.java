package com.branciho.citiesinlife.client.screen;

import com.branciho.citiesinlife.config.CitiesInLifeClientConfig;
import com.branciho.citiesinlife.config.CitiesInLifeConfig;
import com.branciho.citiesinlife.net.CitiesInLifeNetwork;
import com.branciho.citiesinlife.net.ClientCityCache;
import com.branciho.citiesinlife.net.payload.ModSettingsPayload;
import com.branciho.citiesinlife.net.payload.SetSettingsPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * The mod's settings, from inside the game.
 *
 * <p>This exists because the settings were unreachable for the one person who most wanted them.
 * The config is a SERVER config — it decides what actually gets spawned, so it has to be — and
 * NeoForge will not open a SERVER config's screen on a client that is connected to a server. A
 * player hosting their own world through Essential <em>is</em> connected to a server, so the Mods
 * screen quietly refused, and the only way to turn the citizen cap down was to close the world.
 *
 * <p>Only the owner of the world may change anything. Everybody else can look, which is worth
 * more than it sounds: "citizens are capped at 3" explains a great deal about a world, and the
 * person wondering why the streets are empty is usually not the host.
 */
public class SettingsScreen extends Screen {

    private static final int WIDTH = 320;
    private static final int HEIGHT = 272;
    private static final int ROW = 26;

    private final Screen parent;

    private int citizens;
    private boolean cars;
    private int carDistance;
    private int blast;
    private int steam;
    private boolean opsIgnore;
    private boolean editable;

    /**
     * How loud the mod's machines are, for this player.
     *
     * <p>The one row here that is not the world's. It is read from and written to the client
     * config rather than sent anywhere, which is why it stays usable when everything above it is
     * greyed out: a guest on somebody else's server does not get to change how many citizens the
     * cities have, and absolutely does get to turn the reactor down.
     */
    private int volume;

    public SettingsScreen(Screen parent) {
        super(Component.translatable("screen.citiesinlife.settings"));
        this.parent = parent;
        ModSettingsPayload known = ClientCityCache.settings();
        this.citizens = known == null ? 15 : known.citizensPerCity();
        this.cars = known == null || known.carsEnabled();
        this.carDistance = known == null ? 100 : known.carDistance();
        this.blast = known == null ? 100 : known.nuclearBlastPercent();
        this.steam = known == null ? 100 : known.steamPlumePercent();
        this.opsIgnore = known != null && known.opsIgnoreBorders();
        this.editable = known != null && known.editable();
        this.volume = CitiesInLifeClientConfig.machineVolumePercent();
    }

    @Override
    protected void init() {
        int left = (this.width - WIDTH) / 2;
        int top = (this.height - HEIGHT) / 2;
        int y = top + 40;

        // Five at a time rather than one. The ceiling is fifty now, and thirty-five clicks to get
        // there is a dial nobody turns. Clamped, so +5 from 48 lands on 50 rather than refusing.
        stepper(left, y, -5, 5, () -> citizens,
                v -> citizens = Mth.clamp(v, 0, CitiesInLifeConfig.MAX_CITIZENS));
        y += ROW;
        toggle(left, y, () -> cars, v -> cars = v);
        y += ROW;
        stepper(left, y, -16, 16, () -> carDistance, v -> carDistance = Mth.clamp(v, 32, 512));
        y += ROW;
        stepper(left, y, -25, 25, () -> blast, v -> blast = Mth.clamp(v, 25, 200));
        y += ROW;
        stepper(left, y, -25, 25, () -> steam, v -> steam = Mth.clamp(v, 0, 100));
        y += ROW;
        toggle(left, y, () -> opsIgnore, v -> opsIgnore = v);
        y += ROW;
        // Always active, and saved the moment it is clicked - the Save button beside it sends the
        // world's settings to the server, which is not this row's business and is disabled for
        // most of the people who want this row.
        localStepper(left, y, -10, 10);

        addRenderableWidget(Button.builder(
                        Component.translatable("screen.citiesinlife.back"),
                        button -> this.minecraft.setScreen(parent))
                .bounds(left + 12, top + HEIGHT - 30, 120, 20)
                .build());

        Button save = Button.builder(
                        Component.translatable("screen.citiesinlife.settings_save"),
                        button -> {
                            CitiesInLifeNetwork.sendToServer(new SetSettingsPayload(
                                    citizens, cars, carDistance, blast, steam, opsIgnore));
                            this.minecraft.setScreen(parent);
                        })
                .bounds(left + WIDTH - 132, top + HEIGHT - 30, 120, 20)
                .build();
        save.active = editable;
        addRenderableWidget(save);
    }

    /** A stepper that changes a purely local setting and writes it out on the spot. */
    private void localStepper(int left, int y, int down, int up) {
        int x = left + WIDTH - 16 - 74;
        addLocalStep(x, y, down);
        addLocalStep(x + 40, y, up);
    }

    private void addLocalStep(int x, int y, int by) {
        addRenderableWidget(Button.builder(
                        Component.literal(by < 0 ? String.valueOf(by) : "+" + by),
                        b -> {
                            volume = Mth.clamp(volume + by, 0, 100);
                            CitiesInLifeClientConfig.setMachineVolume(volume);
                        })
                .bounds(x, y, 36, 18)
                .build());
    }

    private void stepper(int left, int y, int down, int up,
                         java.util.function.IntSupplier get,
                         java.util.function.IntConsumer set) {
        int x = left + WIDTH - 16 - 74;
        addStep(x, y, down, get, set, String.valueOf(down));
        addStep(x + 40, y, up, get, set, "+" + up);
    }

    private void addStep(int x, int y, int by, java.util.function.IntSupplier get,
                         java.util.function.IntConsumer set, String label) {
        Button button = Button.builder(Component.literal(label),
                        b -> set.accept(get.getAsInt() + by))
                .bounds(x, y, 36, 18)
                .build();
        button.active = editable;
        addRenderableWidget(button);
    }

    private void toggle(int left, int y, java.util.function.BooleanSupplier get,
                        java.util.function.Consumer<Boolean> set) {
        Button button = Button.builder(
                        Component.translatable(get.getAsBoolean()
                                ? "screen.citiesinlife.setting_on"
                                : "screen.citiesinlife.setting_off"),
                        b -> {
                            set.accept(!get.getAsBoolean());
                            rebuildWidgets();
                        })
                .bounds(left + WIDTH - 16 - 76, y, 76, 18)
                .build();
        button.active = editable;
        addRenderableWidget(button);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int left = (this.width - WIDTH) / 2;
        int top = (this.height - HEIGHT) / 2;

        CityScreen.softDim(graphics, this);
        CityScreen.panel(graphics, left, top, WIDTH, HEIGHT);

        graphics.drawString(this.font, this.title, left + 12, top + 12,
                CityScreen.COLOUR_TEXT, false);
        graphics.fill(left + 12, top + 26, left + WIDTH - 12, top + 27, CityScreen.COLOUR_ACCENT);

        if (!editable) {
            graphics.drawString(this.font,
                    Component.translatable("screen.citiesinlife.settings_readonly"),
                    left + 12, top + 30, CityScreen.COLOUR_BAD, false);
        }

        int y = top + 44;
        label(graphics, left, y, "screen.citiesinlife.setting_citizens", String.valueOf(citizens));
        y += ROW;
        label(graphics, left, y, "screen.citiesinlife.setting_cars", "");
        y += ROW;
        label(graphics, left, y, "screen.citiesinlife.setting_car_distance",
                carDistance + " blocks");
        y += ROW;
        label(graphics, left, y, "screen.citiesinlife.setting_blast", blast + "%");
        y += ROW;
        label(graphics, left, y, "screen.citiesinlife.setting_steam", steam + "%");
        y += ROW;
        label(graphics, left, y, "screen.citiesinlife.setting_ops", "");
        y += ROW;
        label(graphics, left, y, "screen.citiesinlife.setting_volume", volume + "%");

        graphics.drawString(this.font, Component.translatable("screen.citiesinlife.settings_hint"),
                left + 12, top + HEIGHT - 48, CityScreen.COLOUR_DIM, false);
    }

    private void label(GuiGraphics graphics, int left, int y, String key, String value) {
        graphics.drawString(this.font, Component.translatable(key), left + 14, y,
                CityScreen.COLOUR_TEXT, false);
        if (!value.isEmpty()) {
            graphics.drawString(this.font, Component.literal(value),
                    left + WIDTH - 16 - 96 - this.font.width(value), y,
                    CityScreen.COLOUR_ACCENT, false);
        }
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
