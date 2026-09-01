package com.branciho.citiesinlife.client.screen;

import com.branciho.citiesinlife.net.CitiesInLifeNetwork;
import com.branciho.citiesinlife.net.payload.DiplomacyPayload;
import com.branciho.citiesinlife.net.payload.PeaceOfferPayload;
import com.branciho.citiesinlife.net.payload.RequestCityPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * They have offered you a treaty.
 *
 * <p>Neither button is the safe one, which is why this is a screen rather than a line of chat.
 * Accepting ends a war you may be winning; refusing continues one you may be losing. Closing it
 * without choosing leaves the offer standing in the Neighbours tab, so nothing is lost by walking
 * away to think about it.
 */
public class PeaceOfferScreen extends Screen {

    private static final int WIDTH = 280;
    private static final int HEIGHT = 150;

    private final PeaceOfferPayload offer;
    private List<FormattedCharSequence> body = List.of();

    public PeaceOfferScreen(PeaceOfferPayload offer) {
        super(Component.translatable("screen.citiesinlife.peace_offer"));
        this.offer = offer;
    }

    @Override
    protected void init() {
        int left = (this.width - WIDTH) / 2;
        int top = (this.height - HEIGHT) / 2;

        body = this.font.split(Component.translatable(
                "screen.citiesinlife.peace_offer_body", offer.fromName()), WIDTH - 32);

        addRenderableWidget(Button.builder(
                        Component.translatable("screen.citiesinlife.peace_decline"),
                        button -> answer(DiplomacyPayload.ACTION_DECLINE_PEACE))
                .bounds(left + 16, top + HEIGHT - 34, 120, 20)
                .build());

        addRenderableWidget(Button.builder(
                        Component.translatable("screen.citiesinlife.peace_accept"),
                        button -> answer(DiplomacyPayload.ACTION_ACCEPT_PEACE))
                .bounds(left + WIDTH - 136, top + HEIGHT - 34, 120, 20)
                .build());
    }

    private void answer(int action) {
        CitiesInLifeNetwork.sendToServer(DiplomacyPayload.of(offer.fromCityId(), action));
        CitiesInLifeNetwork.sendToServer(new RequestCityPayload());
        onClose();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int left = (this.width - WIDTH) / 2;
        int top = (this.height - HEIGHT) / 2;

        CityScreen.softDim(graphics, this);
        CityScreen.panel(graphics, left, top, WIDTH, HEIGHT);

        graphics.drawString(this.font, this.title, left + 16, top + 14,
                CityScreen.COLOUR_ACCENT, false);
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
