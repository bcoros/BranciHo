package com.branciho.livingcities.client.screen;

import com.branciho.livingcities.building.ZoneUse;
import com.branciho.livingcities.net.payload.BuildingListPayload;
import com.branciho.livingcities.net.payload.CitySummaryPayload;
import com.branciho.livingcities.net.payload.RequestBuildingsPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * The city screen: an overview of the numbers, and a browsable list of what the city is made of.
 *
 * <p>The Buildings tab exists because the overview used to report "Buildings: 12" with no way to reach
 * any of them. A registration is invisible in the world, so a list you can click is the only way to
 * find one that has gone wrong.
 */
public class CityManagementScreen extends Screen {

    private static final int PANEL_WIDTH = 300;
    private static final int ROW_HEIGHT = 12;
    private static final int LIST_TOP = 62;

    private enum Tab {
        OVERVIEW("screen.livingcities.tab_overview"),
        BUILDINGS("screen.livingcities.tab_buildings");

        final String key;

        Tab(String key) {
            this.key = key;
        }
    }

    private @Nullable CitySummaryPayload summary;
    private @Nullable BuildingListPayload buildings;
    private Tab tab = Tab.OVERVIEW;

    public CityManagementScreen(@Nullable CitySummaryPayload summary) {
        super(Component.translatable("screen.livingcities.management"));
        this.summary = summary;
    }

    public void refresh(CitySummaryPayload payload) {
        this.summary = payload;
    }

    public void acceptBuildings(BuildingListPayload payload) {
        this.buildings = payload;
        if (tab == Tab.BUILDINGS) {
            rebuildWidgets();
        }
    }

    @Override
    protected void init() {
        int left = (this.width - PANEL_WIDTH) / 2;

        int tabX = left;
        for (Tab candidate : Tab.values()) {
            final Tab target = candidate;
            Button button = Button.builder(Component.translatable(candidate.key), b -> switchTo(target))
                    .bounds(tabX, 20, 92, 18)
                    .build();
            button.active = candidate != tab;
            addRenderableWidget(button);
            tabX += 96;
        }

        if (tab == Tab.BUILDINGS) {
            initBuildingsTab(left);
        }

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(left + PANEL_WIDTH - 76, this.height - 34, 76, 20)
                .build());
    }

    private void initBuildingsTab(int left) {
        BuildingListPayload list = this.buildings;
        if (list == null) {
            // First visit: ask for page zero. The reply rebuilds the widgets.
            PacketDistributor.sendToServer(RequestBuildingsPayload.page(0));
            return;
        }

        for (int i = 0; i < list.rows().size(); i++) {
            BuildingListPayload.Row row = list.rows().get(i);
            int y = LIST_TOP + i * (ROW_HEIGHT + 2);
            addRenderableWidget(Button.builder(Component.translatable("screen.livingcities.open"),
                            b -> PacketDistributor.sendToServer(RequestBuildingsPayload.detail(row.id())))
                    .bounds(left + PANEL_WIDTH - 52, y - 2, 52, 14)
                    .build());
        }

        if (list.pageCount() > 1) {
            int navY = this.height - 34;
            addRenderableWidget(Button.builder(Component.literal("<"),
                            b -> PacketDistributor.sendToServer(RequestBuildingsPayload.page(list.page() - 1)))
                    .bounds(left, navY, 20, 20)
                    .build());
            addRenderableWidget(Button.builder(Component.literal(">"),
                            b -> PacketDistributor.sendToServer(RequestBuildingsPayload.page(list.page() + 1)))
                    .bounds(left + 24, navY, 20, 20)
                    .build());
        }
    }

    private void switchTo(Tab target) {
        this.tab = target;
        if (target == Tab.BUILDINGS && this.buildings == null) {
            PacketDistributor.sendToServer(RequestBuildingsPayload.page(0));
        }
        rebuildWidgets();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        int left = (this.width - PANEL_WIDTH) / 2;

        if (summary == null) {
            guiGraphics.drawCenteredString(this.font,
                    Component.translatable("screen.livingcities.no_city").withStyle(ChatFormatting.GRAY),
                    this.width / 2, this.height / 2, 0xFFFFFF);
            return;
        }

        guiGraphics.drawString(this.font,
                Component.literal(summary.cityName()).withStyle(ChatFormatting.BOLD, ChatFormatting.AQUA),
                left, 8, 0xFFFFFF);

        if (tab == Tab.OVERVIEW) {
            renderOverview(guiGraphics, left);
        } else {
            renderBuildings(guiGraphics, left);
        }
    }

    private void renderOverview(GuiGraphics guiGraphics, int left) {
        CitySummaryPayload s = this.summary;
        int y = LIST_TOP;
        y = row(guiGraphics, left, y, "screen.livingcities.population",
                count(s.population()) + " / " + count(s.housingCapacity()));
        y = row(guiGraphics, left, y, "screen.livingcities.treasury", money(s.treasuryCents()));
        y = row(guiGraphics, left, y, "screen.livingcities.daily_income", money(s.dailyIncomeCents()));
        y = row(guiGraphics, left, y, "screen.livingcities.daily_expense", money(s.dailyExpenseCents()));
        y = row(guiGraphics, left, y, "screen.livingcities.net",
                money(s.dailyIncomeCents() - s.dailyExpenseCents()));
        y += 6;
        y = row(guiGraphics, left, y, "screen.livingcities.jobs", count(s.employed()) + " / " + count(s.jobs()));
        y = row(guiGraphics, left, y, "screen.livingcities.employment", percentOf(s.employed(), workforce(s)));
        y = row(guiGraphics, left, y, "screen.livingcities.happiness", (s.happinessPermille() / 10) + "%");
        y = row(guiGraphics, left, y, "screen.livingcities.electricity",
                s.powerSatisfactionPermille() >= 1000
                        ? Component.translatable("screen.livingcities.power_ok").getString()
                        : (s.powerSatisfactionPermille() / 10) + "% "
                            + Component.translatable("screen.livingcities.power_shortage").getString());
        y += 6;
        y = row(guiGraphics, left, y, "screen.livingcities.territory", count(s.claimedChunks()) + " chunks");
        row(guiGraphics, left, y, "screen.livingcities.buildings", count(s.buildingCount()));
    }

    private void renderBuildings(GuiGraphics guiGraphics, int left) {
        BuildingListPayload list = this.buildings;
        if (list == null) {
            guiGraphics.drawString(this.font,
                    Component.translatable("screen.livingcities.loading").withStyle(ChatFormatting.GRAY),
                    left, LIST_TOP, 0xFFFFFF);
            return;
        }
        if (list.rows().isEmpty()) {
            guiGraphics.drawString(this.font,
                    Component.translatable("screen.livingcities.no_buildings").withStyle(ChatFormatting.GRAY),
                    left, LIST_TOP, 0xFFFFFF);
            return;
        }

        for (int i = 0; i < list.rows().size(); i++) {
            BuildingListPayload.Row entry = list.rows().get(i);
            int y = LIST_TOP + i * (ROW_HEIGHT + 2);

            ChatFormatting nameColour = entry.needsRescan() ? ChatFormatting.GOLD : ChatFormatting.WHITE;
            guiGraphics.drawString(this.font,
                    Component.literal(trim(entry.name(), 18)).withStyle(nameColour), left, y, 0xFFFFFF);

            String use = entry.mixedUse()
                    ? Component.translatable("screen.livingcities.mixed_use").getString()
                    : Component.translatable("zone.livingcities."
                        + ZoneUse.byId(entry.zoneId(), ZoneUse.UNUSED).id()).getString();
            guiGraphics.drawString(this.font, use, left + 116, y, 0xA8A8A8);

            String occupancy = entry.housingCapacity() > 0
                    ? count(entry.residents()) + "/" + count(entry.housingCapacity()) + " res"
                    : entry.jobCapacity() > 0
                        ? count(entry.workers()) + "/" + count(entry.jobCapacity()) + " job"
                        : "-";
            guiGraphics.drawString(this.font, occupancy, left + 196, y, 0xA8A8A8);
        }

        guiGraphics.drawString(this.font,
                Component.literal("Page " + (list.page() + 1) + "/" + list.pageCount()
                        + "   (" + list.totalBuildings() + " total)").withStyle(ChatFormatting.DARK_GRAY),
                left, this.height - 48, 0x808080);
    }

    private int workforce(CitySummaryPayload s) {
        return (int) (s.population() * com.branciho.livingcities.city.CityStats.WORKFORCE_PARTICIPATION);
    }

    private int row(GuiGraphics guiGraphics, int left, int y, String labelKey, String value) {
        guiGraphics.drawString(this.font, Component.translatable(labelKey).withStyle(ChatFormatting.GRAY),
                left, y, 0xFFFFFF);
        guiGraphics.drawString(this.font, value, left + PANEL_WIDTH - this.font.width(value), y, 0xFFFFFF);
        return y + ROW_HEIGHT;
    }

    private static String trim(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }

    private static String count(int value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    private static String money(long cents) {
        return String.format(Locale.ROOT, "$%,d", cents / 100L);
    }

    private static String percentOf(int part, int whole) {
        return whole <= 0 ? "-" : Math.round(part * 100.0F / whole) + "%";
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
