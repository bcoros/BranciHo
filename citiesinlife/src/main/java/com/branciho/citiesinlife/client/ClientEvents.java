package com.branciho.citiesinlife.client;

import com.branciho.citiesinlife.CitiesInLife;
import com.branciho.citiesinlife.client.screen.CityScreen;
import com.branciho.citiesinlife.client.screen.ConfirmDeleteScreen;
import com.branciho.citiesinlife.client.screen.NameCityScreen;
import com.branciho.citiesinlife.net.CitiesInLifeNetwork;
import com.branciho.citiesinlife.net.ClientCityCache;
import com.branciho.citiesinlife.net.payload.RegisterStructurePayload;
import com.branciho.citiesinlife.net.payload.RequestCityPayload;
import com.branciho.citiesinlife.net.payload.StructureSyncPayload;
import com.branciho.citiesinlife.registry.ModItems;
import com.branciho.citiesinlife.structure.StructureType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * All client input for the planner.
 *
 * <p>Every hotkey and click here does something visible. That is not a given — the previous attempt
 * at this mod shipped a keybind wired to a boolean nothing read, which looked correct in the code and
 * did nothing in the game. If a handler is added here without something on screen changing, it is a
 * bug regardless of whether it compiles.
 */
@EventBusSubscriber(modid = CitiesInLife.MOD_ID, value = Dist.CLIENT)
public final class ClientEvents {

    private ClientEvents() {
    }

    private static boolean holdingWand(LocalPlayer player) {
        ItemStack held = player.getMainHandItem();
        return held.is(ModItems.PLANNER_WAND.get());
    }

    // ------------------------------------------------------------------ ticks

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return;
        }

        ClientSelection.tick();

        while (KeyBindings.OPEN_CITY.consumeClick()) {
            CitiesInLifeNetwork.sendToServer(new RequestCityPayload());
            minecraft.setScreen(new CityScreen());
        }

        while (KeyBindings.TOGGLE_STRUCTURE_MODE.consumeClick()) {
            StructureMode.toggle();
            if (StructureMode.active()) {
                // Ask for a fresh snapshot: outlining stale data would show buildings that are not
                // there any more, which is worse than showing nothing.
                CitiesInLifeNetwork.sendToServer(new RequestCityPayload());
            }
            player.displayClientMessage(Component.translatable(StructureMode.active()
                    ? "hud.citiesinlife.structure_mode_on"
                    : "hud.citiesinlife.structure_mode_off"), true);
        }
    }

    // ------------------------------------------------------------ right click

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getLevel().isClientSide()) {
            return;
        }
        if (!(event.getEntity() instanceof LocalPlayer player) || !holdingWand(player)) {
            return;
        }
        handleRightClick(player);
        // Stop the block underneath being opened or activated while planning.
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onRightClickEmpty(PlayerInteractEvent.RightClickEmpty event) {
        if (event.getEntity() instanceof LocalPlayer player && holdingWand(player)) {
            handleRightClick(player);
        }
    }

    private static void handleRightClick(LocalPlayer player) {
        if (player.isShiftKeyDown() && ClientSelection.active()) {
            ClientSelection.cancel();
            player.displayClientMessage(
                    Component.translatable("hud.citiesinlife.selection_cleared"), true);
            return;
        }
        ClientSelection.advance();
    }

    // ------------------------------------------------------------- left click

    @SubscribeEvent
    public static void onAttack(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isAttack()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || !holdingWand(player)) {
            return;
        }

        // Deleting a registration takes priority: it is only reachable while structure mode is on
        // and the player is sneaking, so it cannot be triggered by accident during normal planning.
        if (StructureMode.active() && player.isShiftKeyDown()) {
            StructureSyncPayload.Entry target = StructureMode.lookingAt();
            if (target != null) {
                minecraft.setScreen(new ConfirmDeleteScreen(target));
            } else {
                player.displayClientMessage(
                        Component.translatable("hud.citiesinlife.nothing_targeted"), true);
            }
            event.setSwingHand(false);
            event.setCanceled(true);
            return;
        }

        if (ClientSelection.phase() == ClientSelection.Phase.COMPLETE) {
            confirmSelection(minecraft, player);
        }
        // The wand never breaks anything, so swallow the swing either way.
        event.setSwingHand(false);
        event.setCanceled(true);
    }

    private static void confirmSelection(Minecraft minecraft, LocalPlayer player) {
        StructureType type = ClientSelection.type();
        if (ClientSelection.pointA() == null || ClientSelection.pointB() == null) {
            return;
        }

        // Founding needs a name, so the one case that opens a screen is the first structure ever.
        if (type == StructureType.CITY_CORE && !ClientCityCache.hasCity()) {
            minecraft.setScreen(new NameCityScreen());
            return;
        }

        CitiesInLifeNetwork.sendToServer(new RegisterStructurePayload(
                ClientSelection.pointA(), ClientSelection.pointB(), type.id(), ""));
        ClientSelection.cancel();
    }

    // ----------------------------------------------------------------- scroll

    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || !holdingWand(player) || !ClientSelection.active()) {
            return;
        }
        double delta = event.getScrollDeltaY();
        if (delta == 0.0D) {
            return;
        }
        // Scrolling only steals the hotbar while a selection is in progress; the rest of the time
        // the wand behaves like any other item so switching slots still works.
        ClientSelection.cycleType(delta > 0.0D ? -1 : 1);
        event.setCanceled(true);
    }

    // --------------------------------------------------------------- lifecycle

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        // In single player the JVM outlives the world; without this the next world opens showing the
        // previous one's city and half-drawn selection.
        ClientCityCache.clear();
        ClientSelection.reset();
        StructureMode.deactivate();
    }

    // --------------------------------------------------------------- rendering

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        SelectionRenderer.render(event.getPoseStack(), event.getCamera().getPosition());
    }
}
