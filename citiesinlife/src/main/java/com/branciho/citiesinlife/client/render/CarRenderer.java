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
 * Draws whichever vehicle this is, and turns its wheels.
 *
 * <p>A plain {@link EntityRenderer} rather than a MobRenderer, the way vanilla draws a boat. A
 * MobRenderer applies its own translate for a standing creature's feet, and a car is not standing
 * on anything.
 *
 * <p>Three meshes behind four liveries. A police car genuinely <em>is</em> a saloon with a light
 * bar on it, so it shares the car's mesh; a fire appliance and an ambulance are not, and painting
 * them onto a hatchback made them a red car and a white car. What makes those two recognisable
 * from across a city is the silhouette — long and square-shouldered, or a tall box on a short
 * cab — and none of that is a texture.
 *
 * <p>The wheels are turned by distance covered rather than by time, so a car stopped at a junction
 * has stopped wheels.
 */
public class CarRenderer extends EntityRenderer<CarEntity> {

    /**
     * How much bigger a vehicle is drawn than it is authored.
     *
     * <p>The saloon's mesh describes a car two blocks long, which sounded right on paper and turned
     * out to look like a toy parked next to a person who is nearly two blocks tall on their own. At
     * this scale it is about three and a half blocks long and a little taller than the player.
     *
     * <p>One scale for all three, because the meshes already carry the size difference: the truck
     * is authored longer and taller than the car rather than being the car drawn bigger.
     *
     * <p>Scaling here rather than re-authoring, because the models are scaled about the origin and
     * every wheel already sits with its bottom on it — so a vehicle grows upward and stays on the
     * road.
     */
    private static final float SCALE = 1.75F;

    /**
     * How long each lamp holds before the other takes over.
     *
     * <p>Eight ticks, which is the same period the siren swaps tone on. That is not a coincidence
     * worth leaving to chance — a light bar flashing out of step with its own siren looks like two
     * separate faults.
     */
    private static final int FLASH_TICKS = 8;

    /**
     * One baked vehicle: its parts, and how fast its wheels turn for the ground it covers.
     *
     * <p>A wheel of radius r blocks rolls its circumference in one revolution, so the radians per
     * block travelled is 1/r. Getting that from the mesh rather than hard-coding it is what stops
     * the truck's bigger wheels spinning at the car's rate, which reads as the whole thing sliding.
     */
    private record Vehicle(ModelPart root, ModelPart[] wheels, ModelPart lightLeft,
                           ModelPart lightRight, float rollPerBlock, ResourceLocation texture) {
    }

    private final Vehicle[] vehicles;

    public CarRenderer(EntityRendererProvider.Context context) {
        super(context);
        // The saloon mesh serves both the citizens' car and the police car; only the paint and the
        // light bar differ, which is also true of the real thing.
        Vehicle saloon = bake(context, CarModel.LAYER, CarModel.WHEELS, 3.0F, "car");
        this.vehicles = new Vehicle[]{
                saloon,
                bake(context, CarModel.LAYER, CarModel.WHEELS, 3.0F, "police_car"),
                bake(context, FireTruckModel.LAYER, FireTruckModel.WHEELS, 4.0F, "fire_truck"),
                bake(context, AmbulanceModel.LAYER, AmbulanceModel.WHEELS, 4.0F, "ambulance")};
        this.shadowRadius = 1.0F * SCALE;
    }

    private static Vehicle bake(EntityRendererProvider.Context context,
                                net.minecraft.client.model.geom.ModelLayerLocation layer,
                                String[] wheelNames, float wheelRadius, String texture) {
        ModelPart root = context.bakeLayer(layer);
        ModelPart[] wheels = new ModelPart[wheelNames.length];
        for (int i = 0; i < wheelNames.length; i++) {
            wheels[i] = root.getChild(wheelNames[i]);
        }
        return new Vehicle(root, wheels,
                root.getChild(CarModel.LIGHT_LEFT), root.getChild(CarModel.LIGHT_RIGHT),
                1.0F / (wheelRadius / 16.0F * SCALE),
                CitiesInLife.id("textures/entity/car/" + texture + ".png"));
    }

    /**
     * Which mesh to draw.
     *
     * <p>Clamped rather than indexed straight off the ordinal: the livery arrives over the network
     * as a byte, and a malformed or future value should be a saloon rather than an exception thrown
     * once per frame.
     */
    private Vehicle vehicleFor(CarEntity car) {
        int index = car.livery().ordinal();
        return index >= 0 && index < vehicles.length ? vehicles[index] : vehicles[0];
    }

    @Override
    public void render(CarEntity car, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        super.render(car, entityYaw, partialTick, poseStack, buffer, packedLight);
        Vehicle vehicle = vehicleFor(car);

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-entityYaw));
        // Geometry is authored with Y downwards; this puts it the right way up. No half-block
        // translate here - that is a block-entity correction and would slide the car sideways.
        poseStack.scale(SCALE, -SCALE, -SCALE);

        float roll = car.travelled(partialTick) * vehicle.rollPerBlock();
        for (ModelPart wheel : vehicle.wheels()) {
            wheel.xRot = roll;
        }

        // One lamp at a time, alternating, which is what makes it read as flashing rather than as
        // a coloured box bolted to the roof. A saloon shows neither.
        boolean emergency = car.livery().emergency();
        boolean left = (car.tickCount / FLASH_TICKS) % 2 == 0;
        vehicle.lightLeft().visible = emergency && left;
        vehicle.lightRight().visible = emergency && !left;

        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(vehicle.texture()));
        vehicle.root().render(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(CarEntity entity) {
        return vehicleFor(entity).texture();
    }
}
