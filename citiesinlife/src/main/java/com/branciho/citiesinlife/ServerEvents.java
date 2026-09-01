package com.branciho.citiesinlife;

import com.branciho.citiesinlife.net.ServerActions;
import com.branciho.citiesinlife.plant.PlantSurvey;
import com.branciho.citiesinlife.sim.CitizenDirector;
import com.branciho.citiesinlife.sim.CitySimulation;
import com.branciho.citiesinlife.sim.CreativeFunding;
import com.branciho.citiesinlife.sim.ServiceDirector;
import com.branciho.citiesinlife.sim.WarDirector;
import com.branciho.citiesinlife.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import com.branciho.citiesinlife.nuclear.Meltdown;
import com.branciho.citiesinlife.nuclear.Radiation;
import com.branciho.citiesinlife.nuclear.ReactorSurvey;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import com.branciho.citiesinlife.city.Demolition;
import com.branciho.citiesinlife.city.Meeting;
import com.branciho.citiesinlife.city.Sirens;
import com.branciho.citiesinlife.missile.MissileDirector;
import com.branciho.citiesinlife.missile.Warhead;

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
        // Before the city tick: a melting reactor has left the ten-second cadence and is stepped
        // every tick until it is finished.
        Meltdown.tick(server);
        // Outlives the meltdown it came from, and is ticked separately for exactly that reason.
        Radiation.tick(server);
        // After the explosions, and one tick behind them by design: the event that lists an
        // explosion's victims fires before any of them are gone.
        Demolition.tick(server);
        // The crater an incoming warhead opens, one shell per tick, and the silos in the middle of
        // opening their doors.
        Warhead.tick(server);
        MissileDirector.tick(server);
        Meeting.tick(server);
        CitySimulation.tick(server);
        CitizenDirector.tick(server);
        ServiceDirector.tick(server);
        WarDirector.tick(server);

        if (server.getTickCount() % SYNC_INTERVAL_TICKS == 0) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                ServerActions.syncSurroundings(player);
            }
        }
    }

    /** In single player the next world would otherwise inherit this one's cached power plants. */
    /**
     * Give an already-placed siren the block entity it did not used to need.
     *
     * <p>Sirens were plain blocks until this version. A block entity is only written to the save
     * file once it exists, so every siren standing in a world made before now has no block entity
     * stored against it — and nothing creates one on load, because the chunk only registers block
     * entities it has data for. Without this, a siren you built last week would never tick, never
     * sound, and never even <em>draw</em>, since all of it is now drawn by a renderer that needs a
     * block entity to hang off. It would silently disappear.
     *
     * <p>Asking for the block entity is what creates it: {@code Level#getBlockEntity} creates and
     * registers one on demand for any state whose block wants it. So this only has to find them.
     *
     * <p>The cost is a palette test per section, not a walk over every block. A chunk section
     * knows which blocks it might contain, and the overwhelming majority say "no siren here" for
     * free; only a section that really has one is walked.
     */
    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel().isClientSide() || !(event.getChunk() instanceof LevelChunk chunk)) {
            return;
        }
        Level level = chunk.getLevel();
        LevelChunkSection[] sections = chunk.getSections();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int index = 0; index < sections.length; index++) {
            LevelChunkSection section = sections[index];
            if (section.hasOnlyAir()
                    || !section.maybeHas(state -> state.is(ModBlocks.SIREN.get()))) {
                continue;
            }
            int baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(index));
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        if (!section.getBlockState(x, y, z).is(ModBlocks.SIREN.get())) {
                            continue;
                        }
                        cursor.set(chunk.getPos().getMinBlockX() + x, baseY + y,
                                chunk.getPos().getMinBlockZ() + z);
                        level.getBlockEntity(cursor);
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PlantSurvey.forgetAll();
        ReactorSurvey.forgetAll();
        Radiation.clear();
        Demolition.clear();
        Warhead.clear();
        MissileDirector.clear();
        Meeting.clear();
        Sirens.clear();
        Meltdown.forgetAll();
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
