package com.branciho.citiesinlife.client.render;

import com.branciho.citiesinlife.CitiesInLife;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * The turbine's geometry: two housings, a skid between them, and an eight-bladed rotor in the gap.
 *
 * <p>Built as a model layer rather than a block model for two reasons. It is three blocks wide, which
 * a block model cannot be, and the rotor has to turn, which a block model cannot do either.
 *
 * <p>A warning about the coordinates. Model geometry is authored with <strong>Y pointing down</strong>
 * and the renderer flips it, so in every box below a more negative Y is <em>higher</em> in the world.
 * The origin sits at the middle of the bottom face of the block the turbine was placed in, and one
 * unit is a sixteenth of a block, so the machine runs from X -24 to 24: three blocks.
 */
public final class TurbineModel {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(CitiesInLife.id("turbine"), "main");

    public static final String ROTOR = "rotor";

    /** Blades around the shaft. Eight reads as a turbine; four reads as a ceiling fan. */
    private static final int BLADES = 8;

    private static final int TEXTURE_WIDTH = 256;
    private static final int TEXTURE_HEIGHT = 128;

    private TurbineModel() {
    }

    public static LayerDefinition create() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // The plate the whole machine stands on, spanning all three blocks.
        root.addOrReplaceChild("skid", CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(-24.0F, -3.0F, -10.0F, 48, 3, 20),
                PartPose.ZERO);

        // The two ends: gearbox on one side, generator on the other. The rotor turns in the gap.
        root.addOrReplaceChild("housing_left", CubeListBuilder.create()
                        .texOffs(0, 26)
                        .addBox(-24.0F, -22.0F, -9.0F, 16, 19, 18),
                PartPose.ZERO);
        root.addOrReplaceChild("housing_right", CubeListBuilder.create()
                        .texOffs(70, 26)
                        .addBox(8.0F, -22.0F, -9.0F, 16, 19, 18),
                PartPose.ZERO);

        // The exhaust, so smoke has somewhere obvious to come out of.
        root.addOrReplaceChild("stack", CubeListBuilder.create()
                        .texOffs(140, 26)
                        .addBox(12.0F, -34.0F, -4.0F, 8, 12, 8),
                PartPose.ZERO);

        // The steam inlet, poking down through the hole in the boiler room's ceiling.
        root.addOrReplaceChild("inlet", CubeListBuilder.create()
                        .texOffs(174, 26)
                        .addBox(-4.0F, 0.0F, -4.0F, 8, 4, 8),
                PartPose.ZERO);

        // Everything below this point turns. The pivot is the middle of the gap between the housings.
        PartDefinition rotor = root.addOrReplaceChild(ROTOR, CubeListBuilder.create()
                        .texOffs(0, 66)
                        .addBox(-14.0F, -2.0F, -2.0F, 28, 4, 4)
                        .texOffs(66, 66)
                        .addBox(-5.0F, -5.0F, -5.0F, 10, 10, 10),
                PartPose.offset(0.0F, -12.0F, 0.0F));

        for (int blade = 0; blade < BLADES; blade++) {
            rotor.addOrReplaceChild("blade" + blade, CubeListBuilder.create()
                            .texOffs(108, 66)
                            .addBox(-1.0F, -3.0F, 3.0F, 2, 6, 8),
                    PartPose.rotation(blade * (float) (Math.PI * 2.0D / BLADES), 0.0F, 0.0F));
        }

        return LayerDefinition.create(mesh, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }
}
