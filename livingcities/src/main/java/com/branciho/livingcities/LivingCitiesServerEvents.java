package com.branciho.livingcities;

import com.branciho.livingcities.block.EntranceMarkerBlock;
import com.branciho.livingcities.building.Building;
import com.branciho.livingcities.city.City;
import com.branciho.livingcities.city.CityRegistry;
import com.branciho.livingcities.net.BuildingActions;
import com.branciho.livingcities.npc.CitizenSpawnDirector;
import com.branciho.livingcities.utility.DistributorIndex;
import com.branciho.livingcities.utility.UtilityGrid;
import com.branciho.livingcities.scan.BuildingScanService;
import com.branciho.livingcities.sim.CitySimulation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
        UtilityGrid.get(server).tick(server, registry);
        CitizenSpawnDirector.tick(server, registry);
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
        BuildingActions.reset();
        CitizenSpawnDirector.reset();
        UtilityGrid.resetAll();
        DistributorIndex.resetAll();

        // The scanner deliberately knows nothing about persistence or networking, so the integration
        // layer supplies both: save the new measurements, and push them to anyone looking at the
        // building. Without the second half, a player watching the panel sees "measuring..." forever
        // even though the scan finished.
        BuildingScanService.get(event.getServer()).setListener((level, building, plan) -> {
            MinecraftServer server = level.getServer();
            CityRegistry registry = CityRegistry.get(server);
            registry.setDirty();

            City city = registry.byId(building.cityId());
            if (city == null) {
                return;
            }
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (city.roleOf(player.getUUID()) != null || player.hasPermissions(2)) {
                    BuildingActions.sendDetail(player, building);
                }
            }
        });
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        // Drops queued scans and abandons any in-flight background analysis, so a shutdown is not held
        // up by a half-scanned skyscraper and its result cannot be applied to a world that is gone.
        BuildingScanService.shutdown(event.getServer());
        UtilityGrid.shutdown(event.getServer());
        DistributorIndex.shutdown(event.getServer());
        CitySimulation.reset();
        BuildingActions.reset();
        // Discards the tracked entity list; the citizens themselves are noSave() and simply vanish.
        CitizenSpawnDirector.reset();
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
        if (event.getState().getBlock() instanceof EntranceMarkerBlock) {
            updateEntrance(event.getLevel(), event.getPos(), false);
        }
        markUtilityDirty(event.getLevel(), event.getState());
        markDirty(event.getLevel(), event.getPos());
    }

    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (event.getPlacedBlock().getBlock() instanceof EntranceMarkerBlock) {
            updateEntrance(event.getLevel(), event.getPos(), true);
        }
        markUtilityDirty(event.getLevel(), event.getPlacedBlock());
        markDirty(event.getLevel(), event.getPos());
    }

    /**
     * Flag the network for a rebuild when a utility block changes.
     *
     * <p>Only a flag: the walk itself is deferred and rate limited, because laying a cable run places
     * a hundred blocks in a few seconds and rebuilding per block would be a hundred full walks.
     */
    private static void markUtilityDirty(net.minecraft.world.level.LevelAccessor levelAccessor,
                                       net.minecraft.world.level.block.state.BlockState state) {
        if (levelAccessor instanceof ServerLevel level
                && state.getBlock() instanceof com.branciho.livingcities.utility.UtilityComponent component) {
            UtilityGrid.get(level.getServer()).markDirty(component.utilityKind(), level.dimension());
        }
    }

    /**
     * Attach or detach an entrance marker from whatever building contains it.
     *
     * <p>Handled live rather than only on a rescan, because placing a door marker and having nothing
     * happen until you remember to rescan makes the block look broken. A marker outside every
     * registered building is simply a decorative block.
     */
    private static void updateEntrance(net.minecraft.world.level.LevelAccessor levelAccessor,
                                       net.minecraft.core.BlockPos pos, boolean adding) {
        if (!(levelAccessor instanceof ServerLevel level)) {
            return;
        }
        CityRegistry registry = CityRegistry.get(level.getServer());
        Building building = registry.buildingAt(level.dimension(), pos);
        if (building == null) {
            return;
        }
        if (adding) {
            building.addEntrance(pos);
        } else {
            building.removeEntrance(pos);
        }
        registry.setDirty();
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
