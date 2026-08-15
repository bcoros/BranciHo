package com.branciho.livingcities.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;

public final class KeyBindings {

    public static final String CATEGORY = "key.categories.livingcities";

    /** ALT+O by default, as in the design brief. Rebindable like any other key. */
    public static final KeyMapping OPEN_MANAGEMENT = new KeyMapping(
            "key.livingcities.open_management",
            KeyConflictContext.IN_GAME,
            KeyModifier.ALT,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_O,
            CATEGORY);

    /** Toggles the world overlays (territory borders, building outlines). */
    public static final KeyMapping TOGGLE_OVERLAY = new KeyMapping(
            "key.livingcities.toggle_overlay",
            KeyConflictContext.IN_GAME,
            KeyModifier.ALT,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_B,
            CATEGORY);

    private KeyBindings() {
    }
}
