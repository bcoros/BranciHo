package com.branciho.citiesinlife.client.screen;

import com.branciho.citiesinlife.net.CitiesInLifeNetwork;
import com.branciho.citiesinlife.net.payload.DeleteStructurePayload;
import com.branciho.citiesinlife.net.payload.StructureSyncPayload;
import com.branciho.citiesinlife.structure.MeasureMode;
import com.branciho.citiesinlife.structure.StructureType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * "Delete this structure area?"
 *
 * <p>Confirmed rather than instant because the click that gets here is sneak + right click while
 * looking at a building, and losing a registered tower to a misjudged crosshair would be infuriating.
 * The dialog also states plainly that the blocks survive, which is the first thing anybody wonders.
 */
public class ConfirmDeleteScreen extends Screen {

    private static final int PANEL_WIDTH = 250;
    private static final int PANEL_HEIGHT = 136;

    private final StructureSyncPayload.Entry target;

    private int left;
    private int top;

    public ConfirmDeleteScreen(StructureSyncPayload.Entry target) {
        super(Component.translatable("screen.citiesinlife.delete_title"));
        this.target = target;
    }

    @Override
    protected void init() {
        left = (this.width - PANEL_WIDTH) / 2;
        top = (this.height - PANEL_HEIGHT) / 2;

        addRenderableWidget(Button.builder(
                        Component.translatable("screen.citiesinlife.yes_delete"), button -> confirm())
                .bounds(left + 16, top + PANEL_HEIGHT - 30, 104, 20)
                .build());

        addRenderableWidget(Button.builder(
                        Component.translatable("gui.no"), button -> this.onClose())
                .bounds(left + PANEL_WIDTH - 120, top + PANEL_HEIGHT - 30, 104, 20)
                .build());
    }

    private void confirm() {
        CitiesInLifeNetwork.sendToServer(new DeleteStructurePayload(target.id()));
        this.minecraft.setScreen(null);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        CityScreen.softDim(graphics, this);
        CityScreen.panel(graphics, left, top, PANEL_WIDTH, PANEL_HEIGHT);

        graphics.drawCenteredString(this.font, Component.translatable("screen.citiesinlife.delete_title"),
                left + PANEL_WIDTH / 2, top + 12, CityScreen.COLOUR_TEXT);
        graphics.fill(left + 16, top + 26, left + PANEL_WIDTH - 16, top + 27, CityScreen.COLOUR_ACCENT);

        StructureType type = StructureType.byId(target.typeId(), StructureType.RESIDENTIAL);
        graphics.drawCenteredString(this.font,
                Component.literal(target.name()), left + PANEL_WIDTH / 2, top + 36, 0xFF000000 | type.colour());
        MeasureMode mode = MeasureMode.byId(target.measureModeId(), MeasureMode.FLOORS);
        graphics.drawCenteredString(this.font,
                Component.translatable("screen.citiesinlife.delete_detail",
                        type.displayName(), target.usableCells(), mode.displayName()),
                left + PANEL_WIDTH / 2, top + 50, CityScreen.COLOUR_DIM);
        graphics.drawCenteredString(this.font,
                Component.translatable("screen.citiesinlife.delete_capacity",
                        target.residents(), target.jobs()),
                left + PANEL_WIDTH / 2, top + 62, CityScreen.COLOUR_DIM);
        graphics.drawCenteredString(this.font,
                Component.translatable("screen.citiesinlife.delete_note"),
                left + PANEL_WIDTH / 2, top + 80, CityScreen.COLOUR_DIM);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
