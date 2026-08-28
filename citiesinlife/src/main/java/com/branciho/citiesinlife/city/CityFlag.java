package com.branciho.citiesinlife.city;

import net.minecraft.world.item.DyeColor;

/**
 * A city's flag: forty squares of dye.
 *
 * <p>Eight by five, which is close enough to the two-to-three of a real flag to look like one and
 * small enough that designing one is a minute's work rather than an afternoon's. Every square is a
 * vanilla dye colour and nothing else — no gradients, no images, no uploading anything — because
 * the point is a thing you can make in the middle of founding a city, and because sixteen colours
 * is what every other flag in Minecraft has to work with.
 *
 * <p>Stored as one byte per square rather than packed. Forty bytes is nothing next to the rest of a
 * city, and a plain array is a thing the editor, the block and the packet can all read without
 * anybody having to remember which end the nibbles go.
 */
public final class CityFlag {

    public static final int WIDTH = 8;
    public static final int HEIGHT = 5;
    public static final int CELLS = WIDTH * HEIGHT;

    private CityFlag() {
    }

    /** A blank flag, in the colour a city starts out with. */
    public static byte[] blank() {
        byte[] cells = new byte[CELLS];
        java.util.Arrays.fill(cells, (byte) DyeColor.LIGHT_GRAY.getId());
        return cells;
    }

    /**
     * Make a stored array safe to use, whatever it turns out to be.
     *
     * <p>Called on everything that arrives from disk or from the network. A flag is the one piece
     * of city data a player types in directly, so it is the one most likely to arrive the wrong
     * length or with a colour index that does not exist.
     */
    public static byte[] sanitise(byte[] cells) {
        byte[] safe = blank();
        if (cells == null) {
            return safe;
        }
        for (int i = 0; i < CELLS && i < cells.length; i++) {
            safe[i] = (byte) Math.floorMod(cells[i], 16);
        }
        return safe;
    }

    public static DyeColor colourAt(byte[] cells, int x, int y) {
        int index = y * WIDTH + x;
        if (cells == null || index < 0 || index >= cells.length) {
            return DyeColor.LIGHT_GRAY;
        }
        return DyeColor.byId(Math.floorMod(cells[index], 16));
    }

    /** The packed 0xRRGGBB of a square, which is what both the editor and the block want. */
    public static int rgbAt(byte[] cells, int x, int y) {
        return colourAt(cells, x, y).getTextureDiffuseColor() & 0xFFFFFF;
    }

    // ---- the presets, so nobody has to paint forty squares to get a tricolour ----

    public static byte[] horizontal(DyeColor top, DyeColor middle, DyeColor bottom) {
        byte[] cells = blank();
        for (int y = 0; y < HEIGHT; y++) {
            DyeColor band = y < 2 ? top : y < 3 ? middle : bottom;
            for (int x = 0; x < WIDTH; x++) {
                cells[y * WIDTH + x] = (byte) band.getId();
            }
        }
        return cells;
    }

    public static byte[] vertical(DyeColor left, DyeColor middle, DyeColor right) {
        byte[] cells = blank();
        for (int x = 0; x < WIDTH; x++) {
            DyeColor band = x < 3 ? left : x < 5 ? middle : right;
            for (int y = 0; y < HEIGHT; y++) {
                cells[y * WIDTH + x] = (byte) band.getId();
            }
        }
        return cells;
    }

    public static byte[] cross(DyeColor field, DyeColor bar) {
        byte[] cells = blank();
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                boolean on = y == 2 || x == 2;
                cells[y * WIDTH + x] = (byte) (on ? bar.getId() : field.getId());
            }
        }
        return cells;
    }

    public static byte[] solid(DyeColor colour) {
        byte[] cells = new byte[CELLS];
        java.util.Arrays.fill(cells, (byte) colour.getId());
        return cells;
    }
}
