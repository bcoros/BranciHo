package com.branciho.citiesinlife.client.render;

import com.branciho.citiesinlife.CitiesInLife;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * A wind turbine's nacelle and its three blades.
 *
 * <p>The blades reach seven blocks from the hub, so the rotor is fifteen across - the size the thing
 * actually is, rather than the size a block model could manage. Each blade is three tapering
 * segments, because a slab of constant width reads as a plank and this is supposed to read as a
 * wind turbine.
 *
 * <p>Same coordinate warning as the steam turbine: geometry is authored with <strong>Y pointing
 * down</strong> and the renderer flips it, so a more negative Y is higher in the world. The origin is
 * the middle of the bottom face of the block the nacelle sits in.
 */
public final class WindmillModel {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(CitiesInLife.id("windmill"), "main");

    public static final String ROTOR = "rotor";

    /** Three, like every large turbine built in the last forty years. */
    private static final int BLADES = 3;

    private static final int TEXTURE_WIDTH = 256;
    private static final int TEXTURE_HEIGHT = 128;

    private WindmillModel() {
    }

    public static LayerDefinition create() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // The collar where the nacelle meets whatever tower the player built.
        root.addOrReplaceChild("collar", CubeListBuilder.create()
                        .texOffs(64, 0)
                        .addBox(-6.0F, -6.0F, -6.0F, 12, 6, 12),
                PartPose.ZERO);

        // The nacelle: the housing the rotor is mounted on the front of.
        root.addOrReplaceChild("nacelle", CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(-5.0F, -16.0F, -10.0F, 10, 10, 20),
                PartPose.ZERO);

        // The rotor turns about the Z axis, which is the direction the nacelle points.
        PartDefinition rotor = root.addOrReplaceChild(ROTOR, CubeListBuilder.create()
                        .texOffs(116, 0)
                        .addBox(-4.0F, -4.0F, -4.0F, 8, 8, 8),
                PartPose.offset(0.0F, -11.0F, -12.0F));

        for (int blade = 0; blade < BLADES; blade++) {
            float angle = blade * (float) (Math.PI * 2.0D / BLADES);
            PartDefinition arm = rotor.addOrReplaceChild("blade" + blade, CubeListBuilder.create()
                            // root - wide and thick where the load is
                            .texOffs(0, 24)
                            .addBox(-5.0F, -40.0F, -2.0F, 10, 40, 4)
                            // middle
                            .texOffs(0, 40)
                            .addBox(-4.0F, -76.0F, -1.5F, 8, 36, 3)
                            // tip - narrow and thin
                            .texOffs(0, 54)
                            .addBox(-2.5F, -112.0F, -1.0F, 5, 36, 2),
                    PartPose.rotation(0.0F, 0.0F, angle));
        }

        return LayerDefinition.create(mesh, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }
}
