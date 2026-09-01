package com.branciho.citiesinlife.client.screen;

import com.branciho.citiesinlife.net.CitiesInLifeNetwork;
import com.branciho.citiesinlife.net.ClientArmyCache;
import com.branciho.citiesinlife.net.payload.ArmySyncPayload;
import com.branciho.citiesinlife.net.payload.MilitaryActionPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The army roll.
 *
 * <p>Buttons on every row rather than a selected soldier and a row of buttons underneath. Selecting
 * somebody and then choosing what to do to them is two decisions where there is only one — and it
 * is the arrangement that makes it possible to fire the wrong person.
 *
 * <p>Everything shown here comes from the last packet the server sent and every button sends one
 * back; the screen decides nothing. It rebuilds itself whenever a new roll arrives, so hiring
 * somebody makes them appear rather than needing the screen closed and opened again.
 */
public class MilitaryScreen extends Screen {

    private static final int PANEL_WIDTH = 306;
    private static final int HEADER = 44;
    private static final int ROW_HEIGHT = 24;
    private static final int FOOTER = 36;

    /** Room for the largest army a city may keep, so the panel never has to scroll. */
    private static final int ROWS = 8;

    private static final int BUTTON_WIDTH = 46;

    private int left;
    private int top;
    private int seenRevision = -1;

    public MilitaryScreen() {
        super(Component.translatable("screen.citiesinlife.military"));
    }

    private int panelHeight() {
        return HEADER + ROWS * ROW_HEIGHT + FOOTER;
    }

    @Override
    protected void init() {
        left = (this.width - PANEL_WIDTH) / 2;
        top = (this.height - panelHeight()) / 2;
        seenRevision = ClientArmyCache.revision();

        ArmySyncPayload army = ClientArmyCache.army();

        int y = top + HEADER;
        for (ArmySyncPayload.Entry soldier : army.soldiers()) {
            int x = left + PANEL_WIDTH - 12 - BUTTON_WIDTH * 3 - 8;
            addRenderableWidget(Button.builder(
                            Component.translatable("screen.citiesinlife.arm"),
                            button -> send(MilitaryActionPayload.Action.ARM, soldier))
                    .bounds(x, y, BUTTON_WIDTH, 20).build());
            addRenderableWidget(Button.builder(
                            Component.translatable("screen.citiesinlife.train"),
                            button -> send(MilitaryActionPayload.Action.TRAIN, soldier))
                    .bounds(x + BUTTON_WIDTH + 4, y, BUTTON_WIDTH, 20).build());
            addRenderableWidget(Button.builder(
                            Component.translatable("screen.citiesinlife.dismiss"),
                            button -> send(MilitaryActionPayload.Action.DISMISS, soldier))
                    .bounds(x + (BUTTON_WIDTH + 4) * 2, y, BUTTON_WIDTH, 20).build());
            y += ROW_HEIGHT;
        }

        int footerY = top + panelHeight() - 28;
        Button hire = Button.builder(
                        Component.translatable("screen.citiesinlife.hire"),
                        button -> CitiesInLifeNetwork.sendToServer(new MilitaryActionPayload(
                                MilitaryActionPayload.Action.HIRE, MilitaryActionPayload.NOBODY)))
                .bounds(left + 12, footerY, 120, 20).build();
        // Greyed rather than hidden when there is no base: a missing button is a mystery, and a
        // dead one next to the line explaining why is an instruction.
        hire.active = army.hasBase() && army.soldiers().size() < army.maxArmy();
        addRenderableWidget(hire);

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(left + PANEL_WIDTH - 12 - 90, footerY, 90, 20).build());
    }

    private void send(MilitaryActionPayload.Action action, ArmySyncPayload.Entry soldier) {
        CitiesInLifeNetwork.sendToServer(new MilitaryActionPayload(action, soldier.id()));
    }

    @Override
    public void tick() {
        super.tick();
        if (seenRevision != ClientArmyCache.revision()) {
            rebuildWidgets();
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        CityScreen.softDim(graphics, this);
        CityScreen.panel(graphics, left, top, PANEL_WIDTH, panelHeight());

        ArmySyncPayload army = ClientArmyCache.army();

        graphics.drawString(this.font, this.title, left + 12, top + 10, CityScreen.COLOUR_TEXT, false);
        graphics.fill(left + 12, top + 22, left + PANEL_WIDTH - 12, top + 23, CityScreen.COLOUR_ACCENT);

        String money = CityScreen.format(army.treasury());
        graphics.drawString(this.font, Component.translatable("screen.citiesinlife.treasury_is", money),
                left + 12, top + 29, CityScreen.COLOUR_DIM, false);

        if (!army.hasBase()) {
            graphics.drawString(this.font, Component.translatable("screen.citiesinlife.no_base"),
                    left + 12, top + HEADER + 6, CityScreen.COLOUR_BAD, false);
            return;
        }
        if (army.soldiers().isEmpty()) {
            graphics.drawString(this.font, Component.translatable("screen.citiesinlife.no_soldiers"),
                    left + 12, top + HEADER + 6, CityScreen.COLOUR_DIM, false);
        }

        int y = top + HEADER;
        for (ArmySyncPayload.Entry soldier : army.soldiers()) {
            graphics.drawString(this.font, Component.literal(soldier.name()),
                    left + 12, y + 1, CityScreen.COLOUR_TEXT, false);

            Component detail = soldier.secondsLeft() > 0
                    ? Component.translatable("screen.citiesinlife.on_course", soldier.secondsLeft())
                    : Component.translatable("screen.citiesinlife.soldier_detail",
                            soldier.training(),
                            soldier.weapon().isEmpty()
                                    ? Component.translatable("screen.citiesinlife.bare_hands")
                                    : Component.literal(soldier.weapon()));
            graphics.drawString(this.font, detail, left + 12, y + 12,
                    soldier.secondsLeft() > 0 ? CityScreen.COLOUR_ACCENT : CityScreen.COLOUR_DIM, false);
            y += ROW_HEIGHT;
        }

        graphics.drawString(this.font, Component.translatable("screen.citiesinlife.arm_hint"),
                left + 12, top + panelHeight() - 44, CityScreen.COLOUR_DIM, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
