package com.branciho.citiesinlife.client.render;

import com.branciho.citiesinlife.CitiesInLife;
import com.branciho.citiesinlife.block.MissileBlock;
import com.branciho.citiesinlife.blockentity.MissileBlockEntity;
import com.branciho.citiesinlife.missile.MissileKind;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Draws the rocket standing on the pad.
 *
 * <p>Ten blocks of it, which is why none of it can come from a block model: a baked model may not
 * reach beyond thirty-two units above its own position and this one is a hundred and sixty-two.
 * The blockstate and block model still exist — they carry the particle texture and nothing else —
 * exactly as the nuclear turbine hall does.
 */
public class MissileRenderer implements BlockEntityRenderer<MissileBlockEntity> {

    /** One texture per kind, resolved once. Three files, one mesh. */
    private static final ResourceLocation[] TEXTURES = textures();

    private static ResourceLocation[] textures() {
        MissileKind[] kinds = MissileKind.values();
        ResourceLocation[] paths = new ResourceLocation[kinds.length];
        for (int i = 0; i < kinds.length; i++) {
            paths[i] = CitiesInLife.id("textures/entity/missile/" + kinds[i].id() + ".png");
        }
        return paths;
    }

    private final ModelPart root;

    public MissileRenderer(BlockEntityRendererProvider.Context context) {
        this.root = context.bakeLayer(MissileModel.LAYER);
    }

    @Override
    public void render(MissileBlockEntity missile, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        BlockState state = missile.getBlockState();
        MissileKind kind = MissileBlock.kindAt(state);
        if (kind == null) {
            return;
        }
        float yaw = state.hasProperty(MissileBlock.FACING)
                ? state.getValue(MissileBlock.FACING).toYRot()
                : 0.0F;

        poseStack.pushPose();
        // Middle of the block's bottom face, turned to match the way it was placed.
        poseStack.translate(0.5D, 0.0D, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(-yaw));
        // Model geometry is authored with Y downwards; this puts it the right way up.
        poseStack.scale(kind.scale(), -kind.scale(), -kind.scale());

        root.render(poseStack,
                buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURES[kind.ordinal()])),
                packedLight, packedOverlay);
        poseStack.popPose();
    }

    /**
     * Ten blocks of rocket anchored to one of them.
     *
     * <p>Without this the whole thing pops out of view the moment its one-block foot leaves the
     * frustum, which for something this tall means it vanishes while you are looking straight at
     * the middle of it.
     */
    @Override
    public AABB getRenderBoundingBox(MissileBlockEntity missile) {
        return new AABB(missile.getBlockPos()).inflate(12.0D);
    }
}
