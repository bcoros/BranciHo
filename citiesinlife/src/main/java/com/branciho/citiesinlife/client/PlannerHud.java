package com.branciho.citiesinlife.client;

import com.branciho.citiesinlife.registry.ModItems;
import com.branciho.citiesinlife.road.RoadTile;
import com.branciho.citiesinlife.structure.StructureType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

/**
 * The planner panel, drawn down the left of the screen while the wand is held.
 *
 * <p>Left rather than above the hotbar because the panel is tall — a row per building type plus the
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

    /** The red wand's own colour, matching the box it draws in the world. */
    private static final int COLOUR_WAR = 0xFFFF6B2E;

    private PlannerHud() {
    }

    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.screen != null || minecraft.options.hideGui) {
            return;
        }

        // Fallout, washed over everything before anything else is drawn. Held well below opaque
        // even at a full dose: this is the one effect that could make the game unplayable rather
        // than unpleasant, and being unable to see is what the blindness is already for.
        int fallout = ClientRadiation.tint();
        if (fallout != 0) {
            graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), fallout);
        }

        if (StructureMode.active()) {
            drawStructureModeBanner(graphics, minecraft);
        }

        // Before the war branch, because that one ends in a return and order decides what wins.
        if (player.getMainHandItem().is(ModItems.ROAD_TOOL.get())) {
            drawRoadPanel(graphics, minecraft);
            return;
        }

        if (player.getMainHandItem().is(ModItems.PATH_TOOL.get())) {
            drawPathPanel(graphics, minecraft);
            return;
        }

        if (player.getMainHandItem().is(ModItems.WAR_PLANNER_WAND.get())) {
            drawWarBanner(graphics, minecraft);
            return;
        }

        boolean holdingWand = player.getMainHandItem().is(ModItems.PLANNER_WAND.get());
        if (!holdingWand) {
            return;
        }
        drawPlannerPanel(graphics, minecraft);
    }

    /**
     * A short banner for the red wand rather than the full planner panel.
     *
     * <p>The planner panel exists to answer "how big is this and how many people fit in it". None of
     * that applies to a building somebody else already built and measured: the only two things worth
     * knowing are whether the box is drawn and whether it is about to be rewritten.
     */
    private static void drawWarBanner(GuiGraphics graphics, Minecraft minecraft) {
        final int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        final int screenHeight = minecraft.getWindow().getGuiScaledHeight();

        Component title = Component.translatable("hud.citiesinlife.war_wand_banner");
        Component mode = ClientWarWand.describe();
        Component hint = Component.translatable(
                ClientSelection.phase() == ClientSelection.Phase.COMPLETE
                        ? "hud.citiesinlife.left_click_seize"
                        : "hud.citiesinlife.right_click_start");

        int width = Math.max(minecraft.font.width(title),
                Math.max(minecraft.font.width(mode), minecraft.font.width(hint))) + 16;
        int left = (screenWidth - width) / 2;
        int top = screenHeight - 76;

        panel(graphics, left, top, width, 42);
        graphics.drawCenteredString(minecraft.font, title, screenWidth / 2, top + 6, COLOUR_WAR);
        graphics.drawCenteredString(minecraft.font, mode, screenWidth / 2, top + 18, COLOUR_TEXT);
        graphics.drawCenteredString(minecraft.font, hint, screenWidth / 2, top + 30,
                ClientSelection.phase() == ClientSelection.Phase.COMPLETE ? COLOUR_SET : COLOUR_DIM);
    }

    /**
     * What the road tool is about to paint, without opening the panel.
     *
     * <p>This layer hides itself the moment any screen opens, so the brush shown in {@code
     * RoadToolScreen} is invisible the instant that screen is closed. A player who set a brush,
     * closed the window and then saw no confirmation anywhere would paint the wrong thing, so the
     * same state has to be legible in both places.
     */
    private static void drawRoadPanel(GuiGraphics graphics, Minecraft minecraft) {
        final var font = minecraft.font;
        final int height = 92;
        final int top = Math.max(8, (minecraft.getWindow().getGuiScaledHeight() - height) / 2);

        panel(graphics, PANEL_X, top, PANEL_WIDTH, height);

        int y = top + PADDING;
        graphics.drawString(font, Component.translatable("hud.citiesinlife.road_panel"),
                PANEL_X + PADDING, y, COLOUR_TEXT, false);
        y += 12;
        graphics.fill(PANEL_X + PADDING, y, PANEL_X + PANEL_WIDTH - PADDING, y + 1, COLOUR_TITLE_BAR);
        y += 6;

        y = valueRow(graphics, y, "hud.citiesinlife.road_brush", ClientRoadTool.brush().displayName());

        // The four letters, each lit or dim. This is the only place outside the panel where a
        // one-way street can be told from a two-way one before it is painted.
        graphics.drawString(font, Component.translatable("hud.citiesinlife.road_directions"),
                PANEL_X + PADDING, y, COLOUR_DIM, false);
        int x = PANEL_X + PANEL_WIDTH - PADDING - 4 * 10;
        for (Direction direction : new Direction[]{
                Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
            boolean on = !ClientRoadTool.directionsMatter()
                    || (ClientRoadTool.directions() & RoadTile.bit(direction)) != 0;
            graphics.drawString(font, Component.translatable(
                            "screen.citiesinlife.dir_" + direction.getName()),
                    x, y, on ? COLOUR_SET : COLOUR_UNSET, false);
            x += 10;
        }
        y += 11;

        boolean hasA = ClientSelection.pointA() != null;
        boolean hasB = ClientSelection.phase() == ClientSelection.Phase.COMPLETE;
        y = statusRow(graphics, y, "hud.citiesinlife.point_a", hasA);
        y = statusRow(graphics, y, "hud.citiesinlife.point_b", hasB);

        graphics.drawString(font, Component.translatable("hud.citiesinlife.road_open_ui"),
                PANEL_X + PADDING, y + 2, COLOUR_DIM, false);
    }

    /**
     * The path tool's own panel, which it went without entirely until now.
     *
     * <p>Every other box tool draws something while it is held, so the path tool drawing nothing
     * read as the tool being half-finished - and it hid the one thing the player went looking for,
     * which is that sneak + left click has always cleared pavement. An undocumented gesture is
     * indistinguishable from a missing feature.
     */
    private static void drawPathPanel(GuiGraphics graphics, Minecraft minecraft) {
        final var font = minecraft.font;
        final int height = 80;
        final int top = Math.max(8, (minecraft.getWindow().getGuiScaledHeight() - height) / 2);

        panel(graphics, PANEL_X, top, PANEL_WIDTH, height);

        int y = top + PADDING;
        graphics.drawString(font, Component.translatable("hud.citiesinlife.path_panel"),
                PANEL_X + PADDING, y, COLOUR_TEXT, false);
        y += 12;
        graphics.fill(PANEL_X + PADDING, y, PANEL_X + PANEL_WIDTH - PADDING, y + 1, COLOUR_TITLE_BAR);
        y += 6;

        boolean hasA = ClientSelection.pointA() != null;
        boolean hasB = ClientSelection.phase() == ClientSelection.Phase.COMPLETE;
        y = statusRow(graphics, y, "hud.citiesinlife.point_a", hasA);
        y = statusRow(graphics, y, "hud.citiesinlife.point_b", hasB);

        // Lit only once the box is closed, because that is the only moment either click does
        // anything, and a hint that is always on teaches nothing about when it applies.
        int hintColour = hasB ? COLOUR_SET : COLOUR_DIM;
        graphics.drawString(font, Component.translatable("hud.citiesinlife.path_paint"),
                PANEL_X + PADDING, y, hintColour, false);
        y += 11;
        graphics.drawString(font, Component.translatable("hud.citiesinlife.path_erase"),
                PANEL_X + PADDING, y, hintColour, false);
    }

    private static void drawPlannerPanel(GuiGraphics graphics, Minecraft minecraft) {
        final var font = minecraft.font;
        final int rows = StructureType.SELECTABLE.length;
        final int height = 130 + rows * ROW_HEIGHT;
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

        y += 3;
        graphics.drawString(font, Component.translatable("hud.citiesinlife.arrows_hint"),
                PANEL_X + PADDING, y, COLOUR_DIM, false);
        y += 10;
        if (ClientSelection.phase() == ClientSelection.Phase.COMPLETE) {
            graphics.drawString(font, Component.translatable("hud.citiesinlife.left_click_confirm"),
                    PANEL_X + PADDING, y, COLOUR_SET, false);
        }
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
