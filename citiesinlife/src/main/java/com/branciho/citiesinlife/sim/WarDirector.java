package com.branciho.citiesinlife.sim;

import com.branciho.citiesinlife.city.City;
import com.branciho.citiesinlife.city.CityData;
import com.branciho.citiesinlife.city.Diplomacy;
import com.branciho.citiesinlife.city.Relation;
import com.branciho.citiesinlife.entity.ServiceEntity;
import com.branciho.citiesinlife.registry.ModEntities;
import com.branciho.citiesinlife.service.ServiceType;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Who is standing where, and what that is costing the people who used to own it.
 *
 * <p>Ground changes hands by being occupied. Nothing else takes a chunk — not killing the defenders,
 * not blowing up their buildings — which is what stops a war being decided in the thirty seconds
 * before anybody can respond. A chunk with soldiers standing in it falls in a couple of minutes; a
 * chunk they walked through falls not at all.
 *
 * <p>Counted here rather than in the soldiers' own goal because four soldiers in one chunk should
 * take it four times as fast, and a goal only ever knows about the one unit running it.
 */
public final class WarDirector {

    /** How often ground is counted. Once a second. */
    private static final int INTERVAL_TICKS = 20;

    /** What one soldier is worth per second, before training. */
    private static final int PRESSURE_PER_SOLDIER = 1;

    /**
     * What each level of training adds.
     *
     * <p>Untrained, a chunk is a hundred seconds of standing still. Fully trained, it is twenty-five
     * — which is the difference the player is paying for at the Military Tool.
     */
    private static final int PRESSURE_PER_TRAINING = 1;

    private WarDirector() {
    }

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % INTERVAL_TICKS != 0) {
            return;
        }
        CityData data = CityData.get(server);
        for (ServerLevel level : server.getAllLevels()) {
            pressLevel(server, data, level);
        }
    }

    private static void pressLevel(MinecraftServer server, CityData data, ServerLevel level) {
        List<? extends ServiceEntity> soldiers = level.getEntities(ModEntities.SERVICE.get(),
                entity -> entity.isAlive() && entity.role() == ServiceType.MILITARY);
        if (soldiers.isEmpty()) {
            return;
        }

        // Chunk -> how hard each army is pushing on it. Two armies on the same chunk are counted
        // separately, because a siege belongs to whoever is winning it and not to the ground.
        Map<UUID, Long2IntOpenHashMap> pressure = new HashMap<>();

        for (ServiceEntity soldier : soldiers) {
            City mine = soldier.city();
            if (mine == null) {
                continue;
            }
            long chunkKey = ChunkPos.asLong(soldier.getBlockX() >> 4, soldier.getBlockZ() >> 4);
            City owner = data.cityAtChunk(level.dimension(), chunkKey);
            if (owner == null || owner.id().equals(mine.id())
                    || Diplomacy.stance(owner, mine) != Relation.WAR) {
                continue;
            }
            pressure.computeIfAbsent(mine.id(), key -> new Long2IntOpenHashMap())
                    .addTo(chunkKey, PRESSURE_PER_SOLDIER
                            + soldier.training() * PRESSURE_PER_TRAINING);
        }

        for (Map.Entry<UUID, Long2IntOpenHashMap> entry : pressure.entrySet()) {
            City attacker = data.city(entry.getKey());
            if (attacker == null) {
                continue;
            }
            for (Long2IntMap.Entry chunk : entry.getValue().long2IntEntrySet()) {
                long chunkKey = chunk.getLongKey();
                City defender = data.cityAtChunk(level.dimension(), chunkKey);
                if (defender == null) {
                    continue;
                }
                if (data.advanceSiege(level.dimension(), chunkKey, attacker.id(), chunk.getIntValue())) {
                    capture(server, data, level, attacker, defender, chunkKey);
                }
            }
        }
    }

    private static void capture(MinecraftServer server, CityData data, ServerLevel level,
                                City attacker, City defender, long chunkKey) {
        data.transferChunk(defender, attacker, chunkKey);
        ChunkPos chunk = new ChunkPos(chunkKey);
        tell(server, attacker, "message.citiesinlife.chunk_taken", defender.name(), chunk);
        tell(server, defender, "message.citiesinlife.chunk_lost", attacker.name(), chunk);
    }

    private static void tell(MinecraftServer server, City city, String key, String other,
                             ChunkPos chunk) {
        ServerPlayer player = playerFor(server, city.owner());
        if (player != null) {
            player.sendSystemMessage(Component.translatable(key, other, chunk.x, chunk.z));
        }
    }

    private static @Nullable ServerPlayer playerFor(MinecraftServer server, UUID id) {
        return server.getPlayerList().getPlayer(id);
    }
}
