package com.branciho.livingcities.sim;

import com.branciho.livingcities.LivingCities;
import com.branciho.livingcities.city.City;
import com.branciho.livingcities.city.CityRole;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Decides when a city's problems are worth interrupting a player about.
 *
 * <p>The hard part of a warning system is not detecting problems, it is not saying them again. A city
 * with a permanent housing shortage would otherwise produce one chat line per simulation step, forever,
 * and players would stop reading city messages entirely - at which point the genuinely urgent ones stop
 * working too. Two mechanisms prevent that:
 *
 * <ul>
 *   <li><b>State-change detection.</b> A warning is announced when it becomes true, not while it is
 *       true. Clearing the condition resets it so the next onset is heard again.</li>
 *   <li><b>Cooldowns.</b> A condition that stays true is repeated at most once per in-game day, and a
 *       condition that flickers across its threshold cannot re-announce more than once per quarter day.
 *       The second cooldown is the important one: without it, a metric hovering exactly on a threshold
 *       produces an onset every other step and defeats the state-change check entirely.</li>
 * </ul>
 *
 * <p>Delivery is behind {@link Sink} so integration code can route warnings somewhere else - a toast,
 * a city log, a Discord bridge - without this class knowing about networking. The default sink writes
 * to online members directly, which is enough for v0.1 and adds no dependency on the payload layer.
 *
 * <p>All state here is transient. A restarted server re-announces standing problems once, which is the
 * behaviour you want anyway: a mayor logging in after a crash should be told the city is broke.
 */
public final class CityNotifications {

    /** How long a still-true warning waits before repeating itself. One in-game day. */
    private static final long REPEAT_COOLDOWN_TICKS = 24_000L;

    /**
     * Floor on the gap between two announcements of the same warning, however many times the condition
     * has flipped in between. A quarter of an in-game day.
     */
    private static final long ANTI_FLAP_TICKS = 6_000L;

    /** Sentinel for "never announced". Halved so that {@code gameTime - NEVER} cannot overflow. */
    private static final long NEVER = Long.MIN_VALUE / 2L;

    // --- thresholds; a warning fires only when the problem is both proportionally and absolutely real,
    // so a hamlet with one homeless resident is not treated like a housing crisis ---
    private static final double HOUSING_SHORTAGE_RATIO = 0.05D;
    private static final int HOUSING_SHORTAGE_MINIMUM = 5;
    private static final double WORKER_SHORTAGE_RATIO = 0.20D;
    private static final int WORKER_SHORTAGE_MINIMUM = 10;
    private static final double UNEMPLOYMENT_RATIO = 0.20D;
    private static final int UNEMPLOYMENT_MINIMUM = 10;

    /** Reserves below this many days of expenses count as low. */
    private static final int TREASURY_LOW_DAYS = 3;

    /** Where warnings go. Replaceable by integration code; never null. */
    private static volatile Sink sink = CityNotifications::sendToOnlineMembers;

    private static final Map<UUID, WarningState> STATE = new HashMap<>();

    private CityNotifications() {
    }

    /**
     * Delivery target for warnings.
     *
     * <p>Deliberately narrow: it receives a fully formed {@link Component} and an identified city, so an
     * implementation needs no knowledge of the simulation. Always invoked on the server thread.
     */
    @FunctionalInterface
    public interface Sink {
        void send(MinecraftServer server, City city, CityWarning warning, Component message);
    }

    /** Install a delivery target, or pass null to restore the default direct-to-members behaviour. */
    public static void setSink(@Nullable Sink newSink) {
        sink = newSink == null ? CityNotifications::sendToOnlineMembers : newSink;
    }

    public static Sink sink() {
        return sink;
    }

    /**
     * Check one city's warnings and announce whichever have newly become true or are due a repeat.
     *
     * <p>Called by {@link CitySimulation} at the end of a city's step, so it sees a consistent snapshot
     * rather than half-updated live state.
     */
    public static void evaluate(MinecraftServer server, City city, CitySnapshot snapshot, long gameTime) {
        final WarningState state = STATE.computeIfAbsent(city.id(), key -> new WarningState());

        for (CityWarning warning : CityWarning.values()) {
            if (!isActive(warning, snapshot)) {
                // Clearing the flag is what lets the next onset be announced immediately.
                state.active.remove(warning);
                continue;
            }

            final int slot = warning.ordinal();
            final long last = state.lastSent[slot];
            final boolean onset = state.active.add(warning);

            // A world whose time ran backwards (a rollback, or /time set) must not mute warnings for
            // the rest of the session.
            if (gameTime < last) {
                state.lastSent[slot] = NEVER;
                continue;
            }

            final long since = gameTime - last;
            if (since < ANTI_FLAP_TICKS) {
                continue;
            }
            if (!onset && since < REPEAT_COOLDOWN_TICKS) {
                continue;
            }

            state.lastSent[slot] = gameTime;
            announce(server, city, warning, snapshot);
        }
    }

    /** Drop remembered state for a city that no longer exists. */
    public static void forget(UUID cityId) {
        STATE.remove(cityId);
    }

    /** Drop remembered state for every city outside {@code liveCityIds}. */
    public static void retainOnly(Set<UUID> liveCityIds) {
        STATE.keySet().retainAll(liveCityIds);
    }

    /** Forget everything, e.g. when a server stops and its cities are no longer meaningful. */
    public static void reset() {
        STATE.clear();
    }

    // ------------------------------------------------------------------ conditions

    private static boolean isActive(CityWarning warning, CitySnapshot snapshot) {
        return switch (warning) {
            case HOUSING_SHORTAGE -> exceeds(snapshot.homeless(), snapshot.population(),
                    HOUSING_SHORTAGE_RATIO, HOUSING_SHORTAGE_MINIMUM);
            case WORKER_SHORTAGE -> exceeds(snapshot.unfilledJobs(), snapshot.jobs(),
                    WORKER_SHORTAGE_RATIO, WORKER_SHORTAGE_MINIMUM);
            case UNEMPLOYMENT -> exceeds(snapshot.unemployed(), snapshot.workforce(),
                    UNEMPLOYMENT_RATIO, UNEMPLOYMENT_MINIMUM);
            // Suppressed once the treasury has actually failed: TREASURY_EMPTY says the same thing
            // louder, and two messages about one problem is exactly the noise this class exists to stop.
            case TREASURY_LOW -> !snapshot.unpaidExpenses()
                    && snapshot.dailyExpenseCents() > 0L
                    && snapshot.treasuryCents() < snapshot.dailyExpenseCents() * TREASURY_LOW_DAYS;
            case TREASURY_EMPTY -> snapshot.unpaidExpenses();
        };
    }

    private static boolean exceeds(int amount, int total, double ratio, int minimum) {
        return amount >= minimum && total > 0 && amount >= total * ratio;
    }

    // ------------------------------------------------------------------ delivery

    private static void announce(MinecraftServer server, City city, CityWarning warning, CitySnapshot snapshot) {
        final Component message = describe(city, warning, snapshot);
        try {
            sink.send(server, city, warning, message);
        } catch (RuntimeException e) {
            // A third-party sink must never be able to abort a simulation tick.
            LivingCities.LOGGER.error("City warning sink failed for {}", city.name(), e);
        }
    }

    private static Component describe(City city, CityWarning warning, CitySnapshot snapshot) {
        final Component body = switch (warning) {
            case HOUSING_SHORTAGE -> Component.translatable(warning.translationKey(),
                    city.name(), snapshot.homeless());
            case WORKER_SHORTAGE -> Component.translatable(warning.translationKey(),
                    city.name(), snapshot.unfilledJobs());
            case UNEMPLOYMENT -> Component.translatable(warning.translationKey(),
                    city.name(), snapshot.unemployed());
            case TREASURY_LOW -> Component.translatable(warning.translationKey(),
                    city.name(), formatMoney(snapshot.treasuryCents()), formatMoney(-snapshot.netDailyCents()));
            case TREASURY_EMPTY -> Component.translatable(warning.translationKey(), city.name());
        };
        return body.copy().withStyle(warning.colour());
    }

    /**
     * Default sink: tell every online member whose role is senior enough to act on it.
     *
     * <p>Uses the action-bar-free chat line ({@code false}) because a warning the player scrolled past
     * is still recoverable, whereas an action bar message vanishes.
     */
    private static void sendToOnlineMembers(MinecraftServer server, City city, CityWarning warning, Component message) {
        final PlayerList players = server.getPlayerList();
        final CityRole minimum = warning.minimumRole();
        for (Map.Entry<UUID, CityRole> member : city.members().entrySet()) {
            if (!member.getValue().atLeast(minimum)) {
                continue;
            }
            final ServerPlayer player = players.getPlayer(member.getKey());
            if (player != null) {
                player.displayClientMessage(message, false);
            }
        }
    }

    private static String formatMoney(long cents) {
        return String.format(Locale.ROOT, "$%,d.%02d", cents / 100L, Math.abs(cents % 100L));
    }

    /** Per-city memory of what has already been said. */
    private static final class WarningState {

        private final EnumSet<CityWarning> active = EnumSet.noneOf(CityWarning.class);

        /** Game time of the last announcement, indexed by {@link CityWarning#ordinal()}. */
        private final long[] lastSent = new long[CityWarning.values().length];

        private WarningState() {
            Arrays.fill(lastSent, NEVER);
        }
    }
}
