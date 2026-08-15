package com.branciho.livingcities;

import com.branciho.livingcities.city.CityRegistry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The server-side heartbeat and world-change hooks.
 *
 * <p>This is the only place that drives the simulation, and it deliberately does almost nothing itself:
 * each subsystem decides internally how often it actually needs to run. Nothing here iterates every city
 * or every building on every tick.
 */
@EventBusSubscriber(modid = LivingCities.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class LivingCitiesServerEvents {

    private LivingCitiesServerEvents() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();

        // Integration point for the staggered subsystems. Each decides internally how often it runs:
        //   BuildingScanService.get(server).tick(server)          - budgeted geometry scans
        //   CitySimulation.tick(server, registry, gameTime)       - population, jobs, economy
        //   CitizenSpawnDirector.tick(server, registry)           - representative NPCs
        // Wired up as each package lands; keeping the hook here means the tick path is already in place
        // and reviewed rather than bolted on later.
        if (server.getTickCount() % IDLE_HEARTBEAT_TICKS == 0) {
            CityRegistry.get(server);
        }
    }

    /**
     * How often the registry is touched while no subsystem is wired in yet. This exists so the
     * server-global data is loaded early rather than on the first player interaction.
     */
    private static final int IDLE_HEARTBEAT_TICKS = 600;

    /**
     * Editing blocks inside a registered building invalidates its measurements.
     *
     * <p>We only mark it stale rather than rescanning immediately: a player laying a floor places
     * hundreds of blocks in a few seconds, and rescanning a skyscraper on each one would be ruinous.
     * The stale flag lets capacity keep using the last good numbers until a rescan is requested.
     */
    @SubscribeEvent
    public static void onBlockBroken(BlockEvent.BreakEvent event) {
        markDirty(event.getLevel(), event.getPos());
    }

    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        markDirty(event.getLevel(), event.getPos());
    }

    private static void markDirty(net.minecraft.world.level.LevelAccessor levelAccessor,
                                  net.minecraft.core.BlockPos pos) {
        if (!(levelAccessor instanceof ServerLevel level)) {
            return;
        }
        MinecraftServer server = level.getServer();
        CityRegistry.get(server).markDirtyAt(level.dimension(), pos);
    }
}
