package com.branciho.citiesinlife.client.screen;

import com.branciho.citiesinlife.blockentity.BoilerBlockEntity;
import com.branciho.citiesinlife.menu.BoilerMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;

/**
 * The boiler's screen: a water gauge, a fire, a steam gauge, and a line saying what is wrong.
 *
 * <p>That last part is the important one. A boiler has four separate ways to sit there doing nothing
 * — no coal, no water, no chamber, no vent — and three of them are invisible from inside this screen.
 * Naming the reason is the difference between a machine and a puzzle.
 */
public class BoilerScreen extends AbstractContainerScreen<BoilerMenu> {

    private static final int PANEL = 0xF00B0F16;
    private static final int BORDER = 0x66FFFFFF;
    private static final int SLOT_BACK = 0xFF20262F;
    private static final int SLOT_EDGE = 0xFF0A0D12;
    private static final int GAUGE_BACK = 0xFF151A22;
    private static final int WATER = 0xFF2F7FE0;
    private static final int STEAM = 0xFFBFD4DE;
    private static final int FLAME = 0xFFF0A02A;
    private static final int FLAME_COLD = 0xFF2A2E36;
    private static final int ACCENT = 0xFF16E0D0;
    private static final int TEXT = 0xFFE6ECF2;
    private static final int DIM = 0xFF8C97A3;
    private static final int WARN = 0xFFE8A33D;

    /** The two upright gauges either side of the fire. */
    private static final int GAUGE_TOP = 17;
    private static final int GAUGE_HEIGHT = 52;
    private static final int GAUGE_WIDTH = 12;
    private static final int WATER_GAUGE_X = 14;
    private static final int STEAM_GAUGE_X = 150;

    private static final int FLAME_X = 57;
    private static final int FLAME_Y = 36;
    private static final int FLAME_SIZE = 14;

    public BoilerScreen(BoilerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 188;
        this.inventoryLabelY = BoilerMenu.PLAYER_Y - 11;
        this.titleLabelY = 6;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        final int x = this.leftPos;
        final int y = this.topPos;

        graphics.fill(x, y, x + imageWidth, y + imageHeight, PANEL);
        graphics.fill(x, y, x + imageWidth, y + 1, BORDER);
        graphics.fill(x, y + imageHeight - 1, x + imageWidth, y + imageHeight, BORDER);
        graphics.fill(x, y, x + 1, y + imageHeight, BORDER);
        graphics.fill(x + imageWidth - 1, y, x + imageWidth, y + imageHeight, BORDER);
        graphics.fill(x + 7, y + 15, x + imageWidth - 7, y + 16, ACCENT);

        slot(graphics, x + BoilerMenu.WATER_X, y + BoilerMenu.WATER_Y);
        slot(graphics, x + BoilerMenu.FUEL_X, y + BoilerMenu.FUEL_Y);

        gauge(graphics, x + WATER_GAUGE_X, y + GAUGE_TOP, menu.waterLevel(), WATER);
        gauge(graphics, x + STEAM_GAUGE_X, y + GAUGE_TOP, menu.steamLevel(), STEAM);

        // The fire between the two slots, burning down as the coal is used up.
        graphics.fill(x + FLAME_X, y + FLAME_Y, x + FLAME_X + FLAME_SIZE, y + FLAME_Y + FLAME_SIZE, FLAME_COLD);
        int lit = Math.round(menu.burnProgress() * FLAME_SIZE);
        if (lit > 0) {
            graphics.fill(x + FLAME_X, y + FLAME_Y + FLAME_SIZE - lit,
                    x + FLAME_X + FLAME_SIZE, y + FLAME_Y + FLAME_SIZE, FLAME);
        }

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                slot(graphics, x + BoilerMenu.PLAYER_X + column * 18, y + BoilerMenu.PLAYER_Y + row * 18);
            }
        }
        for (int column = 0; column < 9; column++) {
            slot(graphics, x + BoilerMenu.PLAYER_X + column * 18, y + BoilerMenu.HOTBAR_Y);
        }
    }

    private void slot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, SLOT_EDGE);
        graphics.fill(x, y, x + 16, y + 16, SLOT_BACK);
    }

    /** An upright tube that fills from the bottom. */
    private void gauge(GuiGraphics graphics, int x, int y, float level, int colour) {
        graphics.fill(x - 1, y - 1, x + GAUGE_WIDTH + 1, y + GAUGE_HEIGHT + 1, SLOT_EDGE);
        graphics.fill(x, y, x + GAUGE_WIDTH, y + GAUGE_HEIGHT, GAUGE_BACK);
        int filled = Math.round(level * GAUGE_HEIGHT);
        if (filled > 0) {
            graphics.fill(x, y + GAUGE_HEIGHT - filled, x + GAUGE_WIDTH, y + GAUGE_HEIGHT, colour);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, TEXT, false);
        graphics.drawString(this.font, this.playerInventoryTitle,
                this.inventoryLabelX, this.inventoryLabelY, DIM, false);

        boolean running = menu.status() == BoilerBlockEntity.STATUS_RUNNING_TURBINE
                || menu.status() == BoilerBlockEntity.STATUS_RUNNING_VENTING;
        int colour = running ? ACCENT : WARN;

        // Wrapped, because every one of these lines is an instruction and cutting it in half at the
        // edge of the panel would defeat the point of writing it.
        int y = BoilerMenu.STATUS_Y;
        for (FormattedCharSequence line : this.font.split(statusLine(), imageWidth - 16)) {
            graphics.drawString(this.font, line, 8, y, colour, false);
            y += 10;
        }
    }

    private Component statusLine() {
        return switch (menu.status()) {
            case BoilerBlockEntity.STATUS_RUNNING_TURBINE ->
                    Component.translatable("screen.citiesinlife.boiler_powering");
            case BoilerBlockEntity.STATUS_RUNNING_VENTING ->
                    Component.translatable("screen.citiesinlife.boiler_venting");
            case BoilerBlockEntity.STATUS_NO_CHAMBER ->
                    Component.translatable("screen.citiesinlife.boiler_no_chamber");
            case BoilerBlockEntity.STATUS_NO_OUTLET ->
                    Component.translatable("screen.citiesinlife.boiler_no_outlet");
            case BoilerBlockEntity.STATUS_FOULING ->
                    Component.translatable("screen.citiesinlife.boiler_fouling");
            case BoilerBlockEntity.STATUS_MIXED_PLANT ->
                    Component.translatable("screen.citiesinlife.boiler_mixed_plant");
            case BoilerBlockEntity.STATUS_NO_TURBINE ->
                    Component.translatable("screen.citiesinlife.boiler_no_turbine");
            case BoilerBlockEntity.STATUS_NO_WATER ->
                    Component.translatable("screen.citiesinlife.boiler_no_water");
            default -> Component.translatable("screen.citiesinlife.boiler_no_fuel");
        };
    }
}
