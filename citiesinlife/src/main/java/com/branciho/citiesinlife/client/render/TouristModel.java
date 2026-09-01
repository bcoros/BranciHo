package com.branciho.citiesinlife.client.render;

import com.branciho.citiesinlife.CitiesInLife;
import com.branciho.citiesinlife.entity.TouristEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;

/**
 * A visitor's body: the plain vanilla humanoid mesh.
 *
 * <p>A separate layer from the citizen's only because the model is typed to its entity. Nothing
 * about a tourist's shape differs from anybody else's, which is rather the point - they are ordinary
 * people who happen to be from somewhere else.
 */
public class TouristModel extends HumanoidModel<TouristEntity> {

    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(CitiesInLife.id("tourist"), "main");

    public TouristModel(ModelPart root) {
        super(root);
    }

    public static LayerDefinition createBodyLayer() {
        return LayerDefinition.create(HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F), 64, 64);
    }
}
