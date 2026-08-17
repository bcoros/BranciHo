package com.branciho.citiesinlife.client.screen;

import com.branciho.citiesinlife.blockentity.RegisterCounterBlockEntity;
import com.branciho.citiesinlife.menu.RegisterCounterMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * The till's screen: what is on sale, what it costs, and how that is going.
 *
 * <p>The takings line is the whole teaching tool. Nobody is told that demand falls as the price
 * rises — they raise the price, watch customers drop and takings climb, raise it again and watch
 * takings fall, and work out where the top of the hill is themselves.
 */
public class RegisterCounterScreen extends AbstractContainerScreen<RegisterCounterMenu> {

    private static final int PANEL = 0xF00B0F16;
    private static final int BORDER = 0x66FFFFFF;
    private static final int SLOT_BACK = 0xFF20262F;
    private static final int SLOT_EDGE = 0xFF0A0D12;
    private static final int PRODUCT_BACK = 0xFF2A3550;
    private static final int ACCENT = 0xFF16E0D0;
    private static final int TEXT = 0xFFE6ECF2;
    private static final int DIM = 0xFF8C97A3;
    private static final int GOOD = 0xFF7BE38A;
    private static final int WARN = 0xFFE8A33D;

    public RegisterCounterScreen(RegisterCounterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelY = RegisterCounterMenu.INVENTORY_Y - 11;
        this.titleLabelY = 6;
    }

    @Override
    protected void init() {
        super.init();
        addPriceButton(-10, RegisterCounterMenu.BUTTON_MINUS_TEN, 8);
        addPriceButton(-1, RegisterCounterMenu.BUTTON_MINUS_ONE, 38);
        addPriceButton(1, RegisterCounterMenu.BUTTON_PLUS_ONE, 110);
        addPriceButton(10, RegisterCounterMenu.BUTTON_PLUS_TEN, 140);
    }

    private void addPriceButton(int step, int buttonId, int offsetX) {
        addRenderableWidget(Button.builder(
                        Component.literal(step > 0 ? "+" + step : String.valueOf(step)),
                        button -> send(buttonId))
                .bounds(leftPos + offsetX, topPos + RegisterCounterMenu.PRICE_Y - 4, 28, 16)
                .build());
    }

    private void send(int buttonId) {
        Minecraft minecraft = this.minecraft;
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, buttonId);
        }
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
        graphics.fill(x + 7, y + 17, x + imageWidth - 7, y + 18, ACCENT);

        slot(graphics, x + RegisterCounterMenu.PRODUCT_X, y + RegisterCounterMenu.PRODUCT_Y, PRODUCT_BACK);

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                slot(graphics, x + RegisterCounterMenu.INVENTORY_X + column * 18,
                        y + RegisterCounterMenu.INVENTORY_Y + row * 18, SLOT_BACK);
            }
        }
        for (int column = 0; column < 9; column++) {
            slot(graphics, x + RegisterCounterMenu.INVENTORY_X + column * 18,
                    y + RegisterCounterMenu.HOTBAR_Y, SLOT_BACK);
        }
    }

    private void slot(GuiGraphics graphics, int x, int y, int fill) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, SLOT_EDGE);
        graphics.fill(x, y, x + 16, y + 16, fill);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);

        String price = Component.translatable("screen.citiesinlife.price", menu.price()).getString();
        int priceX = (imageWidth - font.width(price)) / 2;
        graphics.drawString(font, price, priceX, RegisterCounterMenu.PRICE_Y, TEXT, false);

        Component status = switch (menu.status()) {
            case RegisterCounterBlockEntity.STATUS_TRADING -> Component.translatable(
                    "screen.citiesinlife.till_trading", menu.customers(), menu.takings());
            case RegisterCounterBlockEntity.STATUS_NOT_A_SHOP ->
                    Component.translatable("screen.citiesinlife.till_not_a_shop");
            case RegisterCounterBlockEntity.STATUS_NO_STOCK ->
                    Component.translatable("screen.citiesinlife.till_no_stock");
            default -> Component.translatable("screen.citiesinlife.till_no_staff");
        };
        int colour = menu.status() == RegisterCounterBlockEntity.STATUS_TRADING ? GOOD : WARN;
        graphics.drawString(font, status,
                (imageWidth - font.width(status)) / 2, RegisterCounterMenu.STATUS_Y, colour, false);

        Component staff = Component.translatable("screen.citiesinlife.till_staff",
                menu.workers(), RegisterCounterBlockEntity.CAPACITY);
        graphics.drawString(font, staff,
                (imageWidth - font.width(staff)) / 2, RegisterCounterMenu.STATUS_Y + 11, DIM, false);
    }
}
