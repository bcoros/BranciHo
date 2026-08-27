package com.branciho.citiesinlife.client.screen;

import com.branciho.citiesinlife.client.ClientRoadTool;
import com.branciho.citiesinlife.road.RoadTile;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

/**
 * What the road tool is about to paint.
 *
 * <p>A real window rather than another corner of the HUD, because there is genuinely too much here
 * for one: four kinds of tile, four independent directions of travel and three presets. Cycling all
 * of that with arrow keys — the way the planner wand cycles its five building types — would be a
 * dozen presses to say "two-way street running east and west".
 *
 * <p>Nothing here talks to the server. The brush is client state until a box is confirmed, and the
 * server re-derives what it will accept from the packet regardless.
 */
public class RoadToolScreen extends Screen {

    private static final int PANEL_WIDTH = 300;
    private static final int PANEL_HEIGHT = 216;

    /** The four ways of travel, in compass order, with the letter each is drawn as. */
    private static final Direction[] COMPASS = {
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    private int left;
    private int top;

    public RoadToolScreen() {
        super(Component.translatable("screen.citiesinlife.road_tool"));
    }

    @Override
    protected void init() {
        left = (this.width - PANEL_WIDTH) / 2;
        top = (this.height - PANEL_HEIGHT) / 2;

        // Row one: what kind of tile.
        ClientRoadTool.Brush[] brushes = ClientRoadTool.Brush.values();
        int brushWidth = (PANEL_WIDTH - 24 - 3 * 6) / brushes.length;
        for (int i = 0; i < brushes.length; i++) {
            ClientRoadTool.Brush option = brushes[i];
            Button button = Button.builder(option.displayName(), b -> {
                        ClientRoadTool.setBrush(option);
                        rebuildWidgets();
                    })
                    .bounds(left + 12 + i * (brushWidth + 6), top + 52, brushWidth, 20)
                    .build();
            // The current brush reads as the one already pressed in.
            button.active = ClientRoadTool.brush() != option;
            addRenderableWidget(button);
        }

        // Row two: which ways traffic may leave a tile.
        int dirWidth = (PANEL_WIDTH - 24 - 3 * 6) / COMPASS.length;
        for (int i = 0; i < COMPASS.length; i++) {
            Direction direction = COMPASS[i];
            boolean on = RoadTile.allows(ClientRoadTool.directions(), direction);
            Button button = Button.builder(
                            Component.translatable(
                                    on ? "screen.citiesinlife.dir_on" : "screen.citiesinlife.dir_off",
                                    Component.translatable("screen.citiesinlife.dir_" + direction.getName())),
                            b -> {
                                ClientRoadTool.toggle(direction);
                                rebuildWidgets();
                            })
                    .bounds(left + 12 + i * (dirWidth + 6), top + 90, dirWidth, 20)
                    .build();
            // Greyed where they mean nothing: a junction and a bay are passable every way by
            // definition, so offering the toggles there would be offering a lie.
            button.active = ClientRoadTool.directionsMatter();
            addRenderableWidget(button);
        }

        // Row three: the three arrangements anyone actually wants.
        int presetWidth = (PANEL_WIDTH - 24 - 2 * 6) / 3;
        addPreset(0, presetWidth, "road_preset_ns", RoadTile.NORTH | RoadTile.SOUTH);
        addPreset(1, presetWidth, "road_preset_ew", RoadTile.EAST | RoadTile.WEST);
        addPreset(2, presetWidth, "road_preset_all", RoadTile.DIRECTIONS);

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(left + PANEL_WIDTH - 12 - 90, top + PANEL_HEIGHT - 28, 90, 20).build());
    }

    private void addPreset(int index, int width, String key, int mask) {
        Button button = Button.builder(Component.translatable("screen.citiesinlife." + key), b -> {
                    ClientRoadTool.setDirections(mask);
                    rebuildWidgets();
                })
                .bounds(left + 12 + index * (width + 6), top + 124, width, 20)
                .build();
        button.active = ClientRoadTool.directionsMatter();
        addRenderableWidget(button);
    }

    /**
     * Everything static is painted here, not in {@code render}.
     *
     * <p>{@code Screen.render} draws the background after the widgets, so a panel painted there
     * covers its own buttons. This has caught this project out before.
     */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        CityScreen.softDim(graphics, this);
        CityScreen.panel(graphics, left, top, PANEL_WIDTH, PANEL_HEIGHT);

        graphics.drawString(this.font, this.title, left + 12, top + 10, CityScreen.COLOUR_TEXT, false);
        graphics.fill(left + 12, top + 22, left + PANEL_WIDTH - 12, top + 23, CityScreen.COLOUR_ACCENT);

        graphics.drawString(this.font, Component.translatable("screen.citiesinlife.road_painting",
                        ClientRoadTool.brush().displayName(), directionSummary()),
                left + 12, top + 29, CityScreen.COLOUR_DIM, false);

        graphics.drawString(this.font, Component.translatable("screen.citiesinlife.road_kind_label"),
                left + 12, top + 42, CityScreen.COLOUR_TEXT, false);
        graphics.drawString(this.font, Component.translatable("screen.citiesinlife.road_dirs_label"),
                left + 12, top + 80, CityScreen.COLOUR_TEXT, false);

        graphics.drawString(this.font, Component.translatable("screen.citiesinlife.road_paint_hint"),
                left + 12, top + 152, CityScreen.COLOUR_DIM, false);
        graphics.drawString(this.font, Component.translatable("screen.citiesinlife.road_erase_hint"),
                left + 12, top + 164, CityScreen.COLOUR_DIM, false);
        graphics.drawString(this.font, Component.translatable("screen.citiesinlife.road_flat_hint"),
                left + 12, top + 176, CityScreen.COLOUR_DIM, false);
    }

    /** "N S", or the word for every way, so the summary line is readable at a glance. */
    private Component directionSummary() {
        if (!ClientRoadTool.directionsMatter()) {
            return Component.translatable("screen.citiesinlife.dirs_any");
        }
        StringBuilder letters = new StringBuilder();
        for (Direction direction : COMPASS) {
            if ((ClientRoadTool.directions() & RoadTile.bit(direction)) != 0) {
                if (letters.length() > 0) {
                    letters.append(' ');
                }
                letters.append(Component.translatable(
                        "screen.citiesinlife.dir_" + direction.getName()).getString());
            }
        }
        return letters.length() == 0
                ? Component.translatable("screen.citiesinlife.dirs_any")
                : Component.literal(letters.toString());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
