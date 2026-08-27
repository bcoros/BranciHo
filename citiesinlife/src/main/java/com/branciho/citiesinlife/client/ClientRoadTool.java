package com.branciho.citiesinlife.client;

import com.branciho.citiesinlife.road.RoadTile;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

/**
 * What the road tool is currently painting.
 *
 * <p>Pure client state, like {@link ClientWarWand} and {@link StructureMode}. Nothing here is ever
 * sent on its own — it is folded into {@link #flags()} at the moment a box is confirmed, and the
 * server sanitises the result anyway. A brush that lived on the server would mean a round trip every
 * time the player pressed a button in the panel.
 */
public final class ClientRoadTool {

    /** What kind of thing the next box becomes. */
    public enum Brush {
        ROAD,
        INTERSECTION,
        PARKING,
        HIGHWAY;

        public String id() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        public Component displayName() {
            return Component.translatable("road_brush.citiesinlife." + id());
        }
    }

    private static Brush brush = Brush.ROAD;

    /** An ordinary two-way street is the sane thing to start on. */
    private static int directions = RoadTile.NORTH | RoadTile.SOUTH;

    private ClientRoadTool() {
    }

    public static Brush brush() {
        return brush;
    }

    public static void setBrush(Brush next) {
        brush = next;
    }

    public static int directions() {
        return directions;
    }

    public static void setDirections(int mask) {
        directions = mask & RoadTile.DIRECTIONS;
    }

    /** Flip one way of travel on or off. */
    public static void toggle(Direction direction) {
        directions ^= RoadTile.bit(direction);
    }

    /** Whether the direction toggles mean anything for the current brush. */
    public static boolean directionsMatter() {
        return brush == Brush.ROAD || brush == Brush.HIGHWAY;
    }

    /**
     * What the next confirmed box will actually store.
     *
     * <p>Junctions and bays get every direction, not none. A crossroads no one may cross and a car
     * park no car may leave are both easy to paint by accident and impossible to diagnose from
     * inside the game.
     */
    public static int flags() {
        return switch (brush) {
            case ROAD -> directions == 0 ? RoadTile.DIRECTIONS : directions;
            case HIGHWAY -> (directions == 0 ? RoadTile.DIRECTIONS : directions) | RoadTile.HIGHWAY;
            case INTERSECTION -> RoadTile.DIRECTIONS | RoadTile.INTERSECTION;
            case PARKING -> RoadTile.DIRECTIONS | RoadTile.PARKING;
        };
    }

    public static void reset() {
        brush = Brush.ROAD;
        directions = RoadTile.NORTH | RoadTile.SOUTH;
    }
}
