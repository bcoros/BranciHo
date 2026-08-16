package com.branciho.citiesinlife.client;

import com.branciho.citiesinlife.registry.ModItems;
import com.branciho.citiesinlife.structure.StructureType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * The planner panel, drawn down the left of the screen while the wand is held.
 *
 * <p>Left rather than above the hotbar because the panel is tall — five building types plus the
 * measurements — and a tall block of text sitting on the hotbar covers the middle of the screen,
 * which is exactly where the player is trying to look while sizing a box.
 */
public final class PlannerHud {

    private static final int PANEL_X = 8;
    private static final int PANEL_WIDTH = 132;
    private static final int ROW_HEIGHT = 13;
    private static final int PADDING = 6;

    private static final int COLOUR_PANEL = 0xE00B0F16;
    private static final int COLOUR_BORDER = 0x66FFFFFF;
    private static final int COLOUR_TITLE_BAR = 0xFF16E0D0;
    private static final int COLOUR_HEADING = 0xFF7FE6DC;
    private static final int COLOUR_TEXT = 0xFFE6ECF2;
    private static final int COLOUR_DIM = 0xFF8C97A3;
    private static final int COLOUR_SET = 0xFF66E576;
    private static final int COLOUR_UNSET = 0xFF6E7B88;

    private PlannerHud() {
    }

    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.screen != null || minecraft.options.hideGui) {
            return;
        }

        if (StructureMode.active()) {
            drawStructureModeBanner(graphics, minecraft);
        }

        boolean holdingWand = player.getMainHandItem().is(ModItems.PLANNER_WAND.get());
        if (!holdingWand) {
            return;
        }
        drawPlannerPanel(graphics, minecraft);
    }

    private static void drawPlannerPanel(GuiGraphics graphics, Minecraft minecraft) {
        final var font = minecraft.font;
        final int rows = StructureType.SELECTABLE.length;
        final int height = 96 + rows * ROW_HEIGHT;
        final int top = Math.max(8, (minecraft.getWindow().getGuiScaledHeight() - height) / 2);

        panel(graphics, PANEL_X, top, PANEL_WIDTH, height);

        int y = top + PADDING;
        graphics.drawString(font, Component.translatable("hud.citiesinlife.planner"),
                PANEL_X + PADDING, y, COLOUR_TEXT, false);
        y += 12;
        graphics.fill(PANEL_X + PADDING, y, PANEL_X + PANEL_WIDTH - PADDING, y + 1, COLOUR_TITLE_BAR);
        y += 6;

        // --- selection state -------------------------------------------------
        graphics.drawString(font, Component.translatable("hud.citiesinlife.selection"),
                PANEL_X + PADDING, y, COLOUR_HEADING, false);
        y += 12;

        boolean hasA = ClientSelection.pointA() != null;
        boolean hasB = ClientSelection.phase() == ClientSelection.Phase.COMPLETE;
        y = statusRow(graphics, y, "hud.citiesinlife.point_a", hasA);
        y = statusRow(graphics, y, "hud.citiesinlife.point_b", hasB);

        if (ClientSelection.active()) {
            y = valueRow(graphics, y, "hud.citiesinlife.bounds", Component.literal(
                    ClientSelection.spanX() + " x " + ClientSelection.spanY()
                            + " x " + ClientSelection.spanZ()));
            y = valueRow(graphics, y, "hud.citiesinlife.floors", measured(ClientSelection.previewFloors()));
            y = valueRow(graphics, y, "hud.citiesinlife.usable", measured(ClientSelection.previewCells()));
        } else {
            graphics.drawString(font, Component.translatable("hud.citiesinlife.right_click_start"),
                    PANEL_X + PADDING, y, COLOUR_DIM, false);
            y += 11;
        }

        y += 4;
        graphics.drawString(font, Component.translatable("hud.citiesinlife.building_type"),
                PANEL_X + PADDING, y, COLOUR_HEADING, false);
        y += 12;

        // --- type list -------------------------------------------------------
        for (StructureType type : StructureType.SELECTABLE) {
            drawTypeRow(graphics, minecraft, y, type, type == ClientSelection.type());
            y += ROW_HEIGHT;
        }

        y += 2;
        graphics.drawString(font, Component.translatable(ClientSelection.phase() == ClientSelection.Phase.COMPLETE
                        ? "hud.citiesinlife.left_click_confirm"
                        : "hud.citiesinlife.scroll_hint"),
                PANEL_X + PADDING, y, COLOUR_DIM, false);
    }

    /** One selectable building type. The chosen one gets its own colour as a filled bar. */
    private static void drawTypeRow(GuiGraphics graphics, Minecraft minecraft, int y,
                                    StructureType type, boolean selected) {
        final int left = PANEL_X + PADDING;
        final int right = PANEL_X + PANEL_WIDTH - PADDING;
        final int colour = 0xFF000000 | type.colour();

        if (selected) {
            graphics.fill(left, y - 2, right, y + 10, withAlpha(type.colour(), 0x44));
            graphics.fill(left, y - 2, left + 2, y + 10, colour);
        }
        // A colour chip on every row, so the outline colour in the world is learnable.
        graphics.fill(left + 5, y + 1, left + 11, y + 7, colour);
        graphics.drawString(minecraft.font, type.displayName(),
                left + 15, y, selected ? COLOUR_TEXT : COLOUR_DIM, false);
    }

    private static int statusRow(GuiGraphics graphics, int y, String key, boolean set) {
        final Minecraft minecraft = Minecraft.getInstance();
        final int left = PANEL_X + PADDING;
        graphics.fill(left, y + 2, left + 4, y + 6, set ? COLOUR_SET : COLOUR_UNSET);
        graphics.drawString(minecraft.font, Component.translatable(key), left + 9, y, COLOUR_TEXT, false);
        Component state = Component.translatable(set
                ? "hud.citiesinlife.set" : "hud.citiesinlife.unset");
        int width = minecraft.font.width(state);
        graphics.drawString(minecraft.font, state,
                PANEL_X + PANEL_WIDTH - PADDING - width, y, set ? COLOUR_SET : COLOUR_UNSET, false);
        return y + 11;
    }

    private static int valueRow(GuiGraphics graphics, int y, String key, Component value) {
        final Minecraft minecraft = Minecraft.getInstance();
        graphics.drawString(minecraft.font, Component.translatable(key),
                PANEL_X + PADDING, y, COLOUR_DIM, false);
        int width = minecraft.font.width(value);
        graphics.drawString(minecraft.font, value,
                PANEL_X + PANEL_WIDTH - PADDING - width, y, COLOUR_TEXT, false);
        return y + 11;
    }

    /** A measurement, or a dash when the selection is too big to preview cheaply. */
    private static Component measured(int value) {
        return value < 0 ? Component.literal("--") : Component.literal(Integer.toString(value));
    }

    private static void drawStructureModeBanner(GuiGraphics graphics, Minecraft minecraft) {
        final int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        final int screenHeight = minecraft.getWindow().getGuiScaledHeight();

        Component title = Component.translatable("hud.citiesinlife.structure_mode_banner");
        Component hint = Component.translatable("hud.citiesinlife.structure_mode_hint");

        int width = Math.max(minecraft.font.width(title), minecraft.font.width(hint)) + 16;
        int left = (screenWidth - width) / 2;
        int top = screenHeight - 62;

        panel(graphics, left, top, width, 30);
        graphics.drawCenteredString(minecraft.font, title, screenWidth / 2, top + 6, COLOUR_TITLE_BAR);
        graphics.drawCenteredString(minecraft.font, hint, screenWidth / 2, top + 18, COLOUR_DIM);
    }

    /** The shared panel look: dark translucent body with a one-pixel border. */
    private static void panel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, COLOUR_PANEL);
        graphics.fill(x, y, x + width, y + 1, COLOUR_BORDER);
        graphics.fill(x, y + height - 1, x + width, y + height, COLOUR_BORDER);
        graphics.fill(x, y, x + 1, y + height, COLOUR_BORDER);
        graphics.fill(x + width - 1, y, x + width, y + height, COLOUR_BORDER);
    }

    private static int withAlpha(int rgb, int alpha) {
        return (alpha << 24) | (rgb & 0xFFFFFF);
    }
}
