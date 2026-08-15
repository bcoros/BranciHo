package com.branciho.livingcities.client;

import com.branciho.livingcities.client.screen.BuildingPanelScreen;
import com.branciho.livingcities.client.screen.CityManagementScreen;
import com.branciho.livingcities.client.screen.CreateCityScreen;
import com.branciho.livingcities.net.payload.BuildingDetailPayload;
import com.branciho.livingcities.net.payload.BuildingListPayload;
import com.branciho.livingcities.net.payload.CityOverlayPayload;
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

    /**
     * Show a building's details, opening the panel if it is not already the active screen.
     *
     * <p>Refreshing in place matters: a scan finishing, or a zone change being accepted, arrives while
     * the player is looking at the panel, and replacing the screen would reset their scroll position
     * and steal focus mid-click.
     */
    public static void acceptBuildingDetail(BuildingDetailPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof BuildingPanelScreen panel) {
            panel.refresh(payload);
        } else {
            minecraft.setScreen(new BuildingPanelScreen(payload));
        }
    }

    public static void openScreen(OpenCityScreenPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        switch (payload.screen()) {
            case CREATE_CITY -> minecraft.setScreen(new CreateCityScreen(payload.context()));
            case MANAGEMENT -> minecraft.setScreen(new CityManagementScreen(lastSummary));
        }
    }

    /** A page of buildings only makes sense while the management screen is open to receive it. */
    public static void acceptBuildingList(BuildingListPayload payload) {
        if (Minecraft.getInstance().screen instanceof CityManagementScreen screen) {
            screen.acceptBuildings(payload);
        }
    }

    public static void acceptOverlay(CityOverlayPayload payload) {
        ClientOverlayCache.accept(payload);
    }

    public static void clear() {
        lastSummary = null;
        ClientOverlayCache.clear();
    }
}
