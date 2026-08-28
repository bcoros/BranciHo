package com.branciho.citiesinlife.client.render;

import com.branciho.citiesinlife.CitiesInLife;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * An ambulance: a short bonnet, a low cab, and a tall square box on the back.
 *
 * <p>The box is the whole silhouette and it is deliberately <em>wider</em> than the cab in front of
 * it, which is the shape every box-body ambulance in the world has and the thing that separates one
 * from a van at a glance. Take the box off and this is a car; that is why it could not stay one.
 *
 * <p>Same coordinate warning as {@link CarModel}: authored with <strong>Y pointing down</strong>,
 * flipped by the renderer, front facing negative Z, wheels centred on their own pivots.
 */
public final class AmbulanceModel {

    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(CitiesInLife.id("ambulance"), "main");

    public static final String[] WHEELS = {
            "wheel_front_left", "wheel_front_right",
            "wheel_rear_left", "wheel_rear_right"};

    private static final int TEXTURE_WIDTH = 256;
    private static final int TEXTURE_HEIGHT = 128;

    private AmbulanceModel() {
    }

    public static LayerDefinition create() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        root.addOrReplaceChild("chassis", CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(-8.0F, -10.0F, -18.0F, 16, 5, 36),
                PartPose.ZERO);

        // The cab. Deliberately shorter than the box behind it - the step up over the bulkhead is
        // most of what reads as an ambulance rather than a van.
        root.addOrReplaceChild("cab", CubeListBuilder.create()
                        .texOffs(0, 42)
                        .addBox(-8.0F, -21.0F, -18.0F, 16, 11, 12),
                PartPose.ZERO);

        // The body. Wider than the cab and a good deal taller, which is the whole silhouette.
        root.addOrReplaceChild("box", CubeListBuilder.create()
                        .texOffs(58, 42)
                        .addBox(-9.0F, -23.0F, -6.0F, 18, 13, 24),
                PartPose.ZERO);

        wheel(root, WHEELS[0], -8.5F, -11.0F);
        wheel(root, WHEELS[1], 8.5F, -11.0F);
        wheel(root, WHEELS[2], -8.5F, 11.0F);
        wheel(root, WHEELS[3], 8.5F, 11.0F);

        // On the cab roof at -21, ahead of the box rather than on top of it.
        root.addOrReplaceChild(CarModel.LIGHT_LEFT, CubeListBuilder.create()
                        .texOffs(106, 18)
                        .addBox(-6.0F, -23.0F, -14.0F, 6, 2, 5),
                PartPose.ZERO);
        root.addOrReplaceChild(CarModel.LIGHT_RIGHT, CubeListBuilder.create()
                        .texOffs(106, 26)
                        .addBox(0.0F, -23.0F, -14.0F, 6, 2, 5),
                PartPose.ZERO);

        return LayerDefinition.create(mesh, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    private static void wheel(PartDefinition root, String name, float x, float z) {
        root.addOrReplaceChild(name, CubeListBuilder.create()
                        .texOffs(106, 0)
                        .addBox(-2.0F, -4.0F, -4.0F, 4, 8, 8),
                PartPose.offset(x, -4.0F, z));
    }
}
