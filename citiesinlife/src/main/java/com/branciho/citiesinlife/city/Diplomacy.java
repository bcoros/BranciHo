package com.branciho.citiesinlife.city;

import com.branciho.citiesinlife.config.CitiesInLifeConfig;
import com.branciho.citiesinlife.structure.Structure;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * The one question the whole of multiplayer comes down to: may this player touch this?
 *
 * <p>Every protection rule in the mod funnels through here, so there is exactly one definition of
 * "yours" and no chance of the block-break rule and the citizen rule quietly disagreeing about it.
 *
 * <p>Deliberately cheap in the common case. Almost every block a player breaks is on unclaimed
 * ground, and that answer costs one lookup in a chunk index and nothing else — worth caring about,
 * because this runs on every block placed and broken by every player on the server.
 */
public final class Diplomacy {

    private Diplomacy() {
    }

    /**
     * Which city, if any, this position answers to.
     *
     * <p>Claimed ground first, and then — for ground nobody has claimed — whichever city has a
     * registered building standing on it. That second half matters more than it looks: a power plant
     * is deliberately allowed outside city limits, and without this its owner was the only player on
     * the server who could not be sure it would still be there tomorrow.
     */
    public static @Nullable City owner(MinecraftServer server, ResourceKey<Level> dimension, BlockPos pos) {
        CityData data = CityData.get(server);
        City claimed = data.cityAtChunk(dimension, ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
        if (claimed != null) {
            return claimed;
        }
        Structure standing = data.structureAt(dimension, pos);
        return standing == null ? null : data.city(standing.cityId());
    }

    /**
     * How a landowner regards a visitor.
     *
     * <p>War is checked from both sides because a war exists as soon as one city wants one. Permission
     * is checked from the owner's side only, because it is theirs to give.
     */
    public static Relation stance(@Nullable City owner, @Nullable City visitor) {
        if (owner == null || visitor == null) {
            return Relation.NEUTRAL;
        }
        if (owner.hostileTo(visitor.id()) || visitor.hostileTo(owner.id())) {
            return Relation.WAR;
        }
        return owner.grantedTo(visitor.id()) ? Relation.ALLIED : Relation.NEUTRAL;
    }

    /**
     * Whether this player may place, break or otherwise interfere at this position.
     *
     * <p>Unclaimed ground is everybody's, your own city is yours, and somebody else's needs either
     * their permission or a war.
     *
     * <p>Operators are <em>not</em> exempt unless the server has been configured to exempt them. They
     * were, unconditionally, and it quietly broke the entire feature in the only setting that matters
     * for it: the person hosting a world is an operator, so they could raze a friend's city while the
     * friend could not lay a block in theirs.
     */
    public static boolean mayInterfere(MinecraftServer server, ServerPlayer player, BlockPos pos) {
        return mayInterfereWith(server, player, owner(server, player.level().dimension(), pos));
    }

    /** The same question when the owning city is already in hand — citizens carry theirs. */
    public static boolean mayInterfereWith(MinecraftServer server, ServerPlayer player, @Nullable City owner) {
        if (owner == null || owner.owner().equals(player.getUUID())) {
            return true;
        }
        if (CitiesInLifeConfig.opsIgnoreBorders() && player.hasPermissions(2)) {
            return true;
        }
        return stance(owner, visitorCity(server, player, owner.dimension())).permitsInterference();
    }

    /** How the owning city regards this player, for the refusal message. */
    public static Relation relationFor(MinecraftServer server, ServerPlayer player, City owner) {
        return stance(owner, visitorCity(server, player, owner.dimension()));
    }

    /**
     * The city this player owns in a given dimension.
     *
     * <p>Null when they have not founded one, and that is a real limitation held on purpose:
     * permission is granted between cities, so a player with no city of their own cannot be given
     * building rights anywhere. They are told exactly that when they are refused, rather than being
     * left to guess.
     */
    public static @Nullable City visitorCity(MinecraftServer server, ServerPlayer player,
                                             ResourceKey<Level> dimension) {
        return CityData.get(server).cityOf(player.getUUID(), dimension);
    }
}
