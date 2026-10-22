package com.branciho.citiesinlife.client.screen;

import com.branciho.citiesinlife.city.CityFlag;
import com.branciho.citiesinlife.net.CitiesInLifeNetwork;
import com.branciho.citiesinlife.net.payload.RequestCityPayload;
import com.branciho.citiesinlife.net.payload.SetFlagPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;

/**
 * Forty squares and sixteen colours.
 *
 * <p>Simple on purpose. Pick a colour, click squares, and there is your flag — no layers, no
 * shapes, no undo stack, nothing to learn. The three presets exist because a tricolour is what most
 * people want and painting one square at a time to get it would make the whole feature feel like
 * work.
 *
 * <p>Nothing is sent until Save, so a flag half way through being redrawn never reaches the poles.
 */
public class FlagScreen extends Screen {

    private static final int WIDTH = 300;
    private static final int HEIGHT = 300;

    /** How big one square of the flag is drawn, in pixels. */
    private static final int CELL = 22;

    /** One swatch of the palette. Same width as a flag square, so the two grids line up. */
    private static final int SWATCH = 22;
    private static final int SWATCH_HEIGHT = 20;

    private final Screen parent;
    private byte[] cells;
    private DyeColor brush = DyeColor.RED;

    private int gridLeft;
    private int gridTop;
    private int paletteTop;

    public FlagScreen(byte[] starting, Screen parent) {
        super(Component.translatable("screen.citiesinlife.flag"));
        this.cells = CityFlag.sanitise(starting);
        this.parent = parent;
    }

    @Override
    protected void init() {
        int left = (this.width - WIDTH) / 2;
        int top = (this.height - HEIGHT) / 2;
        gridLeft = left + (WIDTH - CityFlag.WIDTH * CELL) / 2;
        gridTop = top + 48;
        paletteTop = gridTop + CityFlag.HEIGHT * CELL + 10;

        // The palette is NOT made of buttons. It was, and every swatch came out grey: a Screen
        // draws its background first and its widgets afterwards, so sixteen grey buttons were
        // painted straight over the sixteen colours underneath them. Drawn and hit-tested by hand
        // here, the same way the flag grid already is.

        int presetsY = paletteTop + 2 * SWATCH_HEIGHT + 24;
        preset(left + 12, presetsY, "screen.citiesinlife.flag_bands",
                () -> CityFlag.horizontal(brush, DyeColor.WHITE, brush));
        preset(left + 108, presetsY, "screen.citiesinlife.flag_stripes",
                () -> CityFlag.vertical(brush, DyeColor.WHITE, brush));
        preset(left + 204, presetsY, "screen.citiesinlife.flag_cross",
                () -> CityFlag.cross(brush, DyeColor.WHITE));

        addRenderableWidget(Button.builder(
                        Component.translatable("screen.citiesinlife.back"),
                        button -> this.minecraft.setScreen(parent))
                .bounds(left + 12, top + HEIGHT - 28, 110, 20)
                .build());

        addRenderableWidget(Button.builder(
                        Component.translatable("screen.citiesinlife.flag_save"),
                        button -> {
                            CitiesInLifeNetwork.sendToServer(new SetFlagPayload(cells));
                            CitiesInLifeNetwork.sendToServer(new RequestCityPayload());
                            this.minecraft.setScreen(parent);
                        })
                .bounds(left + WIDTH - 122, top + HEIGHT - 28, 110, 20)
                .build());
    }

    private void preset(int x, int y, String key, java.util.function.Supplier<byte[]> pattern) {
        addRenderableWidget(Button.builder(Component.translatable(key),
                        button -> cells = pattern.get())
                .bounds(x, y, 84, 20)
                .build());
    }

    /**
     * Painting, on press and on drag.
     *
     * <p>Dragging matters more than it sounds: filling a band one careful click at a time is the
     * difference between designing a flag and doing data entry.
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (paint(mouseX, mouseY) || pick(mouseX, mouseY)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX,
                                double dragY) {
        if (paint(mouseX, mouseY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    private boolean paint(double mouseX, double mouseY) {
        int x = (int) ((mouseX - gridLeft) / CELL);
        int y = (int) ((mouseY - gridTop) / CELL);
        if (mouseX < gridLeft || mouseY < gridTop
                || x < 0 || x >= CityFlag.WIDTH || y < 0 || y >= CityFlag.HEIGHT) {
            return false;
        }
        cells[y * CityFlag.WIDTH + x] = (byte) brush.getId();
        return true;
    }

    /** Picking a colour. Only on a press, so dragging over the palette does not change the brush. */
    private boolean pick(double mouseX, double mouseY) {
        int x = (int) ((mouseX - gridLeft) / SWATCH);
        int y = (int) ((mouseY - paletteTop) / SWATCH_HEIGHT);
        if (mouseX < gridLeft || mouseY < paletteTop || x < 0 || x >= 8 || y < 0 || y >= 2) {
            return false;
        }
        brush = DyeColor.byId(y * 8 + x);
        return true;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int left = (this.width - WIDTH) / 2;
        int top = (this.height - HEIGHT) / 2;

        CityScreen.softDim(graphics, this);
        CityScreen.panel(graphics, left, top, WIDTH, HEIGHT);

        graphics.drawString(this.font, this.title, left + 12, top + 10,
                CityScreen.COLOUR_TEXT, false);
        graphics.fill(left + 12, top + 24, left + WIDTH - 12, top + 25, CityScreen.COLOUR_ACCENT);

        for (int y = 0; y < CityFlag.HEIGHT; y++) {
            for (int x = 0; x < CityFlag.WIDTH; x++) {
                int px = gridLeft + x * CELL;
                int py = gridTop + y * CELL;
                graphics.fill(px, py, px + CELL, py + CELL,
                        0xFF000000 | CityFlag.rgbAt(cells, x, y));
                graphics.renderOutline(px, py, CELL, CELL, 0x22000000);
            }
        }
        graphics.renderOutline(gridLeft - 1, gridTop - 1,
                CityFlag.WIDTH * CELL + 2, CityFlag.HEIGHT * CELL + 2, CityScreen.COLOUR_ACCENT);

        // The palette, drawn and clicked entirely here. The selected colour gets a white ring and
        // a step up in size, because a thin accent outline on a dark swatch is the one thing that
        // would leave this exactly as unreadable as the grey buttons were.
        for (DyeColor colour : DyeColor.values()) {
            int index = colour.getId();
            int px = gridLeft + (index % 8) * SWATCH;
            int py = paletteTop + (index / 8) * SWATCH_HEIGHT;
            boolean chosen = colour == brush;
            int inset = chosen ? 0 : 2;
            graphics.fill(px + inset, py + inset,
                    px + SWATCH - 2 - inset, py + SWATCH_HEIGHT - 2 - inset,
                    0xFF000000 | (colour.getTextureDiffuseColor() & 0xFFFFFF));
            if (chosen) {
                graphics.renderOutline(px - 1, py - 1, SWATCH, SWATCH_HEIGHT, 0xFFFFFFFF);
            }
        }

        graphics.drawString(this.font,
                Component.translatable("screen.citiesinlife.flag_chosen", dyeName(brush)),
                left + 12, paletteTop + 2 * SWATCH_HEIGHT + 8, CityScreen.COLOUR_TEXT, false);
        graphics.drawString(this.font, Component.translatable("screen.citiesinlife.flag_hint"),
                left + 12, top + 30, CityScreen.COLOUR_DIM, false);
    }

    /** "Light Blue" rather than "light_blue", without a language key per dye. */
    private static Component dyeName(DyeColor colour) {
        StringBuilder pretty = new StringBuilder();
        for (String word : colour.getName().split("_")) {
            if (word.isEmpty()) {
                continue;
            }
            if (pretty.length() > 0) {
                pretty.append(' ');
            }
            pretty.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return Component.literal(pretty.toString());
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
