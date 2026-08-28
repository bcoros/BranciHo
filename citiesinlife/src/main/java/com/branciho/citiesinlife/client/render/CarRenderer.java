package com.branciho.citiesinlife.client.render;

import com.branciho.citiesinlife.CitiesInLife;
import com.branciho.citiesinlife.entity.CarEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws the car and turns its wheels.
 *
 * <p>A plain {@link EntityRenderer} rather than a MobRenderer, the way vanilla draws a boat. A
 * MobRenderer applies its own translate for a standing creature's feet, and a car is not standing
 * on anything.
 *
 * <p>The wheels are turned by distance covered rather than by time, so a car stopped at a junction
 * has stopped wheels. A wheel of radius three model units rolls its circumference in one revolution,
 * which is where the constant below comes from.
 */
public class CarRenderer extends EntityRenderer<CarEntity> {

    /**
     * One texture per livery, resolved once at class load.
     *
     * <p>Keyed by the enum's own ordinal so adding a livery is adding one enum constant and one
     * PNG. Resolving the path per frame would be building a ResourceLocation sixty times a second
     * per car to arrive at the same four answers.
     */
    private static final ResourceLocation[] TEXTURES = textures();

    private static ResourceLocation[] textures() {
        CarEntity.Livery[] liveries = CarEntity.Livery.values();
        ResourceLocation[] paths = new ResourceLocation[liveries.length];
        for (int i = 0; i < liveries.length; i++) {
            paths[i] = CitiesInLife.id("textures/entity/car/" + liveries[i].texture() + ".png");
        }
        return paths;
    }

    /**
     * How long each lamp holds before the other takes over.
     *
     * <p>Eight ticks, which is the same period the siren swaps tone on. That is not a coincidence
     * worth leaving to chance - a light bar flashing out of step with its own siren looks like two
     * separate faults.
     */
    private static final int FLASH_TICKS = 8;

    /**
     * How much bigger the car is drawn than it is authored.
     *
     * <p>The mesh describes a car two blocks long, which sounded right on paper and turned out to
     * look like a toy parked next to a person who is nearly two blocks tall on their own. At this
     * scale it is about three and a half blocks long and a little taller than the player, which is
     * roughly the proportion a real car has to a real driver.
     *
     * <p>Scaling here rather than re-authoring the mesh, because the model is scaled about the
     * origin and the wheels already sit with their bottoms on it - so the car grows upward and
     * stays on the road. Re-authoring would also have meant redrawing every UV.
     */
    private static final float SCALE = 1.75F;

    /**
     * Radians of wheel rotation per block travelled: one turn per 2*pi*r.
     *
     * <p>r is 3/16 of a block as authored, so the drawn wheel is that much larger again - and a
     * bigger wheel turns more slowly over the same ground. Forgetting that is how a car ends up
     * driving with its wheels visibly spinning too fast for the speed it is doing.
     */
    private static final float ROLL_PER_BLOCK = 1.0F / (3.0F / 16.0F * SCALE);

    private final ModelPart root;
    private final ModelPart[] wheels;
    private final ModelPart lightLeft;
    private final ModelPart lightRight;

    public CarRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.root = context.bakeLayer(CarModel.LAYER);
        this.wheels = new ModelPart[]{
                root.getChild(CarModel.WHEEL_FRONT_LEFT),
                root.getChild(CarModel.WHEEL_FRONT_RIGHT),
                root.getChild(CarModel.WHEEL_REAR_LEFT),
                root.getChild(CarModel.WHEEL_REAR_RIGHT)};
        this.lightLeft = root.getChild(CarModel.LIGHT_LEFT);
        this.lightRight = root.getChild(CarModel.LIGHT_RIGHT);
        this.shadowRadius = 1.0F * SCALE;
    }

    @Override
    public void render(CarEntity car, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        super.render(car, entityYaw, partialTick, poseStack, buffer, packedLight);

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-entityYaw));
        // Geometry is authored with Y downwards; this puts it the right way up. No half-block
        // translate here - that is a block-entity correction and would slide the car sideways.
        poseStack.scale(SCALE, -SCALE, -SCALE);

        float roll = car.travelled(partialTick) * ROLL_PER_BLOCK;
        for (ModelPart wheel : wheels) {
            wheel.xRot = roll;
        }

        // One lamp at a time, alternating, which is what makes it read as flashing rather than as
        // a coloured box bolted to the roof. A saloon shows neither.
        boolean emergency = car.livery().emergency();
        boolean left = (car.tickCount / FLASH_TICKS) % 2 == 0;
        lightLeft.visible = emergency && left;
        lightRight.visible = emergency && !left;

        VertexConsumer consumer = buffer.getBuffer(
                RenderType.entityCutoutNoCull(getTextureLocation(car)));
        root.render(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(CarEntity entity) {
        return TEXTURES[entity.livery().ordinal()];
    }
}
