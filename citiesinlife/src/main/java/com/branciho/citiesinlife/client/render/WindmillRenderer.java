package com.branciho.citiesinlife.client.render;

import com.branciho.citiesinlife.CitiesInLife;
import com.branciho.citiesinlife.block.WindmillBlock;
import com.branciho.citiesinlife.block.WindmillColour;
import com.branciho.citiesinlife.blockentity.WindmillBlockEntity;
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

import java.util.EnumMap;
import java.util.Map;

/** Draws a wind turbine and turns its rotor. */
public class WindmillRenderer implements BlockEntityRenderer<WindmillBlockEntity> {

    /**
     * One texture per livery rather than a tint.
     *
     * <p>Four recoloured sheets cost nothing and mean the blades can have their own shading and the
     * warning stripe near the tip, which a flat multiply over one texture would flatten out.
     */
    private static final Map<WindmillColour, ResourceLocation> TEXTURES = new EnumMap<>(WindmillColour.class);

    static {
        for (WindmillColour colour : WindmillColour.values()) {
            TEXTURES.put(colour, CitiesInLife.id("textures/block/" + colour.blockName() + ".png"));
        }
    }

    private final ModelPart root;
    private final ModelPart rotor;

    public WindmillRenderer(BlockEntityRendererProvider.Context context) {
        this.root = context.bakeLayer(WindmillModel.LAYER);
        this.rotor = root.getChild(WindmillModel.ROTOR);
    }

    @Override
    public void render(WindmillBlockEntity mill, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        BlockState state = mill.getBlockState();
        if (!(state.getBlock() instanceof WindmillBlock block)) {
            return;
        }
        float yaw = state.hasProperty(WindmillBlock.FACING)
                ? state.getValue(WindmillBlock.FACING).toYRot()
                : 0.0F;

        poseStack.pushPose();
        poseStack.translate(0.5D, 0.0D, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(-yaw));
        // Model geometry is authored with Y downwards; this puts it the right way up.
        poseStack.scale(1.0F, -1.0F, -1.0F);

        rotor.zRot = (float) Math.toRadians(mill.spin(partialTick));

        VertexConsumer consumer = buffer.getBuffer(
                RenderType.entityCutoutNoCull(TEXTURES.get(block.colour())));
        root.render(poseStack, consumer, packedLight, packedOverlay);
        poseStack.popPose();
    }

    /**
     * A wind farm is a landmark, so draw it from a long way off.
     *
     * <p>Well past the usual block entity range: the whole point of putting one on a ridge is seeing
     * it from the city it powers.
     */
    @Override
    public int getViewDistance() {
        return 256;
    }

    /** Never cull it against its own single block - the rotor is fifteen blocks across. */
    @Override
    public boolean shouldRenderOffScreen(WindmillBlockEntity mill) {
        return true;
    }
}
