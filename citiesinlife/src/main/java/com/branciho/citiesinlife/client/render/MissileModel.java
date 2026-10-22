package com.branciho.citiesinlife.client.render;

import com.branciho.citiesinlife.CitiesInLife;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * A rocket: ten blocks of it, standing on one.
 *
 * <p>Authored once and used by all three missiles. A ballistic round, a nuclear one and an
 * interceptor are the same shape — what tells them apart is the paint and, for the interceptor,
 * the renderer drawing it about half size. Three meshes differing by nothing would have been three
 * sets of UVs to keep in step for no gain, which is the mistake the service vehicles started out
 * making.
 *
 * <p>The same coordinate warning as every other model here. Geometry is authored with <strong>Y
 * pointing down</strong> and the renderer flips it, so a more negative Y is <em>higher</em> in the
 * world; one unit is a sixteenth of a block. Everything sits at or above authored zero so the
 * rocket stands on the block it is placed on rather than sinking into it.
 *
 * <p>Nothing tapers, because a box cannot. The nose is three boxes of decreasing width stacked on
 * one another, which at sixteen pixels to the block reads as a cone perfectly well and is how
 * every rounded thing in this game is built.
 */
public final class MissileModel {

    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(CitiesInLife.id("missile"), "main");

    /** The fuselage, so the flight entity can lean it into its own trajectory. */
    public static final String BODY = "body";

    private static final int TEXTURE_WIDTH = 256;
    private static final int TEXTURE_HEIGHT = 256;

    /**
     * How tall the finished rocket is, in model units.
     *
     * <p>A hundred and sixty-two, which is a shade over ten blocks. Published because the renderer
     * needs it to stand the thing up and the flight entity needs it to know where its own nose is.
     */
    public static final float HEIGHT = 162.0F;

    private MissileModel() {
    }

    public static LayerDefinition create() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // Everything hangs off one part so the entity renderer can pitch the whole rocket over as
        // it arcs without having to rotate six boxes in step.
        PartDefinition body = root.addOrReplaceChild(BODY, CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(-14.0F, -128.0F, -14.0F, 28, 128, 28),
                PartPose.ZERO);

        // The nose, in three steps.
        body.addOrReplaceChild("nose_lower", CubeListBuilder.create()
                        .texOffs(114, 0)
                        .addBox(-11.0F, -144.0F, -11.0F, 22, 16, 22),
                PartPose.ZERO);
        body.addOrReplaceChild("nose_upper", CubeListBuilder.create()
                        .texOffs(114, 40)
                        .addBox(-7.0F, -156.0F, -7.0F, 14, 12, 14),
                PartPose.ZERO);
        body.addOrReplaceChild("tip", CubeListBuilder.create()
                        .texOffs(172, 40)
                        .addBox(-3.0F, -162.0F, -3.0F, 6, 6, 6),
                PartPose.ZERO);

        // The bell at the bottom, flared wider than the body.
        body.addOrReplaceChild("engine", CubeListBuilder.create()
                        .texOffs(114, 68)
                        .addBox(-10.0F, -10.0F, -10.0F, 20, 10, 20),
                PartPose.ZERO);

        // Two crossed plates rather than four separate fins. They meet inside the fuselage where
        // nobody can see the join, and it is half the boxes and half the UVs for the same rocket.
        body.addOrReplaceChild("fin_x", CubeListBuilder.create()
                        .texOffs(114, 100)
                        .addBox(-20.0F, -44.0F, -2.0F, 40, 44, 4),
                PartPose.ZERO);
        body.addOrReplaceChild("fin_z", CubeListBuilder.create()
                        .texOffs(114, 150)
                        .addBox(-2.0F, -44.0F, -20.0F, 4, 44, 40),
                PartPose.ZERO);

        return LayerDefinition.create(mesh, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }
}
