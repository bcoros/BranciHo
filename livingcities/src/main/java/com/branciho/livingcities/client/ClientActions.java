package com.branciho.livingcities.client;

import com.branciho.livingcities.client.screen.CityManagementScreen;
import com.branciho.livingcities.client.screen.CreateCityScreen;
import com.branciho.livingcities.net.payload.CitySummaryPayload;
import com.branciho.livingcities.net.payload.OpenCityScreenPayload;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

/** Client-side reactions to server messages. Never referenced from common code. */
public final class ClientActions {

    private static @Nullable CitySummaryPayload lastSummary;

    private ClientActions() {
    }

    /** The most recent city snapshot the server sent, or null if the player has no city. */
    public static @Nullable CitySummaryPayload lastSummary() {
        return lastSummary;
    }

    public static void acceptSummary(CitySummaryPayload payload) {
        lastSummary = payload;
        if (Minecraft.getInstance().screen instanceof CityManagementScreen screen) {
            screen.refresh(payload);
        }
    }

    public static void openScreen(OpenCityScreenPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        switch (payload.screen()) {
            case CREATE_CITY -> minecraft.setScreen(new CreateCityScreen(payload.context()));
            case MANAGEMENT -> minecraft.setScreen(new CityManagementScreen(lastSummary));
        }
    }

    public static void clear() {
        lastSummary = null;
    }
}
