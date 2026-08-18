package com.branciho.citiesinlife.net;

import com.branciho.citiesinlife.CitiesInLife;
import com.branciho.citiesinlife.net.payload.ArmySyncPayload;
import com.branciho.citiesinlife.net.payload.ClaimChunkPayload;
import com.branciho.citiesinlife.net.payload.CitySyncPayload;
import com.branciho.citiesinlife.net.payload.ConfirmDeleteCityPayload;
import com.branciho.citiesinlife.net.payload.DeleteAreaPayload;
import com.branciho.citiesinlife.net.payload.DiplomacyPayload;
import com.branciho.citiesinlife.net.payload.ForeignLandPayload;
import com.branciho.citiesinlife.net.payload.LinkPowerPayload;
import com.branciho.citiesinlife.net.payload.LinkOutletPayload;
import com.branciho.citiesinlife.net.payload.LinkWaterPayload;
import com.branciho.citiesinlife.net.payload.MarkPathPayload;
import com.branciho.citiesinlife.net.payload.MilitaryActionPayload;
import com.branciho.citiesinlife.net.payload.NeighbourCitiesPayload;
import com.branciho.citiesinlife.net.payload.PathSyncPayload;
import com.branciho.citiesinlife.net.payload.PowerLinesPayload;
import com.branciho.citiesinlife.net.payload.RegisterStructurePayload;
import com.branciho.citiesinlife.net.payload.RequestArmyPayload;
import com.branciho.citiesinlife.net.payload.SeizeStructurePayload;
import com.branciho.citiesinlife.net.payload.RequestCityPayload;
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

        registrar.playToServer(MarkPathPayload.TYPE, MarkPathPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> onServer(context, player -> ServerActions.markPath(player, payload))));

        registrar.playToServer(DiplomacyPayload.TYPE, DiplomacyPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> onServer(context, player -> ServerActions.diplomacy(player, payload))));

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

        registrar.playToClient(NeighbourCitiesPayload.TYPE, NeighbourCitiesPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientCityCache.accept(payload)));

        registrar.playToClient(ForeignLandPayload.TYPE, ForeignLandPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientCityCache.accept(payload)));

        registrar.playToClient(ArmySyncPayload.TYPE, ArmySyncPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientArmyCache.accept(payload)));

        registrar.playToClient(ConfirmDeleteCityPayload.TYPE, ConfirmDeleteCityPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientCityCache.accept(payload)));
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
