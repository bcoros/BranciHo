package com.branciho.citiesinlife.client.screen;

import com.branciho.citiesinlife.blockentity.FactoryOutputBlockEntity;
import com.branciho.citiesinlife.menu.FactoryOutputMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * The factory crate's screen: a template slot on top, a chest's worth of output below it.
 *
 * <p>Drawn with plain rectangles rather than a texture, matching the rest of the mod's panels. It
 * costs nothing and means the whole UI stays consistent without shipping a GUI sheet that would have
 * to be redrawn every time a slot moves.
 */
public class FactoryOutputScreen extends AbstractContainerScreen<FactoryOutputMenu> {

    private static final int SLOT = 18;
    private static final int PANEL = 0xF00B0F16;
    private static final int BORDER = 0x66FFFFFF;
    private static final int SLOT_BACK = 0xFF20262F;
    private static final int SLOT_EDGE = 0xFF0A0D12;
    private static final int TEMPLATE_BACK = 0xFF2A3550;
    private static final int ACCENT = 0xFF16E0D0;
    private static final int TEXT = 0xFFE6ECF2;
    private static final int DIM = 0xFF8C97A3;
    private static final int WARN = 0xFFE8A33D;

    public FactoryOutputScreen(FactoryOutputMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 218;
        this.inventoryLabelY = FactoryOutputMenu.PLAYER_Y - 11;
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
        graphics.fill(x + 7, y + 17, x + imageWidth - 7, y + 18, ACCENT);

        // Template slot, tinted so it is obviously not part of the output.
        slot(graphics, x + FactoryOutputMenu.TEMPLATE_X, y + FactoryOutputMenu.TEMPLATE_Y, TEMPLATE_BACK);

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                slot(graphics, x + FactoryOutputMenu.STORAGE_X + column * SLOT,
                        y + FactoryOutputMenu.STORAGE_Y + row * SLOT, SLOT_BACK);
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                slot(graphics, x + FactoryOutputMenu.STORAGE_X + column * SLOT,
                        y + FactoryOutputMenu.PLAYER_Y + row * SLOT, SLOT_BACK);
            }
        }
        for (int column = 0; column < 9; column++) {
            slot(graphics, x + FactoryOutputMenu.STORAGE_X + column * SLOT,
                    y + FactoryOutputMenu.HOTBAR_Y, SLOT_BACK);
        }
    }

    private void slot(GuiGraphics graphics, int x, int y, int fill) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, SLOT_EDGE);
        graphics.fill(x, y, x + 16, y + 16, fill);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, TEXT, false);
        graphics.drawString(this.font, this.playerInventoryTitle,
                this.inventoryLabelX, this.inventoryLabelY, DIM, false);

        // The one instruction that makes the block make sense at a glance.
        Component hint = Component.translatable("screen.citiesinlife.factory_hint");
        graphics.drawString(this.font, hint, 8, FactoryOutputMenu.HINT_Y, DIM, false);

        // Why it is or is not making anything. A crate that silently does nothing is the worst
        // possible version of this block, so say which of the reasons it is.
        graphics.drawString(this.font, statusLine(), 8, FactoryOutputMenu.STATUS_Y,
                menu.status() == FactoryOutputBlockEntity.STATUS_PRODUCING ? ACCENT : WARN, false);
    }

    private Component statusLine() {
        return switch (menu.status()) {
            case FactoryOutputBlockEntity.STATUS_PRODUCING ->
                    Component.translatable("screen.citiesinlife.factory_working", menu.workers());
            case FactoryOutputBlockEntity.STATUS_NO_WORKERS ->
                    Component.translatable("screen.citiesinlife.factory_no_workers");
            case FactoryOutputBlockEntity.STATUS_NO_TEMPLATE ->
                    Component.translatable("screen.citiesinlife.factory_no_template");
            case FactoryOutputBlockEntity.STATUS_FULL ->
                    Component.translatable("screen.citiesinlife.factory_full");
            default -> Component.translatable("screen.citiesinlife.factory_not_registered");
        };
    }
}
