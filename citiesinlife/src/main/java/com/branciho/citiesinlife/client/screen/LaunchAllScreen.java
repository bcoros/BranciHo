package com.branciho.citiesinlife.client.screen;

import com.branciho.citiesinlife.city.Relation;
import com.branciho.citiesinlife.missile.MissileKind;
import com.branciho.citiesinlife.net.CitiesInLifeNetwork;
import com.branciho.citiesinlife.net.ClientCityCache;
import com.branciho.citiesinlife.net.payload.LaunchAllPayload;
import com.branciho.citiesinlife.net.payload.NeighbourCitiesPayload;
import com.branciho.citiesinlife.net.payload.RequestCityPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Who to empty the silos onto.
 *
 * <p>Launch All used to open the strategic map and ask for a chunk, which was wrong twice over: it
 * let you flatten one square with eight rockets, and it made the biggest decision in the mod — who
 * you are about to bombard — a matter of clicking the right pixel. You pick a <em>country</em> now,
 * and the volley scatters across it.
 *
 * <p>Only cities you are actually at war with are listed, because only they are legal targets. The
 * server checks it again on arrival; this list exists so you are not offered a button that will be
 * refused.
 */
public class LaunchAllScreen extends Screen {

    private static final int PANEL_WIDTH = 300;
    private static final int HEADER = 46;
    private static final int ROW = 24;
    private static final int ROWS = 6;
    private static final int FOOTER = 62;

    private int left;
    private int top;
    private int scroll;

    /** What is armed. Held across rebuilds so choosing a target does not reset the warhead. */
    private MissileKind kind = MissileKind.BALLISTIC;

    /** Who is selected, so the fire button can be a deliberate second click rather than the first. */
    private @org.jetbrains.annotations.Nullable UUID chosen;

    /** Whether the refresh has been asked for. Once per screen, not once per button press. */
    private boolean asked;

    /**
     * What the list looked like when the buttons were built.
     *
     * <p>The neighbours table is replaced silently by the packet handler — nothing tells an open
     * screen about it. Without this, a screen opened before the reply lands says you are at war
     * with nobody and goes on saying it, and a war declared while you are reading never appears.
     */
    private String stamp = "";

    public LaunchAllScreen() {
        super(Component.translatable("screen.citiesinlife.launch_all"));
    }

    private static int panelHeight() {
        return HEADER + ROWS * ROW + FOOTER;
    }

    /** Everybody this city is at war with, which is everybody it may legally fire at. */
    private static List<NeighbourCitiesPayload.Entry> enemies() {
        List<NeighbourCitiesPayload.Entry> found = new ArrayList<>();
        for (NeighbourCitiesPayload.Entry entry : ClientCityCache.neighbours()) {
            if (Relation.byOrdinal(entry.theirStance()) == Relation.WAR
                    || Relation.byOrdinal(entry.yourStance()) == Relation.WAR) {
                found.add(entry);
            }
        }
        return found;
    }

    @Override
    protected void init() {
        left = (this.width - PANEL_WIDTH) / 2;
        top = (this.height - panelHeight()) / 2;
        // The neighbours table is the same one the Neighbours tab reads, and a city sync refreshes
        // it. Asking on open means a war declared while this screen was shut is already in the list.
        // Once per screen: init() runs again on every button press, and a packet per click would be
        // a request storm for a table that has not changed.
        if (!asked) {
            asked = true;
            CitiesInLifeNetwork.sendToServer(new RequestCityPayload());
        }

        List<NeighbourCitiesPayload.Entry> targets = enemies();
        stamp = stamp(targets);
        scroll = Mth.clamp(scroll, 0, Math.max(0, targets.size() - ROWS));
        if (chosen != null && targets.stream().noneMatch(e -> e.cityId().equals(chosen))) {
            // Peace was made while you were looking at them.
            chosen = null;
        }

        int y = top + HEADER;
        for (int i = scroll; i < targets.size() && i < scroll + ROWS; i++) {
            NeighbourCitiesPayload.Entry target = targets.get(i);
            boolean picked = chosen != null && chosen.equals(target.cityId());
            Button pick = Button.builder(
                            Component.translatable(picked
                                            ? "screen.citiesinlife.target_picked"
                                            : "screen.citiesinlife.target_pick",
                                    target.name(), target.ownerName()),
                            press -> {
                                chosen = target.cityId();
                                rebuildWidgets();
                            })
                    .bounds(left + 12, y, PANEL_WIDTH - 24, 20)
                    .build();
            pick.active = !picked;
            addRenderableWidget(pick);
            y += ROW;
        }

        int half = (PANEL_WIDTH - 24 - 6) / 2;
        int foot = top + panelHeight() - FOOTER + 8;

        addRenderableWidget(Button.builder(
                        Component.translatable("screen.citiesinlife.warhead", kind.displayName()),
                        press -> {
                            kind = kind == MissileKind.BALLISTIC
                                    ? MissileKind.NUCLEAR
                                    : MissileKind.BALLISTIC;
                            rebuildWidgets();
                        })
                .bounds(left + 12, foot, half, 20)
                .build());

        Button fire = Button.builder(
                        Component.translatable("screen.citiesinlife.launch_all_fire"),
                        press -> {
                            if (chosen != null) {
                                CitiesInLifeNetwork.sendToServer(
                                        new LaunchAllPayload(chosen, kind.id()));
                                this.onClose();
                            }
                        })
                .bounds(left + PANEL_WIDTH - 12 - half, foot, half, 20)
                .build();
        fire.active = chosen != null;
        addRenderableWidget(fire);

        addRenderableWidget(Button.builder(
                        Component.translatable("gui.back"),
                        press -> Minecraft.getInstance().setScreen(new CityHallScreen()))
                .bounds(left + PANEL_WIDTH / 2 - 45, foot + 26, 90, 20)
                .build());
    }

    /** A cheap signature of the list, so a change to it can be spotted without diffing. */
    private static String stamp(List<NeighbourCitiesPayload.Entry> targets) {
        StringBuilder builder = new StringBuilder();
        for (NeighbourCitiesPayload.Entry entry : targets) {
            builder.append(entry.cityId()).append('/').append(entry.name()).append(';');
        }
        return builder.toString();
    }

    /**
     * Rebuild when the table underneath changes.
     *
     * <p>Only when it actually changes: the reply to the refresh usually says the same thing, and
     * rebuilding on every tick would throw away the target you had just clicked.
     */
    @Override
    public void tick() {
        List<NeighbourCitiesPayload.Entry> targets = enemies();
        if (!stamp.equals(stamp(targets))) {
            rebuildWidgets();
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int hidden = Math.max(0, enemies().size() - ROWS);
        if (hidden > 0) {
            scroll = Mth.clamp(scroll - (int) Math.signum(scrollY), 0, hidden);
            rebuildWidgets();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        CityScreen.softDim(graphics, this);
        CityScreen.panel(graphics, left, top, PANEL_WIDTH, panelHeight());

        graphics.drawCenteredString(this.font, this.title, left + PANEL_WIDTH / 2, top + 10,
                CityScreen.COLOUR_TEXT);
        graphics.fill(left + 12, top + 24, left + PANEL_WIDTH - 12, top + 25,
                CityScreen.COLOUR_ACCENT);

        List<NeighbourCitiesPayload.Entry> targets = enemies();
        if (targets.isEmpty()) {
            graphics.drawCenteredString(this.font,
                    Component.translatable("screen.citiesinlife.launch_all_no_war"),
                    left + PANEL_WIDTH / 2, top + 60, CityScreen.COLOUR_BAD);
            graphics.drawCenteredString(this.font,
                    Component.translatable("screen.citiesinlife.launch_all_no_war_hint"),
                    left + PANEL_WIDTH / 2, top + 76, CityScreen.COLOUR_DIM);
            return;
        }

        graphics.drawString(this.font,
                Component.translatable("screen.citiesinlife.launch_all_pick", targets.size()),
                left + 12, top + 30, CityScreen.COLOUR_TEXT, false);

        // Under the buttons rather than over them: what the volley will actually do, in one line,
        // because "every silo you own" is a bigger commitment than the word Launch suggests.
        Component note = chosen == null
                ? Component.translatable("screen.citiesinlife.launch_all_hint")
                : Component.translatable("screen.citiesinlife.launch_all_ready",
                        nameOf(targets, chosen));
        graphics.drawCenteredString(this.font, note, left + PANEL_WIDTH / 2,
                top + panelHeight() - FOOTER - 4,
                chosen == null ? CityScreen.COLOUR_DIM : CityScreen.COLOUR_BAD);
    }

    private static String nameOf(List<NeighbourCitiesPayload.Entry> targets, UUID id) {
        for (NeighbourCitiesPayload.Entry entry : targets) {
            if (entry.cityId().equals(id)) {
                return entry.name();
            }
        }
        return "";
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
