package com.branciho.citiesinlife.client.render;

import com.branciho.citiesinlife.CitiesInLife;
import com.branciho.citiesinlife.city.CityFlag;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * One uploaded texture per distinct flag design, made on demand.
 *
 * <p>An eight by five image is the honest way to draw a flag whose forty squares are only known at
 * runtime. The alternative — forty individually coloured quads per pole per frame — is the same
 * picture drawn forty times more expensively, and it would still have to fight the block atlas to
 * get a plain white pixel to tint.
 *
 * <p>Keyed on the design rather than on the pole, so a hundred flagpoles flying the same city's
 * colours cost exactly one texture. Cities rarely change their flag, and when one does the old
 * texture stops being asked for; the cache is bounded below so that a player who sits in the flag
 * editor cycling colours cannot fill the GPU with abandoned uploads.
 */
public final class FlagTextures {

    /** Well past the number of cities a server has, and small enough to be a real bound. */
    private static final int MAX_CACHED = 96;

    private static final Map<String, ResourceLocation> CACHE = new HashMap<>();

    private FlagTextures() {
    }

    public static ResourceLocation of(byte[] flag) {
        byte[] cells = CityFlag.sanitise(flag);
        String key = key(cells);
        ResourceLocation known = CACHE.get(key);
        if (known != null) {
            return known;
        }
        if (CACHE.size() >= MAX_CACHED) {
            // Dropped wholesale rather than by age. Rebuilding a handful of eight-by-five images is
            // cheaper than the bookkeeping that would let us evict exactly the right one.
            CACHE.clear();
        }

        NativeImage image = new NativeImage(CityFlag.WIDTH, CityFlag.HEIGHT, false);
        for (int y = 0; y < CityFlag.HEIGHT; y++) {
            for (int x = 0; x < CityFlag.WIDTH; x++) {
                // NativeImage is ABGR, and getting that round the wrong way gives you a flag in
                // the right shape and entirely the wrong colours.
                int rgb = CityFlag.rgbAt(cells, x, y);
                int abgr = 0xFF000000
                        | ((rgb & 0xFF) << 16)
                        | (rgb & 0xFF00)
                        | ((rgb >> 16) & 0xFF);
                image.setPixelRGBA(x, y, abgr);
            }
        }

        DynamicTexture texture = new DynamicTexture(image);
        ResourceLocation id = Minecraft.getInstance().getTextureManager()
                .register(CitiesInLife.MOD_ID + "_flag", texture);
        CACHE.put(key, id);
        return id;
    }

    private static String key(byte[] cells) {
        return Arrays.toString(cells);
    }
}
