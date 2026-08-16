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
 * <p>Every handler here changes something visible. That is not a given — the predecessor to this mod
 * shipped a keybind wired to a boolean nothing read, which looked correct and did nothing. If a
 * handler is added here and nothing on screen changes, it is a bug regardless of whether it compiles.
 */
@EventBusSubscriber(modid = CitiesInLife.MOD_ID, value = Dist.CLIENT)
public final class ClientEvents {

    private ClientEvents() {
    }

    private static boolean holdingWand(LocalPlayer player) {
        return player.getMainHandItem().is(ModItems.PLANNER_WAND.get());
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
                // Ask for a fresh snapshot: outlining stale data would draw buildings that are not
                // there any more, which is worse than drawing nothing.
                CitiesInLifeNetwork.sendToServer(new RequestCityPayload());
            }
            player.displayClientMessage(Component.translatable(StructureMode.active()
                    ? "hud.citiesinlife.structure_mode_on"
                    : "hud.citiesinlife.structure_mode_off"), true);
        }

        // Type and measurement keys work whether or not a box is being drawn, so the player can set
        // up what they are about to place before placing it.
        boolean holdingWand = holdingWand(player);

        while (KeyBindings.TYPE_PREVIOUS.consumeClick()) {
            if (holdingWand) {
                ClientSelection.cycleType(-1);
            }
        }
        while (KeyBindings.TYPE_NEXT.consumeClick()) {
            if (holdingWand) {
                ClientSelection.cycleType(1);
            }
        }
        while (KeyBindings.TOGGLE_MEASURE_MODE.consumeClick()) {
            if (holdingWand) {
                ClientSelection.toggleMeasureMode();
                player.displayClientMessage(Component.translatable(
                        "hud.citiesinlife.measure_switched",
                        ClientSelection.measureMode().displayName()), true);
            }
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

    /**
     * Right click does everything: place corners, clear a selection, and delete a registration.
     *
     * <p>Deleting used to be on Shift + left click and did not work reliably — the attack key is
     * fought over by several things at once. Right click is the same path that already places
     * corners, so it is known to fire, and sneaking plus structure mode is a specific enough
     * combination that it cannot happen by accident.
     */
    private static void handleRightClick(LocalPlayer player) {
        if (player.isShiftKeyDown()) {
            if (StructureMode.active()) {
                deleteTargeted(player);
                return;
            }
            if (ClientSelection.active()) {
                ClientSelection.cancel();
                player.displayClientMessage(
                        Component.translatable("hud.citiesinlife.selection_cleared"), true);
                return;
            }
        }
        ClientSelection.advance();
    }

    private static void deleteTargeted(LocalPlayer player) {
        Minecraft minecraft = Minecraft.getInstance();
        StructureSyncPayload.Entry target = StructureMode.lookingAt();
        if (target == null) {
            player.displayClientMessage(
                    Component.translatable("hud.citiesinlife.nothing_targeted"), true);
            return;
        }
        minecraft.setScreen(new ConfirmDeleteScreen(target));
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
                ClientSelection.pointA(),
                ClientSelection.pointB(),
                type.id(),
                ClientSelection.measureMode().id(),
                ""));
        ClientSelection.cancel();
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
