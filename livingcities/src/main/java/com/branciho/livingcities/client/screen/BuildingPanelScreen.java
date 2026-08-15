package com.branciho.livingcities.client.screen;

import com.branciho.livingcities.building.ZoneUse;
import com.branciho.livingcities.net.payload.BuildingDetailPayload;
import com.branciho.livingcities.net.payload.RescanBuildingPayload;
import com.branciho.livingcities.net.payload.SetFloorZonePayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Locale;

/**
 * The panel for one registered building: what the scanner measured, and what each floor is for.
 *
 * <p>Assigning uses is the whole point of the mod, so it is one click per floor rather than a form.
 * The floor list pages rather than scrolls because a 30-storey tower is common and a scrollbar in a
 * screen this simple is more code than it is worth.
 */
public class BuildingPanelScreen extends Screen {

    /** The uses offered by the cycle button, in the order a player is likely to want them. */
    private static final ZoneUse[] CYCLE = {
            ZoneUse.UNUSED, ZoneUse.RESIDENTIAL, ZoneUse.COMMERCIAL, ZoneUse.OFFICE, ZoneUse.INDUSTRIAL,
            ZoneUse.WAREHOUSE, ZoneUse.GOVERNMENT, ZoneUse.PUBLIC_SERVICE, ZoneUse.ENTERTAINMENT,
            ZoneUse.PARK, ZoneUse.TRANSPORT, ZoneUse.UTILITY, ZoneUse.SPECIAL
    };

    private static final int PANEL_WIDTH = 320;
    private static final int ROW_HEIGHT = 14;
    private static final int ROWS_PER_PAGE = 10;

    private BuildingDetailPayload detail;
    private int page;

    public BuildingPanelScreen(BuildingDetailPayload detail) {
        super(Component.literal(detail.name()));
        this.detail = detail;
    }

    /** Called when the server pushes an updated snapshot, e.g. after a scan or a zone change. */
    public void refresh(BuildingDetailPayload updated) {
        this.detail = updated;
        clampPage();
        rebuildWidgets();
    }

    private void clampPage() {
        int pages = Math.max(1, (detail.floors().size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
        this.page = Math.clamp(this.page, 0, pages - 1);
    }

    @Override
    protected void init() {
        clampPage();
        int left = (this.width - PANEL_WIDTH) / 2;
        int listTop = 74;

        int first = page * ROWS_PER_PAGE;
        int last = Math.min(first + ROWS_PER_PAGE, detail.floors().size());
        for (int i = first; i < last; i++) {
            BuildingDetailPayload.FloorRow row = detail.floors().get(i);
            int y = listTop + (i - first) * ROW_HEIGHT;
            final int floorIndex = row.index();
            final ZoneUse current = ZoneUse.byId(row.zoneId(), ZoneUse.UNUSED);

            addRenderableWidget(Button.builder(
                            Component.translatable("zone.livingcities." + current.id()),
                            button -> cycleZone(floorIndex, current))
                    .bounds(left + PANEL_WIDTH - 96, y - 3, 96, 13)
                    .build());
        }

        int navY = listTop + ROWS_PER_PAGE * ROW_HEIGHT + 6;
        if (detail.floors().size() > ROWS_PER_PAGE) {
            addRenderableWidget(Button.builder(Component.literal("<"), b -> {
                page--;
                clampPage();
                rebuildWidgets();
            }).bounds(left, navY, 20, 20).build());
            addRenderableWidget(Button.builder(Component.literal(">"), b -> {
                page++;
                clampPage();
                rebuildWidgets();
            }).bounds(left + 24, navY, 20, 20).build());
        }

        addRenderableWidget(Button.builder(Component.translatable("screen.livingcities.rescan"),
                        b -> PacketDistributor.sendToServer(new RescanBuildingPayload(detail.buildingId())))
                .bounds(left + PANEL_WIDTH - 150, navY, 70, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(left + PANEL_WIDTH - 76, navY, 76, 20)
                .build());
    }

    private void cycleZone(int floorIndex, ZoneUse current) {
        int at = 0;
        for (int i = 0; i < CYCLE.length; i++) {
            if (CYCLE[i] == current) {
                at = i;
                break;
            }
        }
        ZoneUse next = CYCLE[(at + 1) % CYCLE.length];
        // The server decides whether this sticks; the button will correct itself from the reply.
        PacketDistributor.sendToServer(new SetFloorZonePayload(detail.buildingId(), floorIndex, next.id()));
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        int left = (this.width - PANEL_WIDTH) / 2;

        guiGraphics.drawString(this.font,
                Component.literal(detail.name()).withStyle(ChatFormatting.BOLD, ChatFormatting.AQUA),
                left, 22, 0xFFFFFF);

        String kind = detail.mixedUse()
                ? Component.translatable("screen.livingcities.mixed_use").getString()
                : Component.translatable("screen.livingcities.single_use").getString();
        guiGraphics.drawString(this.font, Component.literal(kind).withStyle(ChatFormatting.GRAY),
                left, 34, 0xFFFFFF);

        guiGraphics.drawString(this.font, Component.literal(
                        "Residents " + count(detail.residents()) + "/" + count(detail.housingCapacity())
                                + "   Workers " + count(detail.workers()) + "/" + count(detail.jobCapacity())
                                + "   Usable " + count(detail.totalUsableCells())),
                left, 46, 0xD0D0D0);

        if (detail.needsRescan()) {
            guiGraphics.drawString(this.font,
                    Component.translatable("screen.livingcities.stale").withStyle(ChatFormatting.YELLOW),
                    left, 58, 0xFFFFFF);
        } else if (detail.floors().isEmpty()) {
            guiGraphics.drawString(this.font,
                    Component.translatable("screen.livingcities.measuring").withStyle(ChatFormatting.GRAY),
                    left, 58, 0xFFFFFF);
        }

        int listTop = 74;
        int first = page * ROWS_PER_PAGE;
        int last = Math.min(first + ROWS_PER_PAGE, detail.floors().size());
        for (int i = first; i < last; i++) {
            BuildingDetailPayload.FloorRow row = detail.floors().get(i);
            int y = listTop + (i - first) * ROW_HEIGHT;

            String label = "F" + (row.index() + 1) + "  Y" + row.yMin() + "-" + row.yMax();
            guiGraphics.drawString(this.font, label, left, y, 0xFFFFFF);

            String stats = count(row.usableCells()) + " blk"
                    + (row.residents() > 0 ? "  " + count(row.residents()) + " res" : "")
                    + (row.jobs() > 0 ? "  " + count(row.jobs()) + " job" : "");
            guiGraphics.drawString(this.font, stats, left + 108, y, 0xA8A8A8);

            if (!row.flags().isEmpty()) {
                guiGraphics.drawString(this.font,
                        Component.literal(row.flags().toLowerCase(Locale.ROOT)).withStyle(ChatFormatting.DARK_GRAY),
                        left + 108, y + 6, 0x808080);
            }
        }

        if (detail.floorsOmitted() > 0) {
            guiGraphics.drawString(this.font,
                    Component.translatable("screen.livingcities.floors_omitted", detail.floorsOmitted())
                            .withStyle(ChatFormatting.DARK_GRAY),
                    left, listTop + ROWS_PER_PAGE * ROW_HEIGHT - 8, 0x808080);
        }
    }

    private static String count(int value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
