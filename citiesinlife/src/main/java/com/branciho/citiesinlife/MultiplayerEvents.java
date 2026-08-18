package com.branciho.citiesinlife;

import com.branciho.citiesinlife.city.City;
import com.branciho.citiesinlife.city.CityData;
import com.branciho.citiesinlife.city.Diplomacy;
import com.branciho.citiesinlife.entity.CitizenEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
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
     * Right-clicking anything in a foreign city, with one deliberate exception.
     *
     * <p>This started out gated on the block having a block entity, on the theory that a block entity
     * meant "holds something". It let almost everything through. A bucket of water or lava never
     * fires a place event at all; nor does flint and steel, bone meal, a hoe tilling a field, an axe
     * stripping a log, or a shovel cutting a path. A valve is a plain block with a state, so a
     * visitor could shut a city's water off. Every one of those is a right click, so this is where
     * they are caught, and the rule is now the other way round: everything is refused unless it is
     * one of the ways through a town.
     *
     * <p>The exception is doors, gates, trapdoors, buttons, levers and pressure plates. Being unable
     * to walk through somebody's city would be a worse experience than the mischief it prevents, and
     * none of them can be used to take anything.
     */
    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        if (level.isClientSide || passage(level.getBlockState(event.getPos()))) {
            return;
        }
        if (refuse(event.getEntity(), event.getPos())) {
            event.setCanceled(true);
        }
    }

    /** The ways through a town, which stay usable no matter whose town it is. */
    private static boolean passage(BlockState state) {
        return state.is(BlockTags.DOORS)
                || state.is(BlockTags.TRAPDOORS)
                || state.is(BlockTags.FENCE_GATES)
                || state.is(BlockTags.BUTTONS)
                || state.is(BlockTags.PRESSURE_PLATES)
                || state.is(Blocks.LEVER);
    }

    /**
     * Explosions do not cross a border either.
     *
     * <p>Only when a player is behind them. A creeper wandering into a city is weather, and the
     * mod's own turbine detonating is the whole point of the turbine detonating - filtering those
     * would be protecting a city from itself. But primed TNT, a bed in the Nether or a respawn
     * anchor is somebody making a decision, and the border applies to decisions.
     */
    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (!(event.getExplosion().getIndirectSourceEntity() instanceof Player player)) {
            return;
        }
        if (!(player instanceof ServerPlayer server)) {
            return;
        }
        MinecraftServer minecraftServer = server.getServer();
        if (minecraftServer == null) {
            return;
        }
        event.getAffectedBlocks().removeIf(pos ->
                !Diplomacy.mayInterfereWith(minecraftServer, server,
                        Diplomacy.owner(minecraftServer, server.level().dimension(), pos)));
        event.getAffectedEntities().removeIf(entity -> protectedCitizen(server, entity));
    }

    /**
     * Nor may you lead one away.
     *
     * <p>Damage is not the only way to take somebody's citizen. A lead, a boat, a name tag - all of
     * them are an interaction rather than a hit, and none of them reach the damage events.
     */
    @SubscribeEvent
    public static void onInteractEntity(PlayerInteractEvent.EntityInteract event) {
        if (protectedCitizen(event.getEntity(), event.getTarget())) {
            event.setCanceled(true);
        }
    }

    /** The same, for interactions aimed at a particular spot on somebody rather than at them. */
    @SubscribeEvent
    public static void onInteractEntitySpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (protectedCitizen(event.getEntity(), event.getTarget())) {
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
     * Everything else that could hurt a citizen.
     *
     * <p>This asks about the <em>victim</em> first, which the first version of it did not, and that
     * was the whole flaw. Keyed off the attacker, the rule only held when a player was named on the
     * damage — so a dispenser of arrows on a clock, TNT lit by a lever, a respawn anchor, or simply
     * shoving somebody into a lava pit all went through untouched, because none of them name anybody.
     * The guarantee was true at one block and false at thirty-one different ranges.
     *
     * <p>So a citizen is now hurt by exactly one thing: a player its city has given the right to.
     * Everything else — fire, falling, drowning, a zombie, an unattributed explosion — leaves it
     * alone. That is broader than "other players cannot kill your people", deliberately: it is the
     * only version of the rule that closes the whole class rather than one vector at a time, and it
     * has the side benefit that a city's people stop quietly disappearing overnight.
     */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof CitizenEntity citizen)) {
            return;
        }
        City owner = cityOf(citizen);
        if (owner == null) {
            return;
        }
        Player blamed = responsibleFor(event.getSource());
        if (blamed instanceof ServerPlayer player && entitled(player, owner)) {
            return;
        }
        event.setCanceled(true);
        if (blamed instanceof ServerPlayer player) {
            warn(player, owner, "message.citiesinlife.protected_citizen");
        }
    }

    /**
     * A hook is not damage, and it moves people.
     *
     * <p>Reel a citizen out of its own city and every rule above still holds where it now stands —
     * which is exactly the point of doing it. Cancelling the impact means the hook never catches.
     */
    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!(event.getRayTraceResult() instanceof EntityHitResult hit)
                || !(hit.getEntity() instanceof CitizenEntity citizen)) {
            return;
        }
        City owner = cityOf(citizen);
        if (owner == null) {
            return;
        }
        if (event.getProjectile().getOwner() instanceof ServerPlayer player && entitled(player, owner)) {
            return;
        }
        event.setCanceled(true);
    }

    /**
     * Who is actually to blame for a hit.
     *
     * <p>Four links, because the game hides the culprit behind at least that many: the archer behind
     * an arrow, the owner behind a wolf, the player who primed the TNT. Anything further out than
     * this genuinely has nobody behind it, and the caller treats that as "nobody may".
     */
    private static @Nullable Player responsibleFor(DamageSource source) {
        if (source.getEntity() instanceof Player player) {
            return player;
        }
        if (source.getDirectEntity() instanceof Player direct) {
            return direct;
        }
        if (source.getDirectEntity() instanceof TraceableEntity traceable
                && traceable.getOwner() instanceof Player shooter) {
            return shooter;
        }
        if (source.getEntity() instanceof OwnableEntity ownable
                && ownable.getOwner() instanceof Player keeper) {
            return keeper;
        }
        return null;
    }

    /** The city a citizen belongs to, or null if it has none or its city is gone. */
    private static @Nullable City cityOf(CitizenEntity citizen) {
        MinecraftServer server = citizen.level().getServer();
        UUID cityId = citizen.cityId();
        if (server == null || cityId == null) {
            return null;
        }
        return CityData.get(server).city(cityId);
    }

    private static boolean entitled(ServerPlayer player, City owner) {
        MinecraftServer server = player.getServer();
        return server != null && Diplomacy.mayInterfereWith(server, player, owner);
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
