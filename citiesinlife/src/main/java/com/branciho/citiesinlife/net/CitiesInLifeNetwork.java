package com.branciho.citiesinlife.net;

import com.branciho.citiesinlife.CitiesInLife;
import com.branciho.citiesinlife.net.payload.ArmySyncPayload;
import com.branciho.citiesinlife.net.payload.CityHallActionPayload;
import com.branciho.citiesinlife.net.payload.CityHallPayload;
import com.branciho.citiesinlife.net.payload.HologramPayload;
import com.branciho.citiesinlife.net.payload.ClaimChunkPayload;
import com.branciho.citiesinlife.net.payload.CitySyncPayload;
import com.branciho.citiesinlife.net.payload.CallToArmsPayload;
import com.branciho.citiesinlife.net.payload.LaunchAllPayload;
import com.branciho.citiesinlife.net.payload.MeetingInvitePayload;
import com.branciho.citiesinlife.net.payload.MeetingReplyPayload;
import com.branciho.citiesinlife.net.payload.ModSettingsPayload;
import com.branciho.citiesinlife.net.payload.PeaceOfferPayload;
import com.branciho.citiesinlife.net.payload.RadiationPayload;
import com.branciho.citiesinlife.net.payload.RequestCityHallPayload;
import com.branciho.citiesinlife.net.payload.RequestHologramPayload;
import com.branciho.citiesinlife.net.payload.SetSettingsPayload;
import com.branciho.citiesinlife.net.payload.SetFlagPayload;
import com.branciho.citiesinlife.net.payload.ConfirmDeleteCityPayload;
import com.branciho.citiesinlife.net.payload.DeleteAreaPayload;
import com.branciho.citiesinlife.net.payload.DiplomacyPayload;
import com.branciho.citiesinlife.net.payload.ForeignLandPayload;
import com.branciho.citiesinlife.net.payload.LinkPowerPayload;
import com.branciho.citiesinlife.net.payload.LinkOutletPayload;
import com.branciho.citiesinlife.net.payload.OpenHologramPayload;
import com.branciho.citiesinlife.net.payload.OpenLaunchAllPayload;
import com.branciho.citiesinlife.net.payload.OpenMonitorPayload;
import com.branciho.citiesinlife.net.payload.ReactorSyncPayload;
import com.branciho.citiesinlife.net.payload.RequestReactorPayload;
import com.branciho.citiesinlife.net.payload.UpgradePayload;
import com.branciho.citiesinlife.net.payload.LinkWaterPayload;
import com.branciho.citiesinlife.net.payload.MarkPathPayload;
import com.branciho.citiesinlife.net.payload.MarkRoadPayload;
import com.branciho.citiesinlife.net.payload.MilitaryActionPayload;
import com.branciho.citiesinlife.net.payload.NeighbourCitiesPayload;
import com.branciho.citiesinlife.net.payload.PathSyncPayload;
import com.branciho.citiesinlife.net.payload.PowerLinesPayload;
import com.branciho.citiesinlife.net.payload.RegisterStructurePayload;
import com.branciho.citiesinlife.net.payload.RequestArmyPayload;
import com.branciho.citiesinlife.net.payload.SeizeStructurePayload;
import com.branciho.citiesinlife.net.payload.RequestCityPayload;
import com.branciho.citiesinlife.net.payload.RoadSyncPayload;
import com.branciho.citiesinlife.net.payload.StructureSyncPayload;
import com.branciho.citiesinlife.net.payload.ToggleCreativeMoneyPayload;
import com.branciho.citiesinlife.net.payload.WaterLinesPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import com.branciho.citiesinlife.net.payload.LaunchMissilePayload;
import com.branciho.citiesinlife.net.payload.MissileMapPayload;
import com.branciho.citiesinlife.net.payload.RequestMissileMapPayload;

/**
 * Payload registration.
 *
 * <p>The client-bound handlers hand off to {@link ClientCityCache}, which is written to import no
 * client classes precisely so that this common-side registration does not drag the client into a
 * dedicated server's class loader.
 */
@EventBusSubscriber(modid = CitiesInLife.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class CitiesInLifeNetwork {

    private static final String VERSION = "1";

    private CitiesInLifeNetwork() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);

        registrar.playToServer(RegisterStructurePayload.TYPE, RegisterStructurePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> onServer(context, player -> ServerActions.registerStructure(player, payload))));

        registrar.playToServer(DeleteAreaPayload.TYPE, DeleteAreaPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> onServer(context, player -> ServerActions.deleteArea(player, payload))));

        registrar.playToServer(LinkPowerPayload.TYPE, LinkPowerPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> onServer(context, player -> ServerActions.linkPower(player, payload))));

        registrar.playToServer(LinkWaterPayload.TYPE, LinkWaterPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> onServer(context, player -> ServerActions.linkWater(player, payload))));

        registrar.playToServer(LinkOutletPayload.TYPE, LinkOutletPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> onServer(context, player -> ServerActions.linkOutlet(player, payload))));

        registrar.playToServer(UpgradePayload.TYPE, UpgradePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> onServer(context, player -> ServerActions.upgrade(player, payload))));

        registrar.playToServer(RequestReactorPayload.TYPE, RequestReactorPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> onServer(context, player -> ServerActions.sendReactor(player, payload))));

        registrar.playToServer(MarkPathPayload.TYPE, MarkPathPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> onServer(context, player -> ServerActions.markPath(player, payload))));

        registrar.playToServer(MarkRoadPayload.TYPE, MarkRoadPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> onServer(context, player -> ServerActions.markRoad(player, payload))));

        registrar.playToServer(DiplomacyPayload.TYPE, DiplomacyPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> onServer(context, player -> ServerActions.diplomacy(player, payload))));

        registrar.playToServer(SetFlagPayload.TYPE, SetFlagPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> onServer(context, player -> ServerActions.setFlag(player, payload))));

        registrar.playToServer(ClaimChunkPayload.TYPE, ClaimChunkPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> onServer(context, player -> ServerActions.claimChunk(player, payload))));

        registrar.playToServer(ToggleCreativeMoneyPayload.TYPE, ToggleCreativeMoneyPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> onServer(context, ServerActions::toggleCreativeMoney)));

        registrar.playToServer(SeizeStructurePayload.TYPE, SeizeStructurePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> onServer(context, player -> ServerActions.seizeStructure(player, payload))));

        registrar.playToServer(RequestArmyPayload.TYPE, RequestArmyPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> onServer(context, ServerActions::syncArmy)));

        registrar.playToServer(MilitaryActionPayload.TYPE, MilitaryActionPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> onServer(context, player -> ServerActions.militaryAction(player, payload))));

        registrar.playToServer(RequestCityPayload.TYPE, RequestCityPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> onServer(context, ServerActions::sync)));

        registrar.playToServer(RequestMissileMapPayload.TYPE,
                RequestMissileMapPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> onServer(context, ServerActions::syncMissileMap)));

        registrar.playToServer(LaunchMissilePayload.TYPE, LaunchMissilePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> onServer(context, player -> ServerActions.launchMissile(player, payload))));

        registrar.playToServer(LaunchAllPayload.TYPE, LaunchAllPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> onServer(context, player -> ServerActions.launchAll(player, payload))));

        registrar.playToServer(RequestCityHallPayload.TYPE, RequestCityHallPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> onServer(context, ServerActions::syncCityHall)));

        registrar.playToServer(CityHallActionPayload.TYPE, CityHallActionPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> onServer(context, player -> ServerActions.cityHallAction(player, payload))));

        registrar.playToServer(RequestHologramPayload.TYPE, RequestHologramPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> onServer(context, ServerActions::syncHologram)));

        registrar.playToServer(MeetingReplyPayload.TYPE, MeetingReplyPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> onServer(context, player -> ServerActions.meetingReply(player, payload))));

        registrar.playToClient(CitySyncPayload.TYPE, CitySyncPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientCityCache.accept(payload)));

        registrar.playToClient(StructureSyncPayload.TYPE, StructureSyncPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientCityCache.accept(payload)));

        registrar.playToClient(PowerLinesPayload.TYPE, PowerLinesPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientCityCache.accept(payload)));

        registrar.playToClient(WaterLinesPayload.TYPE, WaterLinesPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientCityCache.accept(payload)));

        registrar.playToClient(PathSyncPayload.TYPE, PathSyncPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientCityCache.accept(payload)));

        registrar.playToClient(OpenMonitorPayload.TYPE, OpenMonitorPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> ClientCityCache.openMonitor(payload.monitor())));

        registrar.playToClient(ReactorSyncPayload.TYPE, ReactorSyncPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientCityCache.accept(payload)));

        registrar.playToClient(RoadSyncPayload.TYPE, RoadSyncPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientCityCache.accept(payload)));

        registrar.playToClient(NeighbourCitiesPayload.TYPE, NeighbourCitiesPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientCityCache.accept(payload)));

        registrar.playToClient(ForeignLandPayload.TYPE, ForeignLandPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientCityCache.accept(payload)));

        registrar.playToClient(ArmySyncPayload.TYPE, ArmySyncPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientArmyCache.accept(payload)));

        registrar.playToClient(ConfirmDeleteCityPayload.TYPE, ConfirmDeleteCityPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientCityCache.accept(payload)));

        registrar.playToClient(CallToArmsPayload.TYPE, CallToArmsPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientCityCache.accept(payload)));

        // Two types, because what the server sends carries whether you may edit it and what the
        // client sends must not - and one payload registered in both directions is a handler that
        // has to ask which side it is on.
        registrar.playToClient(ModSettingsPayload.TYPE, ModSettingsPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientCityCache.accept(payload)));

        registrar.playToClient(RadiationPayload.TYPE, RadiationPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientCityCache.accept(payload)));

        registrar.playToClient(PeaceOfferPayload.TYPE, PeaceOfferPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientCityCache.accept(payload)));

        registrar.playToClient(MissileMapPayload.TYPE, MissileMapPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> ClientCityCache.accept(payload)));

        registrar.playToClient(CityHallPayload.TYPE, CityHallPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> ClientCityCache.accept(payload)));

        registrar.playToClient(OpenHologramPayload.TYPE, OpenHologramPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(ClientCityCache::openHologram));

        registrar.playToClient(OpenLaunchAllPayload.TYPE, OpenLaunchAllPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(ClientCityCache::openLaunchAll));

        registrar.playToClient(HologramPayload.TYPE, HologramPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> ClientCityCache.accept(payload)));

        registrar.playToClient(MeetingInvitePayload.TYPE, MeetingInvitePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> ClientCityCache.accept(payload)));

        registrar.playToServer(SetSettingsPayload.TYPE, SetSettingsPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> onServer(context, player ->
                                ServerActions.applySettings(player, payload))));
    }

    /** Run an action only if the sender really is a server player. */
    private static void onServer(IPayloadContext context, java.util.function.Consumer<ServerPlayer> action) {
        if (context.player() instanceof ServerPlayer player) {
            action.accept(player);
        }
    }

    public static void sendTo(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    /** Send a payload from the client to the server. Safe to call only on the client. */
    public static void sendToServer(CustomPacketPayload payload) {
        PacketDistributor.sendToServer(payload);
    }
}
