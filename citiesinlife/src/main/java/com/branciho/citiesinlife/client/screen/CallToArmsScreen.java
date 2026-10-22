package com.branciho.citiesinlife.client.screen;

import com.branciho.citiesinlife.net.CitiesInLifeNetwork;
import com.branciho.citiesinlife.net.payload.CallToArmsPayload;
import com.branciho.citiesinlife.net.payload.DiplomacyPayload;
import com.branciho.citiesinlife.net.payload.RequestCityPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * An ally has gone to war and is asking whether you are coming.
 *
 * <p>Declining is the focused, harmless button and joining is the one you have to reach for, the
 * same way round as every other irreversible thing in this mod. Saying no costs nothing: the
 * alliance survives it, because an alliance that dissolved the moment you sat one war out would be
 * a pact nobody could afford to sign.
 */
public class CallToArmsScreen extends Screen {

    private static final int WIDTH = 280;
    private static final int HEIGHT = 150;

    private final CallToArmsPayload request;
    private List<FormattedCharSequence> body = List.of();

    public CallToArmsScreen(CallToArmsPayload request) {
        super(Component.translatable("screen.citiesinlife.call_to_arms"));
        this.request = request;
    }

    @Override
    protected void init() {
        int left = (this.width - WIDTH) / 2;
        int top = (this.height - HEIGHT) / 2;

        body = this.font.split(Component.translatable("screen.citiesinlife.call_to_arms_body",
                request.allyName(), request.enemyName()), WIDTH - 32);

        addRenderableWidget(Button.builder(
                        Component.translatable("screen.citiesinlife.stay_out"), button -> onClose())
                .bounds(left + 16, top + HEIGHT - 34, 120, 20)
                .build());

        addRenderableWidget(Button.builder(
                        Component.translatable("screen.citiesinlife.join_war"),
                        button -> {
                            CitiesInLifeNetwork.sendToServer(DiplomacyPayload.of(
                                    request.enemyCityId(), DiplomacyPayload.ACTION_JOIN_WAR));
                            CitiesInLifeNetwork.sendToServer(new RequestCityPayload());
                            onClose();
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
    public boolean isPauseScreen() {
        return false;
    }
}
