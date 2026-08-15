package com.branciho.livingcities.client;

import com.branciho.livingcities.LivingCities;
import com.branciho.livingcities.item.CityPlannerToolItem;
import com.branciho.livingcities.item.SelectionData;
import com.branciho.livingcities.net.payload.RequestCityDataPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Client game-bus handling: hotkeys and world overlays.
 *
 * <p>The selection box is drawn from the tool's own data component, so it appears the moment the
 * server confirms a corner and needs no extra networking.
 */
@EventBusSubscriber(modid = LivingCities.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class ClientEvents {

    private static boolean overlayEnabled;

    private ClientEvents() {
    }

    public static boolean overlayEnabled() {
        return overlayEnabled;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        while (KeyBindings.OPEN_MANAGEMENT.consumeClick()) {
            // Ask the server what we are allowed to see; it decides and pushes the screen back.
            PacketDistributor.sendToServer(new RequestCityDataPayload(true));
        }
        while (KeyBindings.TOGGLE_OVERLAY.consumeClick()) {
            overlayEnabled = !overlayEnabled;
            minecraft.player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable(overlayEnabled
                            ? "message.livingcities.overlay_on"
                            : "message.livingcities.overlay_off"), true);
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }

        AABB box = selectionBox(player);
        if (box == null) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer consumer = buffers.getBuffer(RenderType.lines());

        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        LevelRenderer.renderLineBox(poseStack, consumer, box, 0.25F, 0.9F, 1.0F, 0.9F);
        poseStack.popPose();

        buffers.endBatch(RenderType.lines());
    }

    /** The active planner selection in either hand, or null when the player is not holding the tool. */
    private static AABB selectionBox(LocalPlayer player) {
        for (ItemStack stack : new ItemStack[]{player.getMainHandItem(), player.getOffhandItem()}) {
            if (stack.getItem() instanceof CityPlannerToolItem) {
                SelectionData selection = CityPlannerToolItem.selectionOf(stack);
                AABB box = selection.toBox();
                if (box != null) {
                    return box;
                }
            }
        }
        return null;
    }
}
