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
    private static final int PANEL_HEIGHT = 320;

    /** How wide the building list is, and where the details start. */
    private static final int LIST_WIDTH = 152;

    private static final int ROW = 15;
    private static final int LIST_TOP = 52;
    private static final int ROWS = 15;

    private int left;
    private int top;
    private int scroll;

    /** Which building is being worked on, by id so a refresh cannot silently switch it. */
    private @org.jetbrains.annotations.Nullable UUID chosen;

    private int seenRevision = -1;

    /** How many to spawn. Held across rebuilds, which happen on every edit. */
    private String spawnCount = "1";

    private EditBox nameBox;
    private EditBox residentBox;
    private EditBox jobBox;
    private EditBox healthBox;
    private EditBox boostBox;
    private EditBox spawnBox;

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

        // Three columns: people, jobs, health. Each starts at the figure currently in effect, so
        // the box you are about to type over says what the mod already thinks rather than nothing.
        int third = (width - 12) / 3;
        residentBox = numberBox(detail, top + 112, third,
                "screen.citiesinlife.editor_residents",
                entry == null ? "" : String.valueOf(entry.residents()));
        jobBox = numberBox(detail + third + 6, top + 112, third,
                "screen.citiesinlife.editor_jobs",
                entry == null ? "" : String.valueOf(entry.jobs()));
        healthBox = numberBox(detail + 2 * (third + 6), top + 112, third,
                "screen.citiesinlife.editor_health_box",
                entry == null ? "" : String.valueOf(entry.maxHealth()));

        boolean live = entry != null;
        nameBox.setEditable(live);
        residentBox.setEditable(live);
        jobBox.setEditable(live);
        healthBox.setEditable(live);

        // The button rows below are two across; the boxes above are three. Kept as its own number
        // rather than reusing the column width, because they are answering different questions.
        int half = (width - 8) / 2;
        int buttons = top + 146;
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

        // Boost: only for the four kinds of building that produce anything, and greyed with a line
        // saying so for everything else, because an editable box that silently does nothing is
        // worse than one you can see is not for this.
        boolean boostable = live && !"none".equals(entry.boostUnit());
        int boostRow = buttons + 76;
        boostBox = numberBox(detail, boostRow, third,
                "screen.citiesinlife.editor_boost", boostable ? String.valueOf(entry.boost()) : "");
        boostBox.setEditable(boostable);
        addRenderableWidget(active(Button.builder(
                        Component.translatable("screen.citiesinlife.editor_boost_apply"),
                        press -> send(EditStructurePayload.Action.SET_BOOST, "",
                                Math.max(0, parse(boostBox.getValue(), 0))))
                .bounds(detail + third + 6, boostRow, width - third - 6, 18).build(), boostable));

        // Spawning is its own row rather than another pair of buttons, because it takes a number
        // and because it is the one thing here that puts something in the world rather than
        // changing a figure about it.
        int spawnRow = buttons + 122;
        spawnBox = new EditBox(this.font, detail, spawnRow, 44, 18,
                Component.translatable("screen.citiesinlife.editor_spawn_count"));
        spawnBox.setMaxLength(2);
        spawnBox.setFilter(EditorScreen::digitsOnly);
        spawnBox.setValue(spawnCount);
        spawnBox.setResponder(text -> spawnCount = text);
        spawnBox.setEditable(live);
        addRenderableWidget(spawnBox);

        addRenderableWidget(active(Button.builder(
                        Component.translatable("screen.citiesinlife.editor_spawn"),
                        press -> send(EditStructurePayload.Action.SPAWN, "",
                                Math.max(1, parse(spawnCount, 1))))
                .bounds(detail + 50, spawnRow, width - 50, 18).build(), live));

        addRenderableWidget(Button.builder(Component.translatable("gui.done"),
                        press -> this.onClose())
                .bounds(left + PANEL_WIDTH / 2 - 45, top + PANEL_HEIGHT - 26, 90, 20)
                .build());
    }

    /** One of the three capacity boxes: digits only, bounded, pre-filled, and registered. */
    private EditBox numberBox(int x, int y, int width, String key, String value) {
        EditBox box = new EditBox(this.font, x, y, width, 18, Component.translatable(key));
        box.setMaxLength(8);
        box.setFilter(EditorScreen::digitsOnly);
        box.setValue(value);
        addRenderableWidget(box);
        return box;
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
        // Health is the TOTAL, not what is left of it - Repair is the button for topping a
        // building up. Setting it carries the current figure along in proportion, so doubling a
        // tower's total does not halve how healthy it looks.
        int health = parse(healthBox.getValue(), entry.maxHealth());
        if (health != entry.maxHealth() || entry.healthOverride() >= 0) {
            send(EditStructurePayload.Action.SET_HEALTH, "", Math.max(1, health));
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

        // Three unlabelled number boxes are three guesses, so each gets a heading above it and,
        // below it, where its figure is currently coming from - said in the panel rather than left
        // to be inferred from whether the number happens to look round.
        int third = (PANEL_WIDTH - LIST_WIDTH - 24 - 12) / 3;
        column(graphics, detail, top, third, "screen.citiesinlife.editor_col_people",
                entry.residentOverride());
        column(graphics, detail + third + 6, top, third, "screen.citiesinlife.editor_col_jobs",
                entry.jobOverride());
        column(graphics, detail + 2 * (third + 6), top, third,
                "screen.citiesinlife.editor_col_health", entry.healthOverride());

        graphics.drawString(this.font,
                Component.translatable("screen.citiesinlife.editor_boost_"
                        + entry.boostUnit()),
                detail, top + 212,
                "none".equals(entry.boostUnit())
                        ? CityScreen.COLOUR_DIM : CityScreen.COLOUR_ACCENT, false);

        graphics.drawString(this.font,
                Component.translatable("screen.citiesinlife.editor_spawn_hint"),
                detail, top + 258, CityScreen.COLOUR_DIM, false);

        graphics.drawString(this.font,
                Component.translatable("screen.citiesinlife.editor_health",
                        entry.health(), entry.maxHealth()),
                detail, top + PANEL_HEIGHT - 44,
                entry.health() >= entry.maxHealth()
                        ? CityScreen.COLOUR_GOOD : CityScreen.COLOUR_BAD, false);
    }

    /** A heading over one of the three boxes, and what its figure is coming from underneath. */
    private void column(GuiGraphics graphics, int x, int top, int width, String key, int override) {
        graphics.drawString(this.font, Component.translatable(key), x, top + 100,
                CityScreen.COLOUR_TEXT, false);
        Component source = Component.translatable(override >= 0
                ? "screen.citiesinlife.editor_set"
                : "screen.citiesinlife.editor_auto_label");
        graphics.drawString(this.font, source, x, top + 132,
                override >= 0 ? CityScreen.COLOUR_ACCENT : CityScreen.COLOUR_DIM, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
