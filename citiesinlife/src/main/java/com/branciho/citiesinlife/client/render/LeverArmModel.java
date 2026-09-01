package com.branciho.citiesinlife.client.render;

import com.branciho.citiesinlife.CitiesInLife;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * The moving half of a reactor lever: two steel arms and the grip bar between them.
 *
 * <p>Built from the photograph. The arms are not straight — each has a dog-leg partway along, which
 * is the detail that makes the thing read as forged steel rather than as a stick, and the grip is a
 * segmented brass bar with a cap at either end.
 *
 * <p>Same coordinate warning as the turbine and the car: geometry here is authored with
 * <strong>Y pointing down</strong>, so a more negative Y is <em>higher</em>. One unit is a
 * sixteenth of a block. Everything hangs off {@code pivot}, which sits at the two outer bosses on
 * the housing, because that is what the real thing rotates about.
 */
public final class LeverArmModel {

    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(CitiesInLife.id("reactor_lever"), "main");

    public static final String PIVOT = "pivot";

    /**
     * The lit indicator, drawn over the recess.
     *
     * <p>A root child rather than a child of the pivot, because the lamp does not move when the
     * arm swings — only which of the two is glowing changes, and that is a texture swap.
     */
    public static final String LAMP = "lamp";

    private static final int TEXTURE_WIDTH = 32;
    private static final int TEXTURE_HEIGHT = 32;

    private LeverArmModel() {
    }

    public static LayerDefinition create() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // The whole assembly turns about the bosses, which sit level with the middle of the plate.
        PartDefinition pivot = root.addOrReplaceChild(PIVOT,
                CubeListBuilder.create(), PartPose.ZERO);

        // Two arms, one either side, each in two segments with a bend between them.
        for (int side = 0; side < 2; side++) {
            float x = side == 0 ? -4.6F : 3.0F;
            pivot.addOrReplaceChild("boss_" + side, CubeListBuilder.create()
                            .texOffs(0, 0)
                            .addBox(x, -0.8F, -0.8F, 2, 2, 2),
                    PartPose.ZERO);
            pivot.addOrReplaceChild("arm_lower_" + side, CubeListBuilder.create()
                            .texOffs(0, 5)
                            .addBox(x + 0.2F, -4.4F, -0.7F, 1, 4, 2),
                    PartPose.ZERO);
            // The dog-leg: the upper segment steps outward, which is the shape in the photo.
            float step = side == 0 ? -1.3F : 1.3F;
            pivot.addOrReplaceChild("arm_upper_" + side, CubeListBuilder.create()
                            .texOffs(6, 5)
                            .addBox(x + 0.2F + step, -8.2F, -0.7F, 1, 4, 2),
                    PartPose.ZERO);
        }

        // The grip: a segmented brass bar spanning the two arms, with a steel cap at each end.
        pivot.addOrReplaceChild("grip", CubeListBuilder.create()
                        .texOffs(0, 12)
                        .addBox(-5.4F, -9.6F, -1.1F, 11, 2, 2),
                PartPose.ZERO);
        pivot.addOrReplaceChild("cap_left", CubeListBuilder.create()
                        .texOffs(0, 17)
                        .addBox(-6.8F, -9.8F, -1.3F, 2, 3, 3),
                PartPose.ZERO);
        pivot.addOrReplaceChild("cap_right", CubeListBuilder.create()
                        .texOffs(0, 17)
                        .addBox(5.0F, -9.8F, -1.3F, 2, 3, 3),
                PartPose.ZERO);

        // Centred on its own origin, so the renderer places it with one translate rather than
        // with a translate plus an offset baked in here. Two places deciding where the lamp goes
        // is how it ended up floating beside the housing instead of inside it.
        root.addOrReplaceChild(LAMP, CubeListBuilder.create()
                        .texOffs(16, 0)
                        .addBox(-2.0F, -1.5F, -0.5F, 4, 3, 1),
                PartPose.ZERO);

        return LayerDefinition.create(mesh, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }
}
