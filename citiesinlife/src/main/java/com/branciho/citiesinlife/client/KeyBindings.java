package com.branciho.citiesinlife.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;

/**
 * Every key this mod binds.
 *
 * <p>GLFW key codes are <em>physical</em> positions taken from a US layout, not the character printed
 * on the key. That is why the city panel is on G and not on the semicolon it originally used: on a
 * Slovak (or German, or French) keyboard the key GLFW calls {@code SEMICOLON} is somewhere else
 * entirely and produces a different character, so the binding simply never fired. Letters and arrows
 * sit in the same physical place on every common layout; punctuation does not.
 *
 * <p>All of these are rebindable in Options → Controls → Cities In Life.
 */
public final class KeyBindings {

    public static final String CATEGORY = "key.categories.citiesinlife";

    /** Opens the city panel — money, population, and the way through to the land map. */
    public static final KeyMapping OPEN_CITY = new KeyMapping(
            "key.citiesinlife.open_city",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            CATEGORY);

    /** Shows every registered structure so they can be inspected and deleted. */
    public static final KeyMapping TOGGLE_STRUCTURE_MODE = new KeyMapping(
            "key.citiesinlife.structure_mode",
            KeyConflictContext.IN_GAME,
            KeyModifier.SHIFT,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_L,
            CATEGORY);

    /**
     * Opens the building editor: rename, re-measure, repair, set capacity by hand.
     *
     * <p>Shift, like the other two toggles that change something rather than open a panel, so it
     * cannot be hit while typing. V because it is the only letter near the other bindings that
     * neither this mod nor vanilla has taken.
     *
     * <p>Creative only. The key is bound in survival too and simply says so when pressed — an
     * unbound key and a refused one look identical, and one of them is a bug report.
     */
    public static final KeyMapping TOGGLE_EDITOR_MODE = new KeyMapping(
            "key.citiesinlife.editor_mode",
            KeyConflictContext.IN_GAME,
            KeyModifier.SHIFT,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            CATEGORY);

    /**
     * Move up the building type list.
     *
     * <p>Arrows rather than the scroll wheel, which was the first attempt: stealing the wheel meant
     * losing hotbar switching, and picking a type by spinning past it never felt like choosing.
     */
    public static final KeyMapping TYPE_PREVIOUS = new KeyMapping(
            "key.citiesinlife.type_previous",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UP,
            CATEGORY);

    /** Move down the building type list. */
    public static final KeyMapping TYPE_NEXT = new KeyMapping(
            "key.citiesinlife.type_next",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_DOWN,
            CATEGORY);

    /**
     * Turn the creative treasury off, and on again.
     *
     * <p>Shift so it cannot be hit while typing a number into something, and I because it is next to
     * nothing else this mod binds and vanilla leaves it alone.
     */
    public static final KeyMapping TOGGLE_CREATIVE_MONEY = new KeyMapping(
            "key.citiesinlife.creative_money",
            KeyConflictContext.IN_GAME,
            KeyModifier.SHIFT,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_I,
            CATEGORY);

    /**
     * Opens the road tool's panel.
     *
     * <p>A letter, for the reason at the top of this file. R because the tool is in hand when it is
     * pressed, so it only has to avoid clashing with what a player does while building.
     */
    public static final KeyMapping OPEN_ROAD_TOOL = new KeyMapping(
            "key.citiesinlife.open_road_tool",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            CATEGORY);

    private KeyBindings() {
    }
}
