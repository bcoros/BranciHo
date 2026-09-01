package com.branciho.citiesinlife.client.screen;

import com.branciho.citiesinlife.net.CitiesInLifeNetwork;
import com.branciho.citiesinlife.net.payload.ConfirmDeleteCityPayload;
import com.branciho.citiesinlife.net.payload.DeleteAreaPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * "This deletes your city." The one dialog in the mod that exists to stop you.
 *
 * <p>Everything else the wand does is reversible by drawing another box. This is not: the treasury,
 * the claims and every registration go together, and there is one city per world, so getting it wrong
 * ends the save's city for good. It lists exactly what is about to be lost, and the destructive
 * button is the one that is not focused.
 */
public class ConfirmDeleteCityScreen extends Screen {

    private static final int PANEL = 0xF00B0F16;
    private static final int BORDER = 0x66FFFFFF;
    private static final int DANGER = 0xFFE0503C;
    private static final int TEXT = 0xFFE6ECF2;
    private static final int DIM = 0xFF8C97A3;

    private static final int WIDTH = 260;
    private static final int HEIGHT = 150;

    private final ConfirmDeleteCityPayload request;
    private List<FormattedCharSequence> body = List.of();

    public ConfirmDeleteCityScreen(ConfirmDeleteCityPayload request) {
        super(Component.translatable("screen.citiesinlife.delete_city"));
        this.request = request;
    }

    @Override
    protected void init() {
        int left = (this.width - WIDTH) / 2;
        int top = (this.height - HEIGHT) / 2;

        body = this.font.split(Component.translatable("screen.citiesinlife.delete_city_body",
                request.structures(), request.chunks(), request.treasury()), WIDTH - 32);

        addRenderableWidget(Button.builder(
                        Component.translatable("screen.citiesinlife.keep_city"),
                        button -> onClose())
                .bounds(left + 16, top + HEIGHT - 34, 110, 20)
                .build());

        addRenderableWidget(Button.builder(
                        Component.translatable("screen.citiesinlife.delete_city_confirm"),
                        button -> {
                            CitiesInLifeNetwork.sendToServer(new DeleteAreaPayload(
                                    request.pointA(), request.pointB(), true));
                            onClose();
                        })
                .bounds(left + WIDTH - 126, top + HEIGHT - 34, 110, 20)
                .build());
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);

        int left = (this.width - WIDTH) / 2;
        int top = (this.height - HEIGHT) / 2;
        graphics.fill(left, top, left + WIDTH, top + HEIGHT, PANEL);
        graphics.fill(left, top, left + WIDTH, top + 1, BORDER);
        graphics.fill(left, top + HEIGHT - 1, left + WIDTH, top + HEIGHT, BORDER);
        graphics.fill(left, top, left + 1, top + HEIGHT, BORDER);
        graphics.fill(left + WIDTH - 1, top, left + WIDTH, top + HEIGHT, BORDER);
        graphics.fill(left + 12, top + 28, left + WIDTH - 12, top + 29, DANGER);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        int left = (this.width - WIDTH) / 2;
        int top = (this.height - HEIGHT) / 2;

        graphics.drawString(this.font,
                Component.translatable("screen.citiesinlife.delete_city_title", request.cityName()),
                left + 16, top + 14, DANGER, false);

        int y = top + 40;
        for (FormattedCharSequence line : body) {
            graphics.drawString(this.font, line, left + 16, y, y == top + 40 ? TEXT : DIM, false);
            y += 11;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
