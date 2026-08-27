package com.branciho.citiesinlife.client.render;

import com.branciho.citiesinlife.CitiesInLife;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * A small saloon car: a body, a cabin sitting on it, and four wheels that turn.
 *
 * <p>The same coordinate warning as {@link TurbineModel}. Geometry here is authored with
 * <strong>Y pointing down</strong> and the renderer flips it, so a more negative Y is
 * <em>higher</em> in the world. One unit is a sixteenth of a block. The front of the car faces
 * negative Z, which is what makes the renderer's yaw come out pointing the way it is driving.
 *
 * <p>Each wheel's box is centred on its own pivot. Offsetting the box instead makes the wheel orbit
 * the axle rather than spin on it, which looks like a shopping trolley.
 */
public final class CarModel {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(CitiesInLife.id("car"), "main");

    public static final String WHEEL_FRONT_LEFT = "wheel_front_left";
    public static final String WHEEL_FRONT_RIGHT = "wheel_front_right";
    public static final String WHEEL_REAR_LEFT = "wheel_rear_left";
    public static final String WHEEL_REAR_RIGHT = "wheel_rear_right";

    private static final int TEXTURE_WIDTH = 128;
    private static final int TEXTURE_HEIGHT = 64;

    private CarModel() {
    }

    public static LayerDefinition create() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // The body: two blocks long, one wide, sitting just clear of the ground.
        root.addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(-7.0F, -10.0F, -16.0F, 14, 6, 32),
                PartPose.ZERO);

        // The cabin, set back from the nose so the car has a bonnet.
        root.addOrReplaceChild("cabin", CubeListBuilder.create()
                        .texOffs(0, 38)
                        .addBox(-6.0F, -16.0F, -6.0F, 12, 6, 16),
                PartPose.ZERO);

        wheel(root, WHEEL_FRONT_LEFT, -7.5F, -10.0F);
        wheel(root, WHEEL_FRONT_RIGHT, 7.5F, -10.0F);
        wheel(root, WHEEL_REAR_LEFT, -7.5F, 10.0F);
        wheel(root, WHEEL_REAR_RIGHT, 7.5F, 10.0F);

        return LayerDefinition.create(mesh, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    private static void wheel(PartDefinition root, String name, float x, float z) {
        root.addOrReplaceChild(name, CubeListBuilder.create()
                        .texOffs(60, 38)
                        .addBox(-1.5F, -3.0F, -3.0F, 3, 6, 6),
                PartPose.offset(x, -3.0F, z));
    }
}
