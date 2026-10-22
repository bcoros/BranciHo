package com.branciho.citiesinlife.client;

import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * Makes the mod's settings reachable from the Mods menu.
 *
 * <p>The config has existed since the first alpha and has never been openable, because registering
 * a {@code ModConfigSpec} only creates the file — it does not put a Config button next to the mod
 * in the list. So the citizen cap, the car settings and the blast scale were all editable in
 * theory and, in practice, only by finding the TOML by hand.
 *
 * <p>Its own class, and reached only through a {@code Dist.CLIENT} branch, because
 * {@link ConfigurationScreen} is a client type. Java loads a class on first active use, so on a
 * dedicated server this one is never touched and its client-only references never resolve.
 */
public final class ClientConfigScreen {

    private ClientConfigScreen() {
    }

    public static void register(ModContainer container) {
        // NeoForge already ships the screen; all this does is say which mod it belongs to.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
