package com.branciho.citiesinlife.client.screen;

import com.branciho.citiesinlife.net.CitiesInLifeNetwork;
import com.branciho.citiesinlife.net.ClientCityCache;
import com.branciho.citiesinlife.net.payload.EditStructurePayload;
import com.branciho.citiesinlife.net.payload.EditorPayload;
import com.branciho.citiesinlife.net.payload.RequestEditorPayload;
import com.branciho.citiesinlife.structure.StructureType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.List;
import java.util.UUID;

/**
 * Editor mode: the city's buildings, and the four things about one you may change by hand.
 *
 * <p>Creative only, and the server enforces that — this screen is what you get after it has said
 * yes, not the thing that decides. Everything here is a packet the server re-checks on arrival.
 *
 * <p>A list on the left and one building's details on the right, rather than a grid of editable
 * rows. A row wide enough for a name box, a residents box and a jobs box is a row too wide to read,
 * and the thing you actually do here is work on one building at a time.
 *
 * <p>The measured figure is never thrown away. Overriding a building's capacity sets a number
 * beside the measurement rather than replacing it, so AUTO always has something to go back to and
 * the panel can always tell you what the box thinks it is worth.
 */
public class EditorScreen extends Screen {

    private static final int PANEL_WIDTH = 380;
    private static final int PANEL_HEIGHT = 250;

    /** How wide the building list is, and where the details start. */
    private static final int LIST_WIDTH = 152;

    private static final int ROW = 15;
    private static final int LIST_TOP = 52;
    private static final int ROWS = 10;

    private int left;
    private int top;
    private int scroll;

    /** Which building is being worked on, by id so a refresh cannot silently switch it. */
    private @org.jetbrains.annotations.Nullable UUID chosen;

    private int seenRevision = -1;

    private EditBox nameBox;
    private EditBox residentBox;
    private EditBox jobBox;

    public EditorScreen() {
        super(Component.translatable("screen.citiesinlife.editor"));
    }

    private static List<EditorPayload.Entry> buildings() {
        return ClientCityCache.editor().buildings();
    }

    private @org.jetbrains.annotations.Nullable EditorPayload.Entry selected() {
        if (chosen == null) {
            return null;
        }
        for (EditorPayload.Entry entry : buildings()) {
            if (entry.id().equals(chosen)) {
                return entry;
            }
        }
        return null;
    }

    @Override
    protected void init() {
        left = (this.width - PANEL_WIDTH) / 2;
        top = (this.height - PANEL_HEIGHT) / 2;
        seenRevision = ClientCityCache.editorRevision();

        List<EditorPayload.Entry> all = buildings();
        if (chosen == null && !all.isEmpty()) {
            chosen = all.get(0).id();
        }
        scroll = Mth.clamp(scroll, 0, Math.max(0, all.size() - ROWS));

        int y = top + LIST_TOP;
        for (int i = scroll; i < all.size() && i < scroll + ROWS; i++) {
            EditorPayload.Entry entry = all.get(i);
            boolean picked = entry.id().equals(chosen);
            Button row = Button.builder(Component.literal(trim(entry.name())), press -> {
                        chosen = entry.id();
                        rebuildWidgets();
                    })
                    .bounds(left + 10, y, LIST_WIDTH - 16, 14)
                    .build();
            row.active = !picked;
            addRenderableWidget(row);
            y += ROW;
        }

        EditorPayload.Entry entry = selected();
        int detail = left + LIST_WIDTH + 12;
        int width = PANEL_WIDTH - LIST_WIDTH - 24;

        nameBox = new EditBox(this.font, detail, top + 66, width, 18,
                Component.translatable("screen.citiesinlife.editor_name"));
        nameBox.setMaxLength(EditorPayload.MAX_NAME);
        nameBox.setValue(entry == null ? "" : entry.name());
        addRenderableWidget(nameBox);

        int half = (width - 8) / 2;
        residentBox = new EditBox(this.font, detail, top + 108, half, 18,
                Component.translatable("screen.citiesinlife.editor_residents"));
        residentBox.setMaxLength(8);
        residentBox.setFilter(EditorScreen::digitsOnly);
        // The measured figure when nothing is overridden, so the box you are about to type over
        // starts from what the mod already thinks rather than from empty.
        residentBox.setValue(entry == null ? "" : String.valueOf(entry.residents()));
        addRenderableWidget(residentBox);

        jobBox = new EditBox(this.font, detail + half + 8, top + 108, half, 18,
                Component.translatable("screen.citiesinlife.editor_jobs"));
        jobBox.setMaxLength(8);
        jobBox.setFilter(EditorScreen::digitsOnly);
        jobBox.setValue(entry == null ? "" : String.valueOf(entry.jobs()));
        addRenderableWidget(jobBox);

        boolean live = entry != null;
        nameBox.setEditable(live);
        residentBox.setEditable(live);
        jobBox.setEditable(live);

        int buttons = top + 140;
        addRenderableWidget(active(Button.builder(
                        Component.translatable("screen.citiesinlife.editor_apply"),
                        press -> apply())
                .bounds(detail, buttons, half, 18).build(), live));

        addRenderableWidget(active(Button.builder(
                        Component.translatable("screen.citiesinlife.editor_auto"),
                        press -> send(EditStructurePayload.Action.AUTOMATIC, "", -1))
                .bounds(detail + half + 8, buttons, half, 18).build(), live));

        addRenderableWidget(active(Button.builder(
                        Component.translatable("screen.citiesinlife.editor_remeasure"),
                        press -> send(EditStructurePayload.Action.REMEASURE, "", 0))
                .bounds(detail, buttons + 22, half, 18).build(), live));

        addRenderableWidget(active(Button.builder(
                        Component.translatable("screen.citiesinlife.editor_repair"),
                        press -> send(EditStructurePayload.Action.REPAIR, "", 0))
                .bounds(detail + half + 8, buttons + 22, half, 18).build(),
                live && entry.health() < entry.maxHealth()));

        addRenderableWidget(active(Button.builder(
                        Component.translatable("screen.citiesinlife.editor_goto"),
                        press -> {
                            send(EditStructurePayload.Action.GOTO, "", 0);
                            this.onClose();
                        })
                .bounds(detail, buttons + 44, width, 18).build(), live));

        addRenderableWidget(Button.builder(Component.translatable("gui.done"),
                        press -> this.onClose())
                .bounds(left + PANEL_WIDTH / 2 - 45, top + PANEL_HEIGHT - 26, 90, 20)
                .build());
    }

    private static Button active(Button button, boolean live) {
        button.active = live;
        return button;
    }

    /** Digits only, and an empty box allowed so it can be cleared and retyped. */
    private static boolean digitsOnly(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (!Character.isDigit(text.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Send whatever the three boxes now say.
     *
     * <p>Only what actually changed. Sending all three every time would mean clicking Apply after
     * touching nothing but the name pinned the capacity at its measured figure — turning a rename
     * into a silent override, which is exactly the kind of thing you would never find.
     */
    private void apply() {
        EditorPayload.Entry entry = selected();
        if (entry == null) {
            return;
        }
        String name = nameBox.getValue().trim();
        if (!name.isEmpty() && !name.equals(entry.name())) {
            send(EditStructurePayload.Action.RENAME, name, 0);
        }
        int residents = parse(residentBox.getValue(), entry.residents());
        if (residents != entry.residents() || entry.residentOverride() >= 0) {
            send(EditStructurePayload.Action.SET_RESIDENTS, "", residents);
        }
        int jobs = parse(jobBox.getValue(), entry.jobs());
        if (jobs != entry.jobs() || entry.jobOverride() >= 0) {
            send(EditStructurePayload.Action.SET_JOBS, "", jobs);
        }
    }

    private static int parse(String text, int fallback) {
        try {
            return Math.max(0, Integer.parseInt(text.trim()));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private void send(EditStructurePayload.Action action, String name, int amount) {
        if (chosen == null) {
            return;
        }
        CitiesInLifeNetwork.sendToServer(
                new EditStructurePayload(chosen, action.id(), name, amount));
    }

    /** Rebuild when a fresh list lands, which is how an edit shows up as having taken. */
    @Override
    public void tick() {
        super.tick();
        if (seenRevision != ClientCityCache.editorRevision()) {
            rebuildWidgets();
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int hidden = Math.max(0, buildings().size() - ROWS);
        if (hidden > 0 && mouseX < left + LIST_WIDTH) {
            scroll = Mth.clamp(scroll - (int) Math.signum(scrollY), 0, hidden);
            rebuildWidgets();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private String trim(String name) {
        return this.font.width(name) <= LIST_WIDTH - 24
                ? name
                : this.font.plainSubstrByWidth(name, LIST_WIDTH - 30) + "…";
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        CityScreen.softDim(graphics, this);
        CityScreen.panel(graphics, left, top, PANEL_WIDTH, PANEL_HEIGHT);

        graphics.drawString(this.font, this.title, left + 12, top + 12,
                CityScreen.COLOUR_TEXT, false);
        graphics.fill(left + 12, top + 26, left + PANEL_WIDTH - 12, top + 27,
                CityScreen.COLOUR_ACCENT);
        graphics.fill(left + LIST_WIDTH, top + 34, left + LIST_WIDTH + 1,
                top + PANEL_HEIGHT - 34, CityScreen.COLOUR_ACCENT);

        EditorPayload state = ClientCityCache.editor();
        if (!state.usable()) {
            graphics.drawCenteredString(this.font,
                    Component.translatable("screen.citiesinlife.editor_locked"),
                    left + PANEL_WIDTH / 2, top + 60, CityScreen.COLOUR_BAD);
            return;
        }

        List<EditorPayload.Entry> all = state.buildings();
        // The whole city, totalled, so a change to one building can be read against everything.
        int housing = 0;
        int jobs = 0;
        for (EditorPayload.Entry entry : all) {
            housing += entry.residents();
            jobs += entry.jobs();
        }
        graphics.drawString(this.font,
                Component.translatable("screen.citiesinlife.editor_totals",
                        all.size(), housing, jobs),
                left + 12, top + 36, CityScreen.COLOUR_DIM, false);

        if (all.isEmpty()) {
            graphics.drawCenteredString(this.font,
                    Component.translatable("screen.citiesinlife.editor_empty"),
                    left + PANEL_WIDTH / 2, top + 80, CityScreen.COLOUR_DIM);
            return;
        }

        // A stripe of the building's own type colour down the left of each row, which is the same
        // colour its outline is drawn in out in the world.
        int y = top + LIST_TOP;
        for (int i = scroll; i < all.size() && i < scroll + ROWS; i++) {
            StructureType type = StructureType.byId(all.get(i).typeId(), StructureType.RESIDENTIAL);
            graphics.fill(left + 6, y, left + 9, y + 14, 0xFF000000 | type.colour());
            y += ROW;
        }

        EditorPayload.Entry entry = selected();
        if (entry == null) {
            return;
        }
        int detail = left + LIST_WIDTH + 12;
        StructureType type = StructureType.byId(entry.typeId(), StructureType.RESIDENTIAL);
        graphics.drawString(this.font, type.displayName(), detail, top + 36,
                0xFF000000 | type.colour(), false);
        graphics.drawString(this.font,
                Component.translatable("screen.citiesinlife.editor_where",
                        entry.x(), entry.y(), entry.z()),
                detail, top + 48, CityScreen.COLOUR_DIM, false);

        graphics.drawString(this.font,
                Component.translatable("screen.citiesinlife.editor_measured",
                        entry.usableCells()),
                detail, top + 90, CityScreen.COLOUR_DIM, false);

        // What each figure is currently coming from, said in the panel rather than left to be
        // inferred from whether the number happens to look round.
        graphics.drawString(this.font, Component.translatable(entry.residentOverride() >= 0
                        ? "screen.citiesinlife.editor_set"
                        : "screen.citiesinlife.editor_auto_label"),
                detail, top + 128, entry.residentOverride() >= 0
                        ? CityScreen.COLOUR_ACCENT : CityScreen.COLOUR_DIM, false);
        int half = (PANEL_WIDTH - LIST_WIDTH - 24 - 8) / 2;
        graphics.drawString(this.font, Component.translatable(entry.jobOverride() >= 0
                        ? "screen.citiesinlife.editor_set"
                        : "screen.citiesinlife.editor_auto_label"),
                detail + half + 8, top + 128, entry.jobOverride() >= 0
                        ? CityScreen.COLOUR_ACCENT : CityScreen.COLOUR_DIM, false);

        graphics.drawString(this.font,
                Component.translatable("screen.citiesinlife.editor_health",
                        entry.health(), entry.maxHealth()),
                detail, top + PANEL_HEIGHT - 48,
                entry.health() >= entry.maxHealth()
                        ? CityScreen.COLOUR_GOOD : CityScreen.COLOUR_BAD, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
