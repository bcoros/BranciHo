package com.branciho.livingcities.net;

import com.branciho.livingcities.LivingCities;
import com.branciho.livingcities.net.payload.AssignBuildingPayload;
import com.branciho.livingcities.net.payload.BuildingDetailPayload;
import com.branciho.livingcities.net.payload.BuildingListPayload;
import com.branciho.livingcities.net.payload.RequestBuildingsPayload;
import com.branciho.livingcities.net.payload.CitySummaryPayload;
import com.branciho.livingcities.net.payload.CityOverlayPayload;
import com.branciho.livingcities.net.payload.RemoveBuildingPayload;
import com.branciho.livingcities.net.payload.RequestOverlayPayload;
import com.branciho.livingcities.net.payload.RescanBuildingPayload;
import com.branciho.livingcities.net.payload.SetFloorZonePayload;
import com.branciho.livingcities.net.payload.ClaimChunkPayload;
import com.branciho.livingcities.net.payload.CreateCityPayload;
import com.branciho.livingcities.net.payload.OpenCityScreenPayload;
import com.branciho.livingcities.net.payload.RequestCityDataPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Payload registration and the server-side send helpers.
 *
 * <p>Client-bound handlers live in {@code com.branciho.livingcities.client.ClientPayloadHandler}.
 * That class is only ever <em>invoked</em> on a client; the method references below are resolved
 * lazily, and the class itself exposes no client types in its signatures, so a dedicated server can
 * safely load this class.
 */
@EventBusSubscriber(modid = LivingCities.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class LivingCitiesNetwork {

    /** Bump when the wire format changes incompatibly. */
    private static final String PROTOCOL_VERSION = "1";

    private LivingCitiesNetwork() {
    }

    @SubscribeEvent
    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);

        // --- client -> server (always treated as untrusted requests) ---
        registrar.playToServer(CreateCityPayload.TYPE, CreateCityPayload.STREAM_CODEC,
                LivingCitiesNetwork::onCreateCity);
        registrar.playToServer(ClaimChunkPayload.TYPE, ClaimChunkPayload.STREAM_CODEC,
                LivingCitiesNetwork::onClaimChunk);
        registrar.playToServer(RequestCityDataPayload.TYPE, RequestCityDataPayload.STREAM_CODEC,
                LivingCitiesNetwork::onRequestCityData);
        registrar.playToServer(AssignBuildingPayload.TYPE, AssignBuildingPayload.STREAM_CODEC,
                LivingCitiesNetwork::onAssignBuilding);
        registrar.playToServer(SetFloorZonePayload.TYPE, SetFloorZonePayload.STREAM_CODEC,
                LivingCitiesNetwork::onSetFloorZone);
        registrar.playToServer(RescanBuildingPayload.TYPE, RescanBuildingPayload.STREAM_CODEC,
                LivingCitiesNetwork::onRescanBuilding);
        registrar.playToServer(RemoveBuildingPayload.TYPE, RemoveBuildingPayload.STREAM_CODEC,
                LivingCitiesNetwork::onRemoveBuilding);
        registrar.playToServer(RequestOverlayPayload.TYPE, RequestOverlayPayload.STREAM_CODEC,
                LivingCitiesNetwork::onRequestOverlay);
        registrar.playToServer(RequestBuildingsPayload.TYPE, RequestBuildingsPayload.STREAM_CODEC,
                LivingCitiesNetwork::onRequestBuildings);

        // --- server -> client ---
        registrar.playToClient(OpenCityScreenPayload.TYPE, OpenCityScreenPayload.STREAM_CODEC,
                com.branciho.livingcities.client.ClientPayloadHandler::handleOpenScreen);
        registrar.playToClient(CitySummaryPayload.TYPE, CitySummaryPayload.STREAM_CODEC,
                com.branciho.livingcities.client.ClientPayloadHandler::handleCitySummary);
        registrar.playToClient(BuildingDetailPayload.TYPE, BuildingDetailPayload.STREAM_CODEC,
                com.branciho.livingcities.client.ClientPayloadHandler::handleBuildingDetail);
        registrar.playToClient(CityOverlayPayload.TYPE, CityOverlayPayload.STREAM_CODEC,
                com.branciho.livingcities.client.ClientPayloadHandler::handleCityOverlay);
        registrar.playToClient(BuildingListPayload.TYPE, BuildingListPayload.STREAM_CODEC,
                com.branciho.livingcities.client.ClientPayloadHandler::handleBuildingList);
    }

    private static void onCreateCity(CreateCityPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            context.enqueueWork(() -> ServerPayloadHandler.createCity(player, payload));
        }
    }

    private static void onClaimChunk(ClaimChunkPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            context.enqueueWork(() -> ServerPayloadHandler.claimChunk(player, payload));
        }
    }

    private static void onRequestCityData(RequestCityDataPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            context.enqueueWork(() -> ServerPayloadHandler.requestCityData(player, payload));
        }
    }

    private static void onAssignBuilding(AssignBuildingPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            context.enqueueWork(() -> BuildingActions.assignBuilding(player, payload));
        }
    }

    private static void onSetFloorZone(SetFloorZonePayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            context.enqueueWork(() -> BuildingActions.setFloorZone(player, payload));
        }
    }

    private static void onRescanBuilding(RescanBuildingPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            context.enqueueWork(() -> BuildingActions.rescanBuilding(player, payload));
        }
    }

    private static void onRemoveBuilding(RemoveBuildingPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            context.enqueueWork(() -> BuildingActions.removeBuilding(player, payload));
        }
    }

    private static void onRequestBuildings(RequestBuildingsPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            context.enqueueWork(() -> BuildingActions.requestBuildings(player, payload));
        }
    }

    private static void onRequestOverlay(RequestOverlayPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            context.enqueueWork(() -> OverlayActions.requestOverlay(player, payload));
        }
    }

    public static void sendTo(ServerPlayer player, net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }
}
