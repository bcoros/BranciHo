package com.branciho.citiesinlife.city;

import com.branciho.citiesinlife.net.CitiesInLifeNetwork;
import com.branciho.citiesinlife.net.payload.MeetingInvitePayload;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A meeting called from a city hall: everyone invited, whoever accepts brought in, and nobody sent
 * home until the host says so.
 *
 * <p>A city hall has, until now, been somewhere you go once. This is the thing that gives it a
 * second use: a room you can actually get other players into, on purpose, to argue about a border
 * or sign a treaty or demand something. The mod already had the machinery for wars and pacts and
 * had no machinery at all for the conversation that ought to come before them.
 *
 * <p>Everyone online is invited, enemies included. A summit you can only hold with your allies is a
 * party, and the interesting use of this is getting somebody you are at war with to stand still for
 * a minute.
 *
 * <p>Held in memory rather than saved. A meeting is a thing that is happening, not a thing a world
 * has; if the server stops mid-meeting there is nothing worth restoring, and a saved meeting would
 * come back holding return positions for players who have long since walked somewhere else.
 *
 * <p><b>Getting home.</b> Accepting records exactly where the guest was standing, and only the host
 * ending the meeting sends them back. That is the rule the mod was asked for, and it is also what
 * stops this being a free teleport: you cannot use a meeting to travel, because you do not choose
 * when you leave. The two ways out that do not need the host are both failures rather than
 * features — the host going offline, and the host's city ceasing to exist — and both return
 * everybody rather than stranding them.
 */
public final class Meeting {

    /**
     * How many guests one meeting will hold.
     *
     * <p>Sixteen is far more than a server will ever get into one room and small enough that the
     * ring of seats stays a ring rather than a crowd standing inside each other.
     */
    public static final int MAX_ATTENDEES = 16;

    /** How far out from the host the ring of seats sits. Close enough to talk, clear of the host. */
    private static final int SEAT_RADIUS = 3;

    /** Where a guest was, and who they are, so the host can put them back exactly. */
    private record Guest(String name, ResourceKey<Level> dimension,
                         double x, double y, double z, float yaw, float pitch) {
    }

    private static final class Session {
        private final UUID cityId;
        private final UUID host;
        private final String hostName;
        private final String cityName;
        private final ResourceKey<Level> dimension;
        private final BlockPos seat;

        /** Insertion-ordered, so the board reads in the order people actually turned up. */
        private final Map<UUID, Guest> guests = new LinkedHashMap<>();

        private Session(UUID cityId, UUID host, String hostName, String cityName,
                        ResourceKey<Level> dimension, BlockPos seat) {
            this.cityId = cityId;
            this.host = host;
            this.hostName = hostName;
            this.cityName = cityName;
            this.dimension = dimension;
            this.seat = seat;
        }
    }

    /** One meeting per city, keyed by the city holding it. */
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    /** Which meeting a player is sitting in, so nobody is in two at once. */
    private static final Map<UUID, UUID> ATTENDING = new HashMap<>();

    private Meeting() {
    }

    /**
     * Call a meeting, and ask everybody online.
     *
     * @return a translation key if it could not be called, or null if it was
     */
    public static @Nullable String open(MinecraftServer server, ServerPlayer host, City city) {
        if (SESSIONS.containsKey(city.id())) {
            return "message.citiesinlife.meeting_already";
        }
        // The host cannot be sitting in somebody else's meeting while calling their own: they would
        // owe two return trips and only one of them could be honoured.
        if (ATTENDING.containsKey(host.getUUID())) {
            return "message.citiesinlife.meeting_busy";
        }

        Session session = new Session(city.id(), host.getUUID(), host.getGameProfile().getName(),
                city.name(), host.level().dimension(), host.blockPosition());
        SESSIONS.put(city.id(), session);

        MeetingInvitePayload invite =
                new MeetingInvitePayload(city.id(), session.hostName, session.cityName);
        for (ServerPlayer everyone : server.getPlayerList().getPlayers()) {
            if (!everyone.getUUID().equals(host.getUUID())) {
                CitiesInLifeNetwork.sendTo(everyone, invite);
            }
        }
        return null;
    }

    /**
     * Accept an invitation.
     *
     * <p>Everything is re-checked here rather than trusted from the invitation: a meeting can end,
     * fill up or move dimension between the question being asked and answered, and a client is free
     * to send whatever it likes.
     *
     * @return a translation key if they could not be brought in, or null if they were
     */
    public static @Nullable String join(MinecraftServer server, ServerPlayer player, UUID cityId) {
        Session session = SESSIONS.get(cityId);
        if (session == null) {
            return "message.citiesinlife.meeting_gone";
        }
        if (session.host.equals(player.getUUID()) || session.guests.containsKey(player.getUUID())) {
            return "message.citiesinlife.meeting_here";
        }
        if (ATTENDING.containsKey(player.getUUID())) {
            return "message.citiesinlife.meeting_busy";
        }
        if (session.guests.size() >= MAX_ATTENDEES) {
            return "message.citiesinlife.meeting_full";
        }

        ServerLevel level = server.getLevel(session.dimension);
        if (level == null || !level.isLoaded(session.seat)) {
            return "message.citiesinlife.meeting_gone";
        }
        BlockPos arrival = seatFor(level, session.seat, session.guests.size());

        // Recorded BEFORE the teleport, because after it the player is standing at the meeting and
        // where they came from is gone for good.
        session.guests.put(player.getUUID(), new Guest(
                player.getGameProfile().getName(), player.level().dimension(),
                player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot()));
        ATTENDING.put(player.getUUID(), cityId);

        player.teleportTo(level, arrival.getX() + 0.5D, arrival.getY(), arrival.getZ() + 0.5D,
                player.getYRot(), player.getXRot());

        Component arrived = Component.translatable("message.citiesinlife.meeting_arrived",
                player.getGameProfile().getName());
        tellTheRoom(server, session, arrived, player.getUUID());
        return null;
    }

    /**
     * End a meeting and send everybody back where they came from.
     *
     * @return whether there was a meeting to end
     */
    public static boolean close(MinecraftServer server, UUID cityId, String reasonKey) {
        Session session = SESSIONS.remove(cityId);
        if (session == null) {
            return false;
        }
        for (Map.Entry<UUID, Guest> entry : session.guests.entrySet()) {
            ATTENDING.remove(entry.getKey());
            ServerPlayer guest = server.getPlayerList().getPlayer(entry.getKey());
            if (guest == null) {
                continue;
            }
            Guest seat = entry.getValue();
            ServerLevel home = server.getLevel(seat.dimension());
            if (home != null) {
                guest.teleportTo(home, seat.x(), seat.y(), seat.z(), seat.yaw(), seat.pitch());
            }
            guest.sendSystemMessage(Component.translatable(reasonKey, session.cityName));
        }
        ServerPlayer host = server.getPlayerList().getPlayer(session.host);
        if (host != null) {
            host.sendSystemMessage(Component.translatable(reasonKey, session.cityName));
        }
        return true;
    }

    /**
     * Every tick, cheaply.
     *
     * <p>Two things end a meeting that nobody asked to end: the host disconnecting, and the host's
     * city ceasing to exist — which, in a mod with warheads in it, is a real way for a city hall to
     * stop being somewhere you can hold a meeting. Both would otherwise leave guests standing in a
     * field with no way home, since only the host can dismiss them.
     */
    public static void tick(MinecraftServer server) {
        if (SESSIONS.isEmpty()) {
            return;
        }
        CityData data = CityData.get(server);
        for (Session session : List.copyOf(SESSIONS.values())) {
            if (server.getPlayerList().getPlayer(session.host) == null) {
                close(server, session.cityId, "message.citiesinlife.meeting_host_gone");
            } else if (data.city(session.cityId) == null) {
                close(server, session.cityId, "message.citiesinlife.meeting_city_gone");
            }
        }
    }

    /**
     * A player has logged out.
     *
     * <p>A guest who leaves is simply no longer at the meeting; there is nobody to teleport, and
     * their return position dies with them — they logged out at the city hall and that is where
     * they will log back in. Ending the whole meeting because one guest quit would punish everyone
     * else, and the host quitting is handled in {@link #tick} instead.
     */
    public static void forget(UUID playerId) {
        UUID cityId = ATTENDING.remove(playerId);
        if (cityId == null) {
            return;
        }
        Session session = SESSIONS.get(cityId);
        if (session != null) {
            session.guests.remove(playerId);
        }
    }

    public static void clear() {
        SESSIONS.clear();
        ATTENDING.clear();
    }

    public static boolean running(UUID cityId) {
        return SESSIONS.containsKey(cityId);
    }

    /** The host and everyone who has turned up, in arrival order, for the live board. */
    public static List<String> roll(UUID cityId) {
        Session session = SESSIONS.get(cityId);
        if (session == null) {
            return List.of();
        }
        List<String> names = new ArrayList<>(session.guests.size() + 1);
        names.add(session.hostName);
        for (Guest guest : session.guests.values()) {
            names.add(guest.name());
        }
        return names;
    }

    /** Which meeting this player is sitting in, if any — so the screen can offer them nothing. */
    public static @Nullable UUID attending(UUID playerId) {
        return ATTENDING.get(playerId);
    }

    private static void tellTheRoom(MinecraftServer server, Session session, Component line,
                                    UUID except) {
        for (UUID id : List.copyOf(session.guests.keySet())) {
            if (id.equals(except)) {
                continue;
            }
            ServerPlayer guest = server.getPlayerList().getPlayer(id);
            if (guest != null) {
                guest.sendSystemMessage(line);
            }
        }
        ServerPlayer host = server.getPlayerList().getPlayer(session.host);
        if (host != null && !session.host.equals(except)) {
            host.sendSystemMessage(line);
        }
    }

    /**
     * A place to stand, in a ring around the host.
     *
     * <p>Everyone arriving on the host's exact block would be a pile rather than a meeting. The
     * ring is walked from the requested seat outwards and gives up back onto the host's own block:
     * a guest standing inside the host is untidy, but a guest dropped into a wall or over a cliff
     * because their seat happened to be there is worse.
     */
    private static BlockPos seatFor(ServerLevel level, BlockPos around, int index) {
        double step = Math.PI * 2.0D / 8.0D;
        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = (index + attempt) * step;
            BlockPos candidate = around.offset(
                    (int) Math.round(Math.cos(angle) * SEAT_RADIUS), 0,
                    (int) Math.round(Math.sin(angle) * SEAT_RADIUS));
            if (roomToStand(level, candidate)) {
                return candidate;
            }
            if (roomToStand(level, candidate.above())) {
                return candidate.above();
            }
        }
        return around;
    }

    private static boolean roomToStand(ServerLevel level, BlockPos pos) {
        return level.isLoaded(pos)
                && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
                && !level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty();
    }
}
