package com.branciho.citiesinlife.road;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

/**
 * What one block of road is, packed into an int.
 *
 * <p>A road tile is not a block. Like pavement it is a position in server data, so a street can be
 * built out of whatever the player likes and still be a street. Unlike pavement it carries a
 * direction, because a car has to know which way round the one-way system goes.
 *
 * <p><b>The sign convention is the thing to get right.</b> A direction bit means a car may
 * <em>leave</em> this tile that way. {@link #NORTH} is toward -Z, {@link #SOUTH} toward +Z,
 * {@link #EAST} toward +X, {@link #WEST} toward -X. So an ordinary two-way north-south street is
 * {@code NORTH | SOUTH} on <em>every</em> tile of it, not NORTH on one lane and SOUTH on the other.
 * Painting only NORTH is what makes a one-way street one-way.
 *
 * <p>A parking bay is deliberately given all four direction bits as well as {@link #PARKING}. A bay
 * with no way out of it is a bay no car can ever leave, and since every journey starts at one, that
 * would quietly make the whole feature inert.
 */
public final class RoadTile {

    /** Travel toward -Z. */
    public static final int NORTH = 1;
    /** Travel toward +X. */
    public static final int EAST = 2;
    /** Travel toward +Z. */
    public static final int SOUTH = 4;
    /** Travel toward -X. */
    public static final int WEST = 8;

    public static final int DIRECTIONS = NORTH | EAST | SOUTH | WEST;

    /** Where streets cross. Always passable every way, whatever else is painted on it. */
    public static final int INTERSECTION = 16;

    /** Somewhere a citizen may fetch a car from. Always has {@link #DIRECTIONS} too. */
    public static final int PARKING = 32;

    /** Faster, and the only kind of road a car may follow across someone else's border. */
    public static final int HIGHWAY = 64;

    /** Every bit this mod understands. Anything else in a packet is discarded. */
    public static final int ALL = DIRECTIONS | INTERSECTION | PARKING | HIGHWAY;

    private RoadTile() {
    }

    /**
     * The bit for a horizontal direction, or 0 for up and down.
     *
     * <p>{@link Direction} has six constants, so the default arm is not optional.
     */
    public static int bit(Direction direction) {
        return switch (direction) {
            case NORTH -> NORTH;
            case EAST -> EAST;
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            default -> 0;
        };
    }

    /** Whether a car standing on these flags may drive off that way. */
    public static boolean allows(int flags, Direction direction) {
        int bit = bit(direction);
        if (bit == 0) {
            return false;
        }
        // A junction and a bay are passable every way regardless of what was painted on them, so a
        // player cannot accidentally draw a crossroads no one can cross.
        if ((flags & (INTERSECTION | PARKING)) != 0) {
            return true;
        }
        return (flags & bit) != 0;
    }

    public static boolean is(int flags, int flag) {
        return (flags & flag) != 0;
    }

    /** What to call this tile in the HUD, most specific kind first. */
    public static Component describe(int flags) {
        if (is(flags, PARKING)) {
            return Component.translatable("hud.citiesinlife.road_parking");
        }
        if (is(flags, INTERSECTION)) {
            return Component.translatable("hud.citiesinlife.road_intersection");
        }
        if (is(flags, HIGHWAY)) {
            return Component.translatable("hud.citiesinlife.road_highway");
        }
        return Component.translatable("hud.citiesinlife.road_plain");
    }
}
