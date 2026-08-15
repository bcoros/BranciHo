package com.branciho.livingcities.client.screen;

import com.branciho.livingcities.net.payload.CreateCityPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Prompt shown when a player interacts with an unbound City Hall Core. */
public class CreateCityScreen extends Screen {

    private static final int PANEL_WIDTH = 220;

    private final BlockPos corePos;
    private EditBox nameField;

    public CreateCityScreen(BlockPos corePos) {
        super(Component.translatable("screen.livingcities.create_city"));
        this.corePos = corePos;
    }

    @Override
    protected void init() {
        int left = (this.width - PANEL_WIDTH) / 2;
        int top = this.height / 2 - 30;

        this.nameField = new EditBox(this.font, left, top, PANEL_WIDTH, 20,
                Component.translatable("screen.livingcities.city_name"));
        this.nameField.setMaxLength(CreateCityPayload.MAX_NAME_LENGTH);
        this.nameField.setHint(Component.translatable("screen.livingcities.city_name"));
        addRenderableWidget(this.nameField);
        setInitialFocus(this.nameField);

        addRenderableWidget(Button.builder(Component.translatable("screen.livingcities.found_city"), button -> submit())
                .bounds(left, top + 30, PANEL_WIDTH, 20)
                .build());

        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
                .bounds(left, top + 54, PANEL_WIDTH, 20)
                .build());
    }

    private void submit() {
        String name = this.nameField.getValue().trim();
        if (name.isEmpty()) {
            return;
        }
        // Server re-validates everything; this is only a request.
        PacketDistributor.sendToServer(new CreateCityPayload(this.corePos, name));
        onClose();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 55, 0xFFFFFF);
        guiGraphics.drawCenteredString(this.font,
                Component.translatable("screen.livingcities.create_city_hint"),
                this.width / 2, this.height / 2 + 80, 0xA0A0A0);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
