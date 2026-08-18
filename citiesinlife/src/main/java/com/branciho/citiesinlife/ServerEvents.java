package com.branciho.citiesinlife;

import com.branciho.citiesinlife.net.ServerActions;
import com.branciho.citiesinlife.plant.PlantSurvey;
import com.branciho.citiesinlife.sim.CitizenDirector;
import com.branciho.citiesinlife.sim.CitySimulation;
import com.branciho.citiesinlife.sim.CreativeFunding;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** Server-side lifecycle: run the simulation, and make sure a joining player sees their city. */
@EventBusSubscriber(modid = CitiesInLife.MOD_ID)
public final class ServerEvents {

    private ServerEvents() {
    }

    /**
     * How often each player is re-sent the power lines and structures around them.
     *
     * <p>Two seconds. Lines and registrations have no blocks of their own, so nothing tells the
     * client about them when you simply walk somewhere new — without this you would ride out to a
     * transmission run you built earlier and find the sky empty.
     */
    private static final int SYNC_INTERVAL_TICKS = 40;

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        CreativeFunding.tick(server);
        CitySimulation.tick(server);
        CitizenDirector.tick(server);

        if (server.getTickCount() % SYNC_INTERVAL_TICKS == 0) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                ServerActions.syncSurroundings(player);
            }
        }
    }

    /** In single player the next world would otherwise inherit this one's cached power plants. */
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PlantSurvey.forgetAll();
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ServerActions.sync(player);
            ServerActions.greetWithWars(player);
        }
    }

    /**
     * Re-sync when a player changes dimension.
     *
     * <p>Cities are per dimension, so without this a player stepping through a portal keeps looking
     * at the territory of the world they left.
     */
    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ServerActions.sync(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ServerActions.forget(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ServerActions.sync(player);
        }
    }
}
