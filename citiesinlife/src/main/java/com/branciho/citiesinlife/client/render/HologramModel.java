package com.branciho.citiesinlife.client.render;

import com.branciho.citiesinlife.CitiesInLife;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * The projection table: a plinth, an emitter ring, and a globe of light above it.
 *
 * <p>The globe is not a sphere — nothing here can be — but eight slabs on rotated rings read as one
 * from any angle a player will look at it from, and they read as a <em>projection</em> rather than
 * as an object, which is better than a sphere would have been anyway. It is drawn on the translucent
 * pass at full brightness, so it glows and you can see the far side of it through the near one.
 *
 * <p>Same coordinate warning as the rest of the block models here: authored with <strong>Y pointing
 * down</strong>, flipped by the renderer, origin at the middle of the block's bottom face.
 */
public final class HologramModel {

    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(CitiesInLife.id("hologram_map"), "main");

    /** The plinth, which is solid, and the globe, which is not. */
    public static final String BASE = "base";
    public static final String GLOBE = "globe";

    private static final int TEXTURE_WIDTH = 64;
    private static final int TEXTURE_HEIGHT = 64;

    /** Flat colour bands in the sheet. */
    private static final int METAL_V = 0;
    private static final int GLOW_V = 32;

    /** How many latitude bands the globe is built from. */
    private static final int BANDS = 5;

    private HologramModel() {
    }

    public static LayerDefinition create() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        PartDefinition base = root.addOrReplaceChild(BASE, CubeListBuilder.create()
                        // the plinth
                        .texOffs(0, METAL_V)
                        .addBox(-7.0F, -3.0F, -7.0F, 14, 3, 14)
                        // the column
                        .texOffs(0, METAL_V)
                        .addBox(-4.0F, -9.0F, -4.0F, 8, 6, 8)
                        // the emitter dish
                        .texOffs(0, METAL_V)
                        .addBox(-6.0F, -11.0F, -6.0F, 12, 2, 12),
                PartPose.ZERO);

        // Four posts round the dish, so the light has something to come out of.
        for (int post = 0; post < 4; post++) {
            float x = (post & 1) == 0 ? 5.0F : -5.0F;
            float z = (post & 2) == 0 ? 5.0F : -5.0F;
            base.addOrReplaceChild("post" + post, CubeListBuilder.create()
                            .texOffs(0, METAL_V)
                            .addBox(-0.5F, -3.0F, -0.5F, 1, 3, 1),
                    PartPose.offset(x, -11.0F, z));
        }

        // The globe, pivoted where it hangs so the whole thing turns about its own middle.
        PartDefinition globe = root.addOrReplaceChild(GLOBE, CubeListBuilder.create(),
                PartPose.offset(0.0F, -19.0F, 0.0F));
        for (int band = 0; band < BANDS; band++) {
            // A circle's worth of latitude: widest in the middle, narrow at the poles.
            double t = (band + 0.5D) / BANDS;
            double angle = Math.PI * t;
            int width = (int) Math.round(Math.sin(angle) * 12.0D);
            if (width < 2) {
                width = 2;
            }
            float y = (float) (-Math.cos(angle) * 6.0D);
            globe.addOrReplaceChild("band" + band, CubeListBuilder.create()
                            .texOffs(0, GLOW_V)
                            .addBox(-width / 2.0F, y, -width / 2.0F, width, 1, width),
                    PartPose.ZERO);
        }
        // A meridian, so the spin is visible on an otherwise featureless ball.
        globe.addOrReplaceChild("meridian", CubeListBuilder.create()
                        .texOffs(0, GLOW_V)
                        .addBox(-0.5F, -6.0F, -6.0F, 1, 12, 12),
                PartPose.ZERO);

        return LayerDefinition.create(mesh, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }
}
