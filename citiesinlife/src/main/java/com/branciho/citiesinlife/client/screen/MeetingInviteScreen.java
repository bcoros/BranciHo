package com.branciho.citiesinlife.client.screen;

import com.branciho.citiesinlife.net.CitiesInLifeNetwork;
import com.branciho.citiesinlife.net.payload.MeetingInvitePayload;
import com.branciho.citiesinlife.net.payload.MeetingReplyPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * "There is a meeting at Riverport. Are you coming?"
 *
 * <p>Declining is the harmless button on the left, joining the one you reach for — the same way
 * round as the call to arms, and for the same reason: accepting this one moves your body to
 * somebody else's city and you do not get to choose when you come back.
 *
 * <p>The warning about that is not buried in a tooltip. Being teleported somewhere you cannot leave
 * until another player releases you is a real thing to agree to, and an invitation that did not say
 * so plainly would be a trap dressed as a courtesy.
 */
public class MeetingInviteScreen extends Screen {

    private static final int WIDTH = 280;
    private static final int HEIGHT = 164;

    private final MeetingInvitePayload invite;
    private List<FormattedCharSequence> body = List.of();

    public MeetingInviteScreen(MeetingInvitePayload invite) {
        super(Component.translatable("screen.citiesinlife.meeting_invite"));
        this.invite = invite;
    }

    @Override
    protected void init() {
        int left = (this.width - WIDTH) / 2;
        int top = (this.height - HEIGHT) / 2;

        body = this.font.split(Component.translatable("screen.citiesinlife.meeting_invite_body",
                invite.hostName(), invite.cityName()), WIDTH - 32);

        addRenderableWidget(Button.builder(
                        Component.translatable("screen.citiesinlife.meeting_decline"),
                        button -> onClose())
                .bounds(left + 16, top + HEIGHT - 34, 120, 20)
                .build());

        addRenderableWidget(Button.builder(
                        Component.translatable("screen.citiesinlife.meeting_accept"),
                        button -> {
                            CitiesInLifeNetwork.sendToServer(
                                    new MeetingReplyPayload(invite.cityId(), true));
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
                CityScreen.COLOUR_ACCENT, false);
        graphics.fill(left + 16, top + 26, left + WIDTH - 16, top + 27, CityScreen.COLOUR_ACCENT);

        int y = top + 38;
        for (FormattedCharSequence line : body) {
            graphics.drawString(this.font, line, left + 16, y, CityScreen.COLOUR_TEXT, false);
            y += 12;
        }
        graphics.drawString(this.font,
                Component.translatable("screen.citiesinlife.meeting_invite_warning"),
                left + 16, top + HEIGHT - 52, CityScreen.COLOUR_BAD, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
