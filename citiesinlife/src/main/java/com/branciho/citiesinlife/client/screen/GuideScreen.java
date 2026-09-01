package com.branciho.citiesinlife.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * The book.
 *
 * <p>Chapters down the left, pages across the bottom. Not a wall of text with a scrollbar: the
 * questions a player actually arrives with are "how do I do the water" and "why is my factory
 * empty", and both of those are a chapter you jump to rather than a paragraph you scroll past.
 *
 * <p>Every word lives in the language file. It is the only way this is ever translatable, and it
 * also means the wording can be fixed without recompiling - which for a document describing a mod
 * that changes every week is worth more than the small awkwardness of numbering pages by key.
 */
public class GuideScreen extends Screen {

    private static final int WIDTH = 400;
    /**
     * Tall enough for the chapter list.
     *
     * <p>Grown with it, and it has to be: the chapters are laid out down the sidebar at a fixed
     * nineteen pixels each from a fixed top, so an eleventh chapter on the old height put its
     * button underneath the page arrows.
     */
    // Grown again for a FOURTEENTH chapter. The chapters are laid out at a fixed nineteen pixels
    // each from a fixed top, so every one added has to be paid for here or the last sits on top of
    // the page buttons.
    private static final int HEIGHT = 348;
    private static final int SIDEBAR = 116;

    /** Where the text may end. Anything past this would be drawn over the page buttons. */
    private static final int LINE_HEIGHT = 11;

    /** Chapter ids, and how many pages each has. Both halves live in the language file. */
    private static final String[] CHAPTERS = {
            "start", "money", "power", "water", "sewage", "roads", "services",
            "war", "neighbours", "nuclear", "missiles", "sirens", "city_hall", "settings"};
    private static final int[] PAGES = {3, 2, 3, 3, 2, 3, 4, 4, 3, 5, 4, 3, 5, 2};

    private int chapter;
    private int page;
    private List<FormattedCharSequence> body = List.of();

    public GuideScreen() {
        super(Component.translatable("item.citiesinlife.tutorial_book"));
    }

    @Override
    protected void init() {
        int left = (this.width - WIDTH) / 2;
        int top = (this.height - HEIGHT) / 2;

        for (int i = 0; i < CHAPTERS.length; i++) {
            int index = i;
            Button button = Button.builder(
                            Component.translatable("guide.citiesinlife." + CHAPTERS[i]),
                            b -> {
                                chapter = index;
                                page = 0;
                                rebuildWidgets();
                            })
                    .bounds(left + 10, top + 34 + i * 19, SIDEBAR - 14, 17)
                    .build();
            button.active = i != chapter;
            addRenderableWidget(button);
        }

        Button previous = Button.builder(Component.literal("<"), b -> turn(-1))
                .bounds(left + SIDEBAR + 8, top + HEIGHT - 28, 24, 20).build();
        previous.active = page > 0;
        addRenderableWidget(previous);

        Button next = Button.builder(Component.literal(">"), b -> turn(1))
                .bounds(left + SIDEBAR + 36, top + HEIGHT - 28, 24, 20).build();
        next.active = page < PAGES[chapter] - 1;
        addRenderableWidget(next);

        addRenderableWidget(Button.builder(
                        Component.translatable("gui.done"), b -> this.onClose())
                .bounds(left + WIDTH - 92, top + HEIGHT - 28, 80, 20)
                .build());

        body = this.font.split(Component.translatable(
                        "guide.citiesinlife." + CHAPTERS[chapter] + ".p" + (page + 1)),
                WIDTH - SIDEBAR - 32);
    }

    private void turn(int direction) {
        page = Math.max(0, Math.min(PAGES[chapter] - 1, page + direction));
        rebuildWidgets();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int left = (this.width - WIDTH) / 2;
        int top = (this.height - HEIGHT) / 2;

        CityScreen.softDim(graphics, this);
        CityScreen.panel(graphics, left, top, WIDTH, HEIGHT);

        graphics.drawString(this.font, this.title, left + 12, top + 12,
                CityScreen.COLOUR_TEXT, false);
        graphics.fill(left + 12, top + 26, left + WIDTH - 12, top + 27, CityScreen.COLOUR_ACCENT);
        graphics.fill(left + SIDEBAR, top + 34, left + SIDEBAR + 1, top + HEIGHT - 34,
                CityScreen.COLOUR_ACCENT);

        int textLeft = left + SIDEBAR + 16;
        graphics.drawString(this.font,
                Component.translatable("guide.citiesinlife." + CHAPTERS[chapter]),
                textLeft, top + 34, CityScreen.COLOUR_ACCENT, false);

        // Clipped to the space rather than trusted to fit. A page that grew a sentence too long
        // would otherwise draw straight over the buttons underneath it, and the text is in a
        // language file that somebody may well translate into something wordier.
        int y = top + 50;
        int floor = top + HEIGHT - 36;
        for (FormattedCharSequence line : body) {
            if (y > floor) {
                break;
            }
            graphics.drawString(this.font, line, textLeft, y, CityScreen.COLOUR_TEXT, false);
            y += LINE_HEIGHT;
        }

        Component counter = Component.literal((page + 1) + " / " + PAGES[chapter]);
        graphics.drawString(this.font, counter,
                left + WIDTH - 100 - this.font.width(counter), top + HEIGHT - 22,
                CityScreen.COLOUR_DIM, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
