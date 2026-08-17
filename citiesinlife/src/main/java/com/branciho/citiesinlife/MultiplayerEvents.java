package com.branciho.citiesinlife;

import com.branciho.citiesinlife.city.City;
import com.branciho.citiesinlife.city.CityData;
import com.branciho.citiesinlife.city.Diplomacy;
import com.branciho.citiesinlife.entity.CitizenEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Territory protection.
 *
 * <p>A city border that anybody can build through is a decoration. This is what makes it a border:
 * inside somebody else's claimed chunks you cannot place a block, break one, open anything with a
 * lid on it, or lay a finger on their citizens — unless they have said you may, or unless one of you
 * has declared war.
 *
 * <p>Every rule here asks {@link Diplomacy} rather than working it out for itself, so "yours" means
 * exactly one thing across the whole mod. And every rule short-circuits on unclaimed ground first,
 * because that is where nearly every block on a server is placed and this runs on all of them.
 */
@EventBusSubscriber(modid = CitiesInLife.MOD_ID)
public final class MultiplayerEvents {

    private MultiplayerEvents() {
    }

    // ----------------------------------------------------------------- blocks

    /**
     * A claim runs from bedrock to the build limit.
     *
     * <p>Territory is per chunk with no height to it, so mining under somebody's city is protected
     * exactly as much as knocking their wall down. That is deliberate — a city that could be
     * undermined from below would need a second, invisible rule to explain — but it is worth being
     * explicit about, because it is not what "a border" looks like from the surface.
     */
    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        // NeoForge pre-cancels this for its own reasons; re-deciding a refusal somebody else has
        // already made would only risk turning it back on.
        if (event.isCanceled()) {
            return;
        }
        if (refuse(event.getPlayer(), event.getPos())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof Player player && refuse(player, event.getPos())) {
            event.setCanceled(true);
        }
    }

    /**
     * Anything with a lid on it is off limits too.
     *
     * <p>Gated on the block having a block entity, which is a rough but accurate stand-in for "this
     * holds something or does something": chests, furnaces, and every machine this mod adds. Doors,
     * buttons and levers keep working, because being unable to walk through somebody's town would be
     * a worse experience than the theft it prevents.
     */
    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        if (level.isClientSide || level.getBlockEntity(event.getPos()) == null) {
            return;
        }
        if (refuse(event.getEntity(), event.getPos())) {
            event.setCanceled(true);
        }
    }

    // --------------------------------------------------------------- citizens

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (protectedCitizen(event.getEntity(), event.getTarget())) {
            event.setCanceled(true);
        }
    }

    /**
     * The same rule for anything that reaches a citizen without a fist behind it.
     *
     * <p>Arrows, potions, a snowball. Covering only the melee event would leave "you cannot kill
     * their citizens" true at one block and false at thirty, which is not a rule, it is a nuisance.
     */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getSource().getEntity() instanceof Player player
                && protectedCitizen(player, event.getEntity())) {
            event.setCanceled(true);
        }
    }

    /**
     * Whether this citizen belongs to somebody who has not invited this player to shoot at it.
     *
     * <p>A citizen carries its own city, so this needs no territory lookup at all — you cannot kill
     * a foreign citizen that has wandered outside its own borders either, which is right: it is
     * still their citizen.
     */
    private static boolean protectedCitizen(Player attacker, Entity target) {
        if (!(target instanceof CitizenEntity citizen) || !(attacker instanceof ServerPlayer player)) {
            return false;
        }
        MinecraftServer server = player.getServer();
        UUID cityId = citizen.cityId();
        if (server == null || cityId == null) {
            return false;
        }
        City owner = CityData.get(server).city(cityId);
        if (owner == null || Diplomacy.mayInterfereWith(server, player, owner)) {
            return false;
        }
        warn(player, owner, "message.citiesinlife.protected_citizen");
        return true;
    }

    // ---------------------------------------------------------------- shared

    /** True when this player must be stopped, and says why on the way past. */
    private static boolean refuse(@Nullable Player maybePlayer, BlockPos pos) {
        if (!(maybePlayer instanceof ServerPlayer player)) {
            return false;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }
        City owner = Diplomacy.owner(server, player.level().dimension(), pos);
        if (owner == null || Diplomacy.mayInterfereWith(server, player, owner)) {
            return false;
        }
        warn(player, owner, "message.citiesinlife.protected_land");
        return true;
    }

    /**
     * Say no, and say what would change the answer.
     *
     * <p>On the action bar rather than in chat: a player who has walked into a neighbour's city is
     * about to hit this several times in a row, and a wall of red chat would be worse than the
     * refusal itself.
     */
    private static void warn(ServerPlayer player, City owner, String key) {
        MinecraftServer server = player.getServer();
        boolean hasCity = server != null
                && Diplomacy.visitorCity(server, player, owner.dimension()) != null;
        // One line, not two. The action bar holds a single message, so sending the hint separately
        // would simply wipe the refusal a tick later and leave the player wondering what happened.
        player.displayClientMessage(Component.translatable(
                hasCity ? key : "message.citiesinlife.protected_no_city", owner.name()), true);
    }
}
