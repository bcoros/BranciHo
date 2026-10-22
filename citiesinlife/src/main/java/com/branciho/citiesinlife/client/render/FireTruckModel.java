package com.branciho.citiesinlife.client.render;

import com.branciho.citiesinlife.CitiesInLife;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * A fire appliance: a long flat deck, a tall crew cab at the front, a body full of gear behind it,
 * and a ladder down the roof.
 *
 * <p>Its own mesh rather than the saloon's, because a fire truck that is a saloon in a different
 * colour is a red car. The silhouette is the whole of what makes one recognisable from across a
 * city — long, square-shouldered and taller than everything else on the road — and none of that
 * survives being painted onto a two-block hatchback.
 *
 * <p>Same coordinate warning as {@link CarModel}. Geometry is authored with <strong>Y pointing
 * down</strong> and the renderer flips it, so a more negative Y is <em>higher</em> in the world.
 * One unit is a sixteenth of a block, the front faces negative Z, and every wheel's box is centred
 * on its own pivot so it spins on the axle rather than orbiting it.
 *
 * <p>Bigger sheet than the saloon uses. A forty-four-unit deck alone wants a hundred and twenty
 * pixels of UV width, which is most of a 128-wide texture before anything else is drawn.
 */
public final class FireTruckModel {

    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(CitiesInLife.id("fire_truck"), "main");

    /** Named so {@link CarRenderer} can find them on any vehicle without knowing which it has. */
    public static final String[] WHEELS = {
            "wheel_front_left", "wheel_front_right",
            "wheel_mid_left", "wheel_mid_right",
            "wheel_rear_left", "wheel_rear_right"};

    private static final int TEXTURE_WIDTH = 256;
    private static final int TEXTURE_HEIGHT = 128;

    /** Six wheels, because an appliance this long on four would sag in the middle. */
    private static final float[] WHEEL_Z = {-14.0F, 4.0F, 16.0F};

    private FireTruckModel() {
    }

    public static LayerDefinition create() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // The deck: nearly three metres of it, sitting just clear of the ground.
        root.addOrReplaceChild("chassis", CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(-8.0F, -10.0F, -22.0F, 16, 5, 44),
                PartPose.ZERO);

        // The crew cab, at the front and a full block taller than a car.
        root.addOrReplaceChild("cab", CubeListBuilder.create()
                        .texOffs(0, 50)
                        .addBox(-8.0F, -22.0F, -22.0F, 16, 12, 14),
                PartPose.ZERO);

        // Everything behind it: pump, hose, lockers. One box, because at this resolution the
        // lockers would be three pixels each and read as noise.
        root.addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(62, 50)
                        .addBox(-8.0F, -21.0F, -8.0F, 16, 11, 30),
                PartPose.ZERO);

        // The ladder, stowed flat down the roof and overhanging the back. It is the one detail
        // that says fire truck rather than lorry, so it is worth its own box.
        root.addOrReplaceChild("ladder", CubeListBuilder.create()
                        .texOffs(122, 0)
                        .addBox(-3.0F, -24.0F, -6.0F, 6, 3, 34),
                PartPose.ZERO);

        for (int pair = 0; pair < WHEEL_Z.length; pair++) {
            wheel(root, WHEELS[pair * 2], -8.5F, WHEEL_Z[pair]);
            wheel(root, WHEELS[pair * 2 + 1], 8.5F, WHEEL_Z[pair]);
        }

        // On the cab roof, which is at -22 rather than the saloon's -16.
        root.addOrReplaceChild(CarModel.LIGHT_LEFT, CubeListBuilder.create()
                        .texOffs(156, 70)
                        .addBox(-6.0F, -24.0F, -18.0F, 6, 2, 5),
                PartPose.ZERO);
        root.addOrReplaceChild(CarModel.LIGHT_RIGHT, CubeListBuilder.create()
                        .texOffs(156, 80)
                        .addBox(0.0F, -24.0F, -18.0F, 6, 2, 5),
                PartPose.ZERO);

        return LayerDefinition.create(mesh, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    private static void wheel(PartDefinition root, String name, float x, float z) {
        root.addOrReplaceChild(name, CubeListBuilder.create()
                        .texOffs(156, 50)
                        .addBox(-2.0F, -4.0F, -4.0F, 4, 8, 8),
                PartPose.offset(x, -4.0F, z));
    }
}
