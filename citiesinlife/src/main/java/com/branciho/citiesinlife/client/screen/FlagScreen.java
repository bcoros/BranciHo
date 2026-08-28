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
    private static final int HEIGHT = 234;

    /** How big one square of the flag is drawn, in pixels. */
    private static final int CELL = 22;

    private final Screen parent;
    private byte[] cells;
    private DyeColor brush = DyeColor.RED;

    private int gridLeft;
    private int gridTop;

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
        gridTop = top + 34;

        // The palette: sixteen swatches under the flag, two rows of eight.
        int paletteTop = gridTop + CityFlag.HEIGHT * CELL + 10;
        for (DyeColor colour : DyeColor.values()) {
            int index = colour.getId();
            int x = gridLeft + (index % 8) * CELL;
            int y = paletteTop + (index / 8) * 20;
            addRenderableWidget(Button.builder(Component.literal(" "), button -> brush = colour)
                    .bounds(x, y, CELL - 2, 18)
                    .build());
        }

        int presetsY = paletteTop + 46;
        preset(left + 12, presetsY, "screen.citiesinlife.flag_bands",
                () -> CityFlag.horizontal(brush, DyeColor.WHITE, brush));
        preset(left + 100, presetsY, "screen.citiesinlife.flag_stripes",
                () -> CityFlag.vertical(brush, DyeColor.WHITE, brush));
        preset(left + 188, presetsY, "screen.citiesinlife.flag_cross",
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
                .bounds(x, y, 84, 18)
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
        if (paint(mouseX, mouseY)) {
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

        // The palette swatches are drawn over their own buttons, which are there for the click.
        int paletteTop = gridTop + CityFlag.HEIGHT * CELL + 10;
        for (DyeColor colour : DyeColor.values()) {
            int index = colour.getId();
            int px = gridLeft + (index % 8) * CELL;
            int py = paletteTop + (index / 8) * 20;
            graphics.fill(px + 2, py + 2, px + CELL - 4, py + 16,
                    0xFF000000 | (colour.getTextureDiffuseColor() & 0xFFFFFF));
            if (colour == brush) {
                graphics.renderOutline(px, py, CELL - 2, 18, CityScreen.COLOUR_ACCENT);
            }
        }

        graphics.drawString(this.font, Component.translatable("screen.citiesinlife.flag_hint"),
                left + 12, top + HEIGHT - 46, CityScreen.COLOUR_DIM, false);
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
