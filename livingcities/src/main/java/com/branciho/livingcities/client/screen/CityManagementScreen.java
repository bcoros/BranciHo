package com.branciho.livingcities.client.screen;

import com.branciho.livingcities.net.payload.CitySummaryPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * The city overview.
 *
 * <p>v0.1 renders the Overview tab only. The layout is deliberately built around a list of labelled
 * rows so that adding the Buildings/Economy/Utilities tabs later is a matter of swapping the row
 * provider, not rewriting the screen.
 */
public class CityManagementScreen extends Screen {

    private static final int PANEL_WIDTH = 260;
    private static final int ROW_HEIGHT = 12;

    private @Nullable CitySummaryPayload summary;

    public CityManagementScreen(@Nullable CitySummaryPayload summary) {
        super(Component.translatable("screen.livingcities.management"));
        this.summary = summary;
    }

    public void refresh(CitySummaryPayload payload) {
        this.summary = payload;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        int left = (this.width - PANEL_WIDTH) / 2;
        int top = 40;

        if (this.summary == null) {
            guiGraphics.drawCenteredString(this.font,
                    Component.translatable("screen.livingcities.no_city").withStyle(ChatFormatting.GRAY),
                    this.width / 2, this.height / 2, 0xFFFFFF);
            return;
        }

        CitySummaryPayload s = this.summary;
        guiGraphics.drawString(this.font,
                Component.literal(s.cityName()).withStyle(ChatFormatting.BOLD, ChatFormatting.AQUA), left, top, 0xFFFFFF);

        int y = top + 18;
        y = row(guiGraphics, left, y, "screen.livingcities.population",
                formatCount(s.population()) + " / " + formatCount(s.housingCapacity()));
        y = row(guiGraphics, left, y, "screen.livingcities.treasury", money(s.treasuryCents()));
        y = row(guiGraphics, left, y, "screen.livingcities.daily_income", money(s.dailyIncomeCents()));
        y = row(guiGraphics, left, y, "screen.livingcities.daily_expense", money(s.dailyExpenseCents()));
        y = row(guiGraphics, left, y, "screen.livingcities.net",
                money(s.dailyIncomeCents() - s.dailyExpenseCents()));
        y += 6;
        y = row(guiGraphics, left, y, "screen.livingcities.jobs",
                formatCount(s.employed()) + " / " + formatCount(s.jobs()));
        y = row(guiGraphics, left, y, "screen.livingcities.employment", percentOf(s.employed(), workforce(s)));
        y = row(guiGraphics, left, y, "screen.livingcities.happiness", (s.happinessPermille() / 10) + "%");
        y += 6;
        y = row(guiGraphics, left, y, "screen.livingcities.territory", formatCount(s.claimedChunks()) + " chunks");
        row(guiGraphics, left, y, "screen.livingcities.buildings", formatCount(s.buildingCount()));
    }

    private int workforce(CitySummaryPayload s) {
        return (int) (s.population() * com.branciho.livingcities.city.CityStats.WORKFORCE_PARTICIPATION);
    }

    private int row(GuiGraphics guiGraphics, int left, int y, String labelKey, String value) {
        guiGraphics.drawString(this.font, Component.translatable(labelKey).withStyle(ChatFormatting.GRAY), left, y, 0xFFFFFF);
        int valueWidth = this.font.width(value);
        guiGraphics.drawString(this.font, value, left + PANEL_WIDTH - valueWidth, y, 0xFFFFFF);
        return y + ROW_HEIGHT;
    }

    private static String formatCount(int value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    private static String money(long cents) {
        return String.format(Locale.ROOT, "$%,d", cents / 100L);
    }

    private static String percentOf(int part, int whole) {
        if (whole <= 0) {
            return "-";
        }
        return Math.round(part * 100.0F / whole) + "%";
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
