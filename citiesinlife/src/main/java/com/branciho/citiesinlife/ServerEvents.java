package com.branciho.citiesinlife;

import com.branciho.citiesinlife.net.ServerActions;
import com.branciho.citiesinlife.sim.CitySimulation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Server-side lifecycle: run the simulation, and make sure a joining player sees their city. */
@EventBusSubscriber(modid = CitiesInLife.MOD_ID)
public final class ServerEvents {

    private ServerEvents() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        CitySimulation.tick(event.getServer());
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ServerActions.sync(player);
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
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ServerActions.sync(player);
        }
    }
}
