package com.branciho.livingcities;

import com.branciho.livingcities.city.CityRegistry;
import com.branciho.livingcities.scan.BuildingScanService;
import com.branciho.livingcities.sim.CitySimulation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
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
        CityRegistry registry = CityRegistry.get(server);
        long gameTime = server.overworld().getGameTime();

        // Each subsystem decides internally how often it actually runs, and staggers its work across
        // cities so load spreads instead of spiking on one tick. Nothing here loops over every city.
        BuildingScanService.get(server).tick(server);
        CitySimulation.tick(server, registry, gameTime);

        // Still to be attached: CitizenSpawnDirector.tick(server, registry)
    }

    /**
     * Clear the simulation's in-memory caches at both ends of a server's life.
     *
     * <p>The simulation keeps per-city scheduling, cash carry and notification cooldowns in static maps
     * keyed by city id. In single player the JVM outlives the integrated server, so without this a
     * player who quits to the menu and opens a different world would inherit the previous world's
     * schedule offsets and warning cooldowns. Resetting on start as well as stop means a crash or an
     * unclean shutdown cannot leave that state behind either.
     */
    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        CitySimulation.reset();
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        // Drops queued scans and abandons any in-flight background analysis, so a shutdown is not held
        // up by a half-scanned skyscraper and its result cannot be applied to a world that is gone.
        BuildingScanService.shutdown(event.getServer());
        CitySimulation.reset();
    }

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
