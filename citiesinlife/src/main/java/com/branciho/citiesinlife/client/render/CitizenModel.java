package com.branciho.citiesinlife.client.render;

import com.branciho.citiesinlife.CitiesInLife;
import com.branciho.citiesinlife.entity.CitizenEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;

/**
 * A citizen's body.
 *
 * <p>Plainly a person, using the same proportions as everything else in the game, because a mod
 * about cities full of people is not the place to invent a new shape of person. The mesh is vanilla's
 * humanoid one; all that is added here is what somebody looks like when they are sat at a desk
 * working.
 */
public class CitizenModel extends HumanoidModel<CitizenEntity> {

    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(CitiesInLife.id("citizen"), "main");

    /** How far the legs swing forward when seated. Just short of horizontal, as a chair puts them. */
    private static final float SEATED_LEG = -1.42F;

    /** How far the knees splay, so two people at adjacent desks are not identical mannequins. */
    private static final float SEATED_KNEE_SPLAY = 0.18F;

    /** Where the hands sit when typing, and how far they bob. */
    private static final float TYPING_ARM = -1.15F;
    private static final float TYPING_BOB = 0.12F;

    public CitizenModel(ModelPart root) {
        super(root);
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(CitizenEntity entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);

        if (entity.activity() == CitizenEntity.ACTIVITY_TYPING) {
            sitAndType(ageInTicks);
        } else if (entity.activity() == CitizenEntity.ACTIVITY_SERVING) {
            standAtTheTill(ageInTicks);
        }
    }

    /**
     * Sat at a desk with both hands on a keyboard.
     *
     * <p>The two hands bob out of phase, which is the whole trick: in phase they read as somebody
     * doing press-ups, and out of phase they read as typing from across the room.
     */
    private void sitAndType(float ageInTicks) {
        rightLeg.xRot = SEATED_LEG;
        rightLeg.yRot = SEATED_KNEE_SPLAY;
        leftLeg.xRot = SEATED_LEG;
        leftLeg.yRot = -SEATED_KNEE_SPLAY;

        float bob = (float) Math.sin(ageInTicks * 0.55D) * TYPING_BOB;
        rightArm.xRot = TYPING_ARM + bob;
        leftArm.xRot = TYPING_ARM - bob;
        rightArm.yRot = -0.22F;
        leftArm.yRot = 0.22F;
        rightArm.zRot = 0.0F;
        leftArm.zRot = 0.0F;
    }

    /** Stood behind a counter: arms down, weight shifting, the universal look of a long shift. */
    private void standAtTheTill(float ageInTicks) {
        float sway = (float) Math.sin(ageInTicks * 0.06D) * 0.08F;
        rightArm.xRot = -0.18F + sway;
        leftArm.xRot = -0.18F - sway;
        rightArm.zRot = 0.06F;
        leftArm.zRot = -0.06F;
    }
}
