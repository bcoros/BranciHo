package com.branciho.citiesinlife.client.screen;

import com.branciho.citiesinlife.client.ClientSelection;
import com.branciho.citiesinlife.net.CitiesInLifeNetwork;
import com.branciho.citiesinlife.net.payload.RegisterStructurePayload;
import com.branciho.citiesinlife.structure.StructureType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Name the city, shown once: the first time a City Core selection is confirmed.
 *
 * <p>The only place in the mod where committing a selection opens a screen. Everything else is a
 * click, because being interrupted by a dialog every time you register a shop would be miserable.
 */
public class NameCityScreen extends Screen {

    private static final int PANEL_WIDTH = 220;
    private static final int PANEL_HEIGHT = 116;
    private static final int MAX_NAME_LENGTH = 32;

    private int left;
    private int top;
    private EditBox nameBox;
    private Button foundButton;

    public NameCityScreen() {
        super(Component.translatable("screen.citiesinlife.found_city"));
    }

    @Override
    protected void init() {
        left = (this.width - PANEL_WIDTH) / 2;
        top = (this.height - PANEL_HEIGHT) / 2;

        nameBox = new EditBox(this.font, left + 16, top + 46, PANEL_WIDTH - 32, 20,
                Component.translatable("screen.citiesinlife.city_name"));
        nameBox.setMaxLength(MAX_NAME_LENGTH);
        nameBox.setResponder(text -> refreshFoundButton());
        addRenderableWidget(nameBox);
        setInitialFocus(nameBox);

        foundButton = Button.builder(
                        Component.translatable("screen.citiesinlife.found"), button -> found())
                .bounds(left + 16, top + PANEL_HEIGHT - 30, 90, 20)
                .build();
        addRenderableWidget(foundButton);

        addRenderableWidget(Button.builder(
                        Component.translatable("gui.cancel"), button -> this.onClose())
                .bounds(left + PANEL_WIDTH - 106, top + PANEL_HEIGHT - 30, 90, 20)
                .build());

        refreshFoundButton();
    }

    private void refreshFoundButton() {
        foundButton.active = nameBox.getValue().trim().length() >= 2;
    }

    private void found() {
        if (ClientSelection.pointA() == null || ClientSelection.pointB() == null) {
            this.onClose();
            return;
        }
        CitiesInLifeNetwork.sendToServer(new RegisterStructurePayload(
                ClientSelection.pointA(),
                ClientSelection.pointB(),
                StructureType.CITY_CORE.id(),
                nameBox.getValue().trim()));
        ClientSelection.cancel();
        this.minecraft.setScreen(null);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        CityScreen.panel(graphics, left, top, PANEL_WIDTH, PANEL_HEIGHT);

        graphics.drawString(this.font, Component.translatable("screen.citiesinlife.found_city"),
                left + 16, top + 12, CityScreen.COLOUR_TEXT, false);
        graphics.fill(left + 16, top + 24, left + PANEL_WIDTH - 16, top + 25, CityScreen.COLOUR_ACCENT);
        graphics.drawString(this.font, Component.translatable("screen.citiesinlife.found_hint"),
                left + 16, top + 32, CityScreen.COLOUR_DIM, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
