package com.branciho.citiesinlife.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
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

    /**
     * How big the book would like to be, and how small it will settle for.
     *
     * <p>It used to be a fixed four hundred by three hundred and forty-eight, grown by hand every
     * time a chapter was added. Fourteen chapters took it past the three hundred and forty-eight
     * mark, and at GUI scale 4 a 1080p window is only two hundred and seventy pixels tall — so the
     * book ran off the top and bottom of the screen and took the page arrows with it. There was no
     * way to turn a page and no way to tell that was why.
     *
     * <p>So it sizes itself to the window now, and the chapter list scrolls when there are more
     * chapters than there is room for. Adding a fifteenth costs nothing.
     */
    private static final int MAX_WIDTH = 400;
    private static final int MAX_HEIGHT = 348;
    private static final int MIN_WIDTH = 220;
    private static final int MIN_HEIGHT = 140;
    private static final int SIDEBAR = 116;

    /** How far apart the chapter buttons sit, and how tall each one is. */
    private static final int CHAPTER_STRIDE = 19;
    private static final int CHAPTER_HEIGHT = 17;

    /** Where the sidebar and the text both start, below the title rule. */
    private static final int CONTENT_TOP = 34;

    /** How much is kept clear at the bottom for the page arrows and the done button. */
    private static final int CONTENT_BOTTOM = 34;

    /** How far the chapter list has been scrolled, in whole chapters. */
    private int chapterScroll;

    /** Where the text may end. Anything past this would be drawn over the page buttons. */
    private static final int LINE_HEIGHT = 11;

    /** Chapter ids, and how many pages each has. Both halves live in the language file. */
    private static final String[] CHAPTERS = {
            "start", "money", "power", "water", "sewage", "roads", "services",
            "war", "neighbours", "nuclear", "missiles", "sirens", "city_hall", "editor",
            "settings"};
    private static final int[] PAGES = {3, 2, 3, 3, 2, 5, 4, 5, 3, 5, 5, 3, 5, 3, 2};

    private int chapter;
    private int page;
    private List<FormattedCharSequence> body = List.of();

    public GuideScreen() {
        super(Component.translatable("item.citiesinlife.tutorial_book"));
    }

    private int bookWidth() {
        return Mth.clamp(this.width - 8, MIN_WIDTH, MAX_WIDTH);
    }

    private int bookHeight() {
        return Mth.clamp(this.height - 8, MIN_HEIGHT, MAX_HEIGHT);
    }

    /** How many chapter buttons there is room for between the rule and the page arrows. */
    private int visibleChapters() {
        int room = bookHeight() - CONTENT_TOP - CONTENT_BOTTOM;
        return Mth.clamp(room / CHAPTER_STRIDE, 1, CHAPTERS.length);
    }

    private int hiddenChapters() {
        return Math.max(0, CHAPTERS.length - visibleChapters());
    }

    @Override
    protected void init() {
        int width = bookWidth();
        int height = bookHeight();
        int left = (this.width - width) / 2;
        int top = (this.height - height) / 2;

        // Keep the chapter you are reading on screen. Opening the book on a short window and
        // finding the highlighted chapter scrolled off is the same bug in a smaller hat.
        int visible = visibleChapters();
        chapterScroll = Mth.clamp(chapterScroll, 0, hiddenChapters());
        if (chapter < chapterScroll) {
            chapterScroll = chapter;
        } else if (chapter >= chapterScroll + visible) {
            chapterScroll = chapter - visible + 1;
        }

        for (int row = 0; row < visible; row++) {
            int index = chapterScroll + row;
            if (index >= CHAPTERS.length) {
                break;
            }
            Button button = Button.builder(
                            Component.translatable("guide.citiesinlife." + CHAPTERS[index]),
                            b -> {
                                chapter = index;
                                page = 0;
                                rebuildWidgets();
                            })
                    .bounds(left + 10, top + CONTENT_TOP + row * CHAPTER_STRIDE,
                            SIDEBAR - 14, CHAPTER_HEIGHT)
                    .build();
            button.active = index != chapter;
            addRenderableWidget(button);
        }

        Button previous = Button.builder(Component.literal("<"), b -> turn(-1))
                .bounds(left + SIDEBAR + 8, top + height - 28, 24, 20).build();
        previous.active = page > 0;
        addRenderableWidget(previous);

        Button next = Button.builder(Component.literal(">"), b -> turn(1))
                .bounds(left + SIDEBAR + 36, top + height - 28, 24, 20).build();
        next.active = page < PAGES[chapter] - 1;
        addRenderableWidget(next);

        addRenderableWidget(Button.builder(
                        Component.translatable("gui.done"), b -> this.onClose())
                .bounds(left + width - 92, top + height - 28, 80, 20)
                .build());

        body = this.font.split(Component.translatable(
                        "guide.citiesinlife." + CHAPTERS[chapter] + ".p" + (page + 1)),
                width - SIDEBAR - 32);
    }

    /** The wheel walks the chapter list, which is the only thing here long enough to need it. */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (hiddenChapters() > 0) {
            chapterScroll = Mth.clamp(chapterScroll - (int) Math.signum(scrollY),
                    0, hiddenChapters());
            rebuildWidgets();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void turn(int direction) {
        page = Math.max(0, Math.min(PAGES[chapter] - 1, page + direction));
        rebuildWidgets();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int width = bookWidth();
        int height = bookHeight();
        int left = (this.width - width) / 2;
        int top = (this.height - height) / 2;

        CityScreen.softDim(graphics, this);
        CityScreen.panel(graphics, left, top, width, height);

        graphics.drawString(this.font, this.title, left + 12, top + 12,
                CityScreen.COLOUR_TEXT, false);
        graphics.fill(left + 12, top + 26, left + width - 12, top + 27, CityScreen.COLOUR_ACCENT);
        graphics.fill(left + SIDEBAR, top + CONTENT_TOP, left + SIDEBAR + 1,
                top + height - CONTENT_BOTTOM, CityScreen.COLOUR_ACCENT);

        int textLeft = left + SIDEBAR + 16;
        graphics.drawString(this.font,
                Component.translatable("guide.citiesinlife." + CHAPTERS[chapter]),
                textLeft, top + CONTENT_TOP, CityScreen.COLOUR_ACCENT, false);

        // Clipped to the space rather than trusted to fit. A page that grew a sentence too long
        // would otherwise draw straight over the buttons underneath it, and the text is in a
        // language file that somebody may well translate into something wordier.
        int y = top + 50;
        int floor = top + height - 36;
        for (FormattedCharSequence line : body) {
            if (y > floor) {
                break;
            }
            graphics.drawString(this.font, line, textLeft, y, CityScreen.COLOUR_TEXT, false);
            y += LINE_HEIGHT;
        }

        Component counter = Component.literal((page + 1) + " / " + PAGES[chapter]);
        graphics.drawString(this.font, counter,
                left + width - 100 - this.font.width(counter), top + height - 22,
                CityScreen.COLOUR_DIM, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
