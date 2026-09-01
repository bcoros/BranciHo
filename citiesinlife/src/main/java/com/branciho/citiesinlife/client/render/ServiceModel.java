package com.branciho.citiesinlife.client.render;

import com.branciho.citiesinlife.CitiesInLife;
import com.branciho.citiesinlife.entity.ServiceEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;

/**
 * A service worker's body.
 *
 * <p>The same vanilla humanoid mesh as a citizen, because a police officer is a person in a police
 * uniform and not a different species. The only thing added is that they hold what they are given:
 * a soldier handed a weapon should be visibly carrying it, which is the entire point of handing them
 * one.
 */
public class ServiceModel extends HumanoidModel<ServiceEntity> {

    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(CitiesInLife.id("service"), "main");

    public ServiceModel(ModelPart root) {
        super(root);
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(ServiceEntity entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        // Set before the super call: the humanoid animation reads these poses rather than the item.
        rightArmPose = entity.getMainHandItem().isEmpty() ? ArmPose.EMPTY : ArmPose.ITEM;
        leftArmPose = entity.getOffhandItem().isEmpty() ? ArmPose.EMPTY : ArmPose.ITEM;
        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
    }
}
