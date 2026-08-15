package com.branciho.livingcities.client;

import com.branciho.livingcities.net.payload.BuildingDetailPayload;
import com.branciho.livingcities.net.payload.CitySummaryPayload;
import com.branciho.livingcities.net.payload.OpenCityScreenPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Entry point for client-bound payloads.
 *
 * <p>Only common types appear in this class's signatures and fields, so a dedicated server can load
 * it harmlessly while registering the payload types. The actual client work happens in
 * {@link ClientActions}, which is never touched on a server because these methods never run there.
 */
public final class ClientPayloadHandler {

    private ClientPayloadHandler() {
    }

    public static void handleOpenScreen(OpenCityScreenPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientActions.openScreen(payload));
    }

    public static void handleCitySummary(CitySummaryPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientActions.acceptSummary(payload));
    }

    public static void handleBuildingDetail(BuildingDetailPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientActions.acceptBuildingDetail(payload));
    }
}
