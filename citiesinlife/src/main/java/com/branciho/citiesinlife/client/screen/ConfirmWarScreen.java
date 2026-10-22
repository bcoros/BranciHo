package com.branciho.citiesinlife.client.screen;

import com.branciho.citiesinlife.net.CitiesInLifeNetwork;
import com.branciho.citiesinlife.net.payload.DiplomacyPayload;
import com.branciho.citiesinlife.net.payload.RequestCityPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;
import java.util.UUID;

/**
 * "Are you sure you want to declare war on them?"
 *
 * <p>Declaring used to be one click of a button sitting next to the one that hands somebody the
 * keys to your city — no warning, no undo, and it costs money and announces itself to the entire
 * server. Everything that makes war interesting is also what makes it a terrible thing to do by
 * misclick.
 *
 * <p>Purely client side. The server has never needed convincing; this is a dialog between a player
 * and their own mouse, and the packet that goes out when they confirm is the same packet the bare
 * button used to send.
 */
public class ConfirmWarScreen extends Screen {

    private static final int WIDTH = 280;
    private static final int HEIGHT = 150;

    private final UUID targetCityId;
    private final String targetName;
    private final Screen parent;
    private List<FormattedCharSequence> body = List.of();

    public ConfirmWarScreen(UUID targetCityId, String targetName, Screen parent) {
        super(Component.translatable("screen.citiesinlife.confirm_war"));
        this.targetCityId = targetCityId;
        this.targetName = targetName;
        this.parent = parent;
    }

    @Override
    protected void init() {
        int left = (this.width - WIDTH) / 2;
        int top = (this.height - HEIGHT) / 2;

        body = this.font.split(Component.translatable(
                "screen.citiesinlife.confirm_war_body", targetName), WIDTH - 32);

        addRenderableWidget(Button.builder(
                        Component.translatable("gui.no"), button -> this.minecraft.setScreen(parent))
                .bounds(left + 16, top + HEIGHT - 34, 120, 20)
                .build());

        addRenderableWidget(Button.builder(
                        Component.translatable("screen.citiesinlife.declare_war"),
                        button -> {
                            CitiesInLifeNetwork.sendToServer(DiplomacyPayload.of(
                                    targetCityId, DiplomacyPayload.ACTION_DECLARE_WAR));
                            CitiesInLifeNetwork.sendToServer(new RequestCityPayload());
                            this.minecraft.setScreen(parent);
                        })
                .bounds(left + WIDTH - 136, top + HEIGHT - 34, 120, 20)
                .build());
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int left = (this.width - WIDTH) / 2;
        int top = (this.height - HEIGHT) / 2;

        CityScreen.softDim(graphics, this);
        CityScreen.panel(graphics, left, top, WIDTH, HEIGHT);

        graphics.drawString(this.font, this.title, left + 16, top + 14,
                CityScreen.COLOUR_BAD, false);
        graphics.fill(left + 16, top + 26, left + WIDTH - 16, top + 27, CityScreen.COLOUR_ACCENT);

        int y = top + 38;
        for (FormattedCharSequence line : body) {
            graphics.drawString(this.font, line, left + 16, y, CityScreen.COLOUR_TEXT, false);
            y += 12;
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
