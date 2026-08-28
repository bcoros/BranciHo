package com.branciho.citiesinlife.client.render;

import com.branciho.citiesinlife.CitiesInLife;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * A turbine hall: eleven blocks of machine, three of them turning.
 *
 * <p>The old nuclear turbine was the coal turbine's model with a different block texture, which
 * made the most powerful thing in the mod look like the cheapest. This is the real shape - a
 * concrete plinth running the length of it, three yellow clamshell casings sitting on it, a shaft
 * down the whole thing, bladed rotor discs turning in the gaps between the casings, the generator
 * in red at one end and the steam pipework in grey at the other.
 *
 * <p>A warning about the coordinates, the same one the coal turbine carries. Model geometry is
 * authored with <strong>Y pointing down</strong> and the renderer flips it, so in every box below a
 * more negative Y is <em>higher</em> in the world. The origin is the middle of the bottom face of
 * the block it was placed in, and one unit is a sixteenth of a block: the machine runs from X -88
 * to 88, which is eleven blocks.
 */
public final class NuclearTurbineModel {

    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(CitiesInLife.id("nuclear_turbine"), "main");

    /** The three discs, named so the renderer can turn each of them. */
    public static final String[] ROTORS = {"rotor_a", "rotor_b", "rotor_c"};

    /** Where each disc sits along the machine, in model units. */
    private static final float[] ROTOR_X = {-41.0F, 0.0F, 41.0F};

    /** Twelve blades reads as a steam turbine. Eight reads as a fan, which is the coal one. */
    private static final int BLADES = 12;

    private static final int TEXTURE_WIDTH = 256;
    private static final int TEXTURE_HEIGHT = 256;

    private NuclearTurbineModel() {
    }

    public static LayerDefinition create() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // The plinth. Everything stands on it and it is what makes the machine read as installed
        // rather than as dropped on the grass.
        root.addOrReplaceChild("plinth", CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(-88.0F, -7.0F, -24.0F, 176, 7, 48),
                PartPose.ZERO);

        // Three casings, in the gaps between the rotor discs.
        casing(root, "casing_a", -62.0F);
        casing(root, "casing_b", -21.0F);
        casing(root, "casing_c", 20.0F);

        // The shaft, running the length above the casings.
        root.addOrReplaceChild("shaft", CubeListBuilder.create()
                        .texOffs(0, 120)
                        .addBox(-88.0F, -40.0F, -7.0F, 176, 14, 14),
                PartPose.ZERO);

        // The generator: the red block at the far end of every photograph of one of these.
        root.addOrReplaceChild("generator", CubeListBuilder.create()
                        .texOffs(0, 152)
                        .addBox(64.0F, -36.0F, -21.0F, 24, 29, 42),
                PartPose.ZERO);

        // The steam end: pipework, stepped down so it does not read as another casing.
        root.addOrReplaceChild("steam_end", CubeListBuilder.create()
                        .texOffs(120, 152)
                        .addBox(-88.0F, -30.0F, -16.0F, 22, 23, 32),
                PartPose.ZERO);
        root.addOrReplaceChild("steam_pipe", CubeListBuilder.create()
                        .texOffs(120, 210)
                        .addBox(-84.0F, -52.0F, -6.0F, 12, 22, 12),
                PartPose.ZERO);

        for (int i = 0; i < ROTORS.length; i++) {
            rotor(root, ROTORS[i], ROTOR_X[i]);
        }

        return LayerDefinition.create(mesh, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    /**
     * One clamshell casing.
     *
     * <p>Two boxes rather than one: a wide lower half and a narrower crown, which from any angle
     * reads as the rounded top the real ones have without costing a curved mesh.
     */
    private static void casing(PartDefinition root, String name, float x) {
        root.addOrReplaceChild(name, CubeListBuilder.create()
                        .texOffs(0, 60)
                        .addBox(x, -26.0F, -20.0F, 42, 19, 40)
                        .texOffs(0, 92)
                        .addBox(x + 3.0F, -34.0F, -15.0F, 36, 9, 30),
                PartPose.ZERO);
    }

    /**
     * One bladed disc, turning about the machine's long axis.
     *
     * <p>The hub is offset to where the shaft is so the blades sweep around it rather than through
     * the plinth, and the pivot has to be the hub - a disc rotated about anything else wobbles.
     */
    private static void rotor(PartDefinition root, String name, float x) {
        PartDefinition rotor = root.addOrReplaceChild(name, CubeListBuilder.create()
                        .texOffs(180, 120)
                        .addBox(-3.0F, -8.0F, -8.0F, 6, 16, 16),
                PartPose.offset(x, -33.0F, 0.0F));

        for (int blade = 0; blade < BLADES; blade++) {
            rotor.addOrReplaceChild(name + "_blade" + blade, CubeListBuilder.create()
                            .texOffs(180, 160)
                            .addBox(-2.0F, -2.0F, 7.0F, 4, 4, 15),
                    PartPose.rotation(blade * (float) (Math.PI * 2.0D / BLADES), 0.0F, 0.0F));
        }
    }
}
