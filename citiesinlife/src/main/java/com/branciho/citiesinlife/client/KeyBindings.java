package com.branciho.citiesinlife.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;

public final class KeyBindings {

    public static final String CATEGORY = "key.categories.citiesinlife";

    /** Semicolon opens the city panel. Rebindable like anything else. */
    public static final KeyMapping OPEN_CITY = new KeyMapping(
            "key.citiesinlife.open_city",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_SEMICOLON,
            CATEGORY);

    /** Shift+L shows every registered structure so they can be inspected and deleted. */
    public static final KeyMapping TOGGLE_STRUCTURE_MODE = new KeyMapping(
            "key.citiesinlife.structure_mode",
            KeyConflictContext.IN_GAME,
            KeyModifier.SHIFT,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_L,
            CATEGORY);

    private KeyBindings() {
    }
}
