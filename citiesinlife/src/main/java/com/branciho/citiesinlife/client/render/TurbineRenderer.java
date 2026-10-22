package com.branciho.citiesinlife.client.render;

import com.branciho.citiesinlife.CitiesInLife;
import com.branciho.citiesinlife.block.NuclearTurbineBlock;
import com.branciho.citiesinlife.block.TurbineBlock;
import com.branciho.citiesinlife.blockentity.TurbineBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Draws the turbine and turns its rotor.
 *
 * <p>The block itself renders a small steel pad; everything with any size to it is drawn from here,
 * because a machine three blocks wide with a moving part is beyond what a block model can express.
 */
public class TurbineRenderer implements BlockEntityRenderer<TurbineBlockEntity> {

    private static final ResourceLocation TEXTURE = CitiesInLife.id("textures/block/turbine.png");
    private static final ResourceLocation HALL_TEXTURE =
            CitiesInLife.id("textures/block/nuclear_turbine_hall.png");

    private final ModelPart root;
    private final ModelPart rotor;

    /** The nuclear machine is a different shape entirely, so it is a different model. */
    private final ModelPart hall;
    private final ModelPart[] hallRotors;

    public TurbineRenderer(BlockEntityRendererProvider.Context context) {
        this.root = context.bakeLayer(TurbineModel.LAYER);
        this.rotor = root.getChild(TurbineModel.ROTOR);
        this.hall = context.bakeLayer(NuclearTurbineModel.LAYER);
        this.hallRotors = new ModelPart[NuclearTurbineModel.ROTORS.length];
        for (int i = 0; i < hallRotors.length; i++) {
            hallRotors[i] = hall.getChild(NuclearTurbineModel.ROTORS[i]);
        }
    }

    @Override
    public void render(TurbineBlockEntity turbine, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        BlockState state = turbine.getBlockState();
        float yaw = state.hasProperty(TurbineBlock.FACING)
                ? state.getValue(TurbineBlock.FACING).toYRot()
                : 0.0F;

        poseStack.pushPose();
        // Middle of the block's bottom face, turned to match the way it was placed.
        poseStack.translate(0.5D, 0.0D, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(-yaw));
        // Model geometry is authored with Y downwards; this puts it the right way up.
        poseStack.scale(1.0F, -1.0F, -1.0F);

        boolean nuclear = state.getBlock() instanceof NuclearTurbineBlock;
        float angle = (float) Math.toRadians(turbine.spin(partialTick));

        if (nuclear) {
            // All three discs on one shaft, so they turn together - a machine whose rotors drifted
            // out of step would look broken rather than powerful.
            for (ModelPart disc : hallRotors) {
                disc.xRot = angle;
            }
            hall.render(poseStack,
                    buffer.getBuffer(RenderType.entityCutoutNoCull(HALL_TEXTURE)),
                    packedLight, packedOverlay);
        } else {
            rotor.xRot = angle;
            root.render(poseStack,
                    buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)),
                    packedLight, packedOverlay);
        }
        poseStack.popPose();
    }

    /**
     * Keep drawing it from further away than a normal block entity.
     *
     * <p>It is the size of a small building and it is the thing a player walks over to look at, so
     * having it pop out of existence at sixteen blocks would be absurd.
     */
    @Override
    public int getViewDistance() {
        return 96;
    }

    /**
     * Draw it even when its own block position is off screen.
     *
     * <p>The machine is two blocks taller and one block wider than the position it is anchored to, so
     * culling on that single block makes the whole turbine blink out whenever you stand close enough
     * to look at it — which is exactly when you want to see it.
     */
    @Override
    public boolean shouldRenderOffScreen(TurbineBlockEntity turbine) {
        return true;
    }

    /**
     * Eleven blocks of machine anchored to one of them.
     *
     * <p>Culling on the block alone would blink the whole hall out whenever the anchor left the
     * screen, which standing beside a machine this long is most of the time.
     */
    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(TurbineBlockEntity turbine) {
        return new net.minecraft.world.phys.AABB(turbine.getBlockPos()).inflate(8.0D);
    }
}
