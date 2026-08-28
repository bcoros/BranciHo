package com.branciho.citiesinlife.client;

import com.branciho.citiesinlife.CitiesInLife;
import com.branciho.citiesinlife.client.screen.CallToArmsScreen;
import com.branciho.citiesinlife.client.screen.CityScreen;
import com.branciho.citiesinlife.client.screen.ConfirmDeleteCityScreen;
import com.branciho.citiesinlife.client.screen.MilitaryScreen;
import com.branciho.citiesinlife.client.screen.NameCityScreen;
import com.branciho.citiesinlife.client.screen.ReactorScreen;
import com.branciho.citiesinlife.client.screen.RoadToolScreen;
import com.branciho.citiesinlife.net.CitiesInLifeNetwork;
import com.branciho.citiesinlife.net.ClientArmyCache;
import com.branciho.citiesinlife.net.ClientCityCache;
import com.branciho.citiesinlife.net.payload.CallToArmsPayload;
import com.branciho.citiesinlife.net.payload.ConfirmDeleteCityPayload;
import com.branciho.citiesinlife.net.payload.DeleteAreaPayload;
import com.branciho.citiesinlife.net.payload.LinkPowerPayload;
import com.branciho.citiesinlife.net.payload.LinkOutletPayload;
import com.branciho.citiesinlife.net.payload.LinkWaterPayload;
import com.branciho.citiesinlife.net.payload.MarkPathPayload;
import com.branciho.citiesinlife.net.payload.MarkRoadPayload;
import com.branciho.citiesinlife.net.payload.UpgradePayload;
import com.branciho.citiesinlife.net.payload.RegisterStructurePayload;
import com.branciho.citiesinlife.net.payload.RequestArmyPayload;
import com.branciho.citiesinlife.net.payload.SeizeStructurePayload;
import com.branciho.citiesinlife.net.payload.RequestCityPayload;
import com.branciho.citiesinlife.net.payload.ToggleCreativeMoneyPayload;
import com.branciho.citiesinlife.registry.ModItems;
import com.branciho.citiesinlife.structure.StructureType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * All client input for the planner and the power line tool.
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

    private static boolean holdingLineTool(LocalPlayer player) {
        return player.getMainHandItem().is(ModItems.POWER_LINE_TOOL.get());
    }

    static boolean holdingPipeTool(LocalPlayer player) {
        return player.getMainHandItem().is(ModItems.PIPE_LINE_TOOL.get());
    }

    /**
     * The path tool draws a box exactly like the wand does.
     *
     * <p>That is the entire reason it is a tool rather than a block. The player was blunt about path
     * nodes and entrance markers being bad, and they were right: placing a block every metre of every
     * street is not planning, it is data entry. Drawing a box round the street is planning.
     */
    static boolean holdingPathTool(LocalPlayer player) {
        return player.getMainHandItem().is(ModItems.PATH_TOOL.get());
    }

    /**
     * The road tool draws its box exactly as the path tool does.
     *
     * <p>Package-private because {@link SelectionRenderer} asks the same question to decide whether
     * to show the road overlay.
     */
    static boolean holdingRoadTool(LocalPlayer player) {
        return player.getMainHandItem().is(ModItems.ROAD_TOOL.get());
    }

    static boolean holdingUpgradeTool(LocalPlayer player) {
        return player.getMainHandItem().is(ModItems.UPGRADE_TOOL.get());
    }

    /**
     * The red wand.
     *
     * <p>Draws a box exactly as the blue one does — same clicks, same corners, same confirm — and
     * differs only in what the box means and what colour it is drawn in.
     */
    static boolean holdingWarWand(LocalPlayer player) {
        return player.getMainHandItem().is(ModItems.WAR_PLANNER_WAND.get());
    }

    private static boolean holdingMilitaryTool(LocalPlayer player) {
        return player.getMainHandItem().is(ModItems.MILITARY_TOOL.get());
    }

    /**
     * Ask for the roll, then open the screen looking at it.
     *
     * <p>In that order and without waiting: the screen reads a cache that the reply fills in, and it
     * rebuilds itself when that happens. Opening only once the packet arrives would put a visible
     * delay between the click and the window on any server that is not the one in this process.
     */
    private static void openMilitary(Minecraft minecraft) {
        CitiesInLifeNetwork.sendToServer(new RequestArmyPayload());
        minecraft.setScreen(new MilitaryScreen());
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

        // The server refuses to delete a city hall without being asked twice; this is the asking.
        ConfirmDeleteCityPayload pendingDelete = ClientCityCache.takeDeleteConfirm();
        if (pendingDelete != null) {
            minecraft.setScreen(new ConfirmDeleteCityScreen(pendingDelete));
        }

        // An ally's declaration, arriving as a question with buttons on it.
        CallToArmsPayload call = ClientCityCache.takeCallToArms();
        if (call != null) {
            minecraft.setScreen(new CallToArmsScreen(call));
        }

        // The control room, opened at the server's request. A block's use handler runs server-side
        // where setScreen does not exist, so the open has to come back as a message.
        BlockPos monitor = ClientCityCache.takeMonitor();
        if (monitor != null) {
            minecraft.setScreen(new ReactorScreen(monitor));
        }

        while (KeyBindings.OPEN_CITY.consumeClick()) {
            CitiesInLifeNetwork.sendToServer(new RequestCityPayload());
            minecraft.setScreen(new CityScreen());
        }

        while (KeyBindings.TOGGLE_CREATIVE_MONEY.consumeClick()) {
            // No client-side check for creative mode. The server owns the answer, and refusing here
            // would mean a survival player pressing it got silence rather than a reply.
            CitiesInLifeNetwork.sendToServer(new ToggleCreativeMoneyPayload());
        }

        while (KeyBindings.TOGGLE_STRUCTURE_MODE.consumeClick()) {
            StructureMode.toggle();
            // A half-drawn box left over from the other mode would be confusing: in structure mode a
            // box deletes, outside it a box creates.
            ClientSelection.cancel();
            if (StructureMode.active()) {
                CitiesInLifeNetwork.sendToServer(new RequestCityPayload());
            }
            player.displayClientMessage(Component.translatable(StructureMode.active()
                    ? "hud.citiesinlife.structure_mode_on"
                    : "hud.citiesinlife.structure_mode_off"), true);
        }

        // Type and measurement keys work whether or not a box is being drawn, so the player can set
        // up what they are about to place before placing it.
        boolean holdingWand = holdingWand(player);
        boolean holdingWarWand = holdingWarWand(player);

        while (KeyBindings.TYPE_PREVIOUS.consumeClick()) {
            if (holdingWand) {
                ClientSelection.cycleType(-1);
            } else if (holdingWarWand) {
                ClientWarWand.cycle(-1);
                player.displayClientMessage(ClientWarWand.describe(), true);
            }
        }
        while (KeyBindings.TYPE_NEXT.consumeClick()) {
            if (holdingWand) {
                ClientSelection.cycleType(1);
            } else if (holdingWarWand) {
                ClientWarWand.cycle(1);
                player.displayClientMessage(ClientWarWand.describe(), true);
            }
        }
        // Only with the tool in hand: R is a common key and stealing it everywhere would be rude.
        while (KeyBindings.OPEN_ROAD_TOOL.consumeClick()) {
            if (holdingRoadTool(player)) {
                minecraft.setScreen(new RoadToolScreen());
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
        // Minecraft tries the main hand and then the off hand, firing this event once for each.
        // Without this check a single click ran the handler twice: the first call set the pending
        // end of a power line and the second immediately linked it to itself, which is why every
        // attempt reported "that is the same block". It was double-advancing the planner too.
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        if (!(event.getEntity() instanceof LocalPlayer player)) {
            return;
        }
        if (holdingMilitaryTool(player)) {
            openMilitary(Minecraft.getInstance());
            event.setCanceled(true);
        } else if (holdingWand(player) || holdingPathTool(player) || holdingRoadTool(player)
                || holdingWarWand(player)) {
            handlePlannerRightClick(player);
            event.setCanceled(true);
        } else if (holdingLineTool(player)) {
            handleLineRightClick(player, event.getPos());
            event.setCanceled(true);
        } else if (holdingPipeTool(player)) {
            handlePipeRightClick(player, event.getPos());
            event.setCanceled(true);
        }
    }

    /**
     * Right-clicking thin air.
     *
     * <p>This used to listen to {@code PlayerInteractEvent.RightClickEmpty}, which never fired:
     * that event is only raised for a hand holding <em>nothing</em>, and both branches require a
     * tool in the main hand. So placing a corner while pointing at the sky did nothing, and there
     * was no way at all to abort a half-drawn power line.
     *
     * <p>The use-key hook fires with an item held. It runs for both hands and for block clicks too,
     * so it is gated to the main hand and to a genuine miss - block clicks are handled by
     * {@link #onRightClickBlock} and must not run twice.
     */
    @SubscribeEvent
    public static void onUseInput(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem() || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        if (minecraft.hitResult != null && minecraft.hitResult.getType() != HitResult.Type.MISS) {
            return;
        }

        if (holdingMilitaryTool(player)) {
            openMilitary(minecraft);
        } else if (holdingWand(player) || holdingPathTool(player) || holdingRoadTool(player)
                || holdingWarWand(player)) {
            handlePlannerRightClick(player);
        } else if (holdingLineTool(player) && player.isShiftKeyDown()) {
            ClientPowerTool.clear();
            player.displayClientMessage(Component.translatable("hud.citiesinlife.line_cleared"), true);
        } else if (holdingPipeTool(player) && player.isShiftKeyDown()) {
            ClientPipeTool.clear();
            player.displayClientMessage(Component.translatable("hud.citiesinlife.pipe_cleared"), true);
        } else {
            return;
        }
        event.setSwingHand(false);
        event.setCanceled(true);
    }

    private static void handlePlannerRightClick(LocalPlayer player) {
        if (player.isShiftKeyDown() && ClientSelection.active()) {
            ClientSelection.cancel();
            player.displayClientMessage(
                    Component.translatable("hud.citiesinlife.selection_cleared"), true);
            return;
        }
        ClientSelection.advance();
    }

    /**
     * Click one power block, then another, and the line goes in.
     *
     * <p>Sneaking while clicking the second one cuts an existing line instead of building one, so
     * undoing a mistake does not need a separate tool.
     */
    private static void handleLineRightClick(LocalPlayer player, BlockPos clicked) {
        BlockPos pending = ClientPowerTool.pending();
        if (pending == null) {
            ClientPowerTool.setPending(clicked);
            player.displayClientMessage(Component.translatable(
                    "hud.citiesinlife.line_started",
                    clicked.getX(), clicked.getY(), clicked.getZ()), true);
            return;
        }
        CitiesInLifeNetwork.sendToServer(
                new LinkPowerPayload(pending, clicked, player.isShiftKeyDown()));
        ClientPowerTool.clear();
    }

    /**
     * The same gesture for water.
     *
     * <p>Deliberately identical to the power line tool, down to sneaking to cut instead of build.
     * Two tools that look the same and behave differently would be worse than one tool doing both.
     */
    private static void handlePipeRightClick(LocalPlayer player, BlockPos clicked) {
        BlockPos pending = ClientPipeTool.pending();
        if (pending == null) {
            ClientPipeTool.setPending(clicked);
            player.displayClientMessage(Component.translatable(
                    "hud.citiesinlife.pipe_started",
                    clicked.getX(), clicked.getY(), clicked.getZ()), true);
            return;
        }
        CitiesInLifeNetwork.sendToServer(
                new LinkWaterPayload(pending, clicked, player.isShiftKeyDown()));
        ClientPipeTool.clear();
    }

    // ------------------------------------------------------------- left click

    @SubscribeEvent
    public static void onAttack(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isAttack()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }

        // Sneak + left click with the pipe tool plumbs an end pipe into a container. It has to be a
        // different gesture from the ordinary pipe link, because that one is a right click and right
        // clicking a chest opens the chest.
        if (holdingPipeTool(player)) {
            if (player.isShiftKeyDown()) {
                handleOutletClick(minecraft, player);
                event.setSwingHand(false);
                event.setCanceled(true);
            }
            return;
        }

        // Sneak + left click with the upgrade tool buys the machine you are pointing at a level.
        // Same gesture as the pipe tool's, for the same reason: sneaking with something in hand
        // never reaches a block's right-click handler at all.
        if (holdingUpgradeTool(player)) {
            if (player.isShiftKeyDown()) {
                handleUpgradeClick(minecraft, player);
            }
            event.setSwingHand(false);
            event.setCanceled(true);
            return;
        }

        boolean wand = holdingWand(player);
        boolean pathTool = holdingPathTool(player);
        boolean roadTool = holdingRoadTool(player);
        boolean warWand = holdingWarWand(player);
        if (!wand && !pathTool && !roadTool && !warWand) {
            return;
        }

        if (ClientSelection.phase() == ClientSelection.Phase.COMPLETE) {
            if (pathTool) {
                confirmPath(player);
            } else if (roadTool) {
                confirmRoad(player);
            } else if (warWand) {
                confirmSeizure();
            } else {
                confirmSelection(minecraft, player);
            }
        }
        // Neither tool ever breaks anything, so swallow the swing either way.
        event.setSwingHand(false);
        event.setCanceled(true);
    }

    /** Send off an upgrade for whatever the player is pointing at. */
    private static void handleUpgradeClick(Minecraft minecraft, LocalPlayer player) {
        if (!(minecraft.hitResult instanceof BlockHitResult hit)
                || minecraft.hitResult.getType() != HitResult.Type.BLOCK) {
            player.displayClientMessage(
                    Component.translatable("hud.citiesinlife.upgrade_nothing"), true);
            return;
        }
        CitiesInLifeNetwork.sendToServer(new UpgradePayload(hit.getBlockPos()));
    }

    /**
     * Click the end pipe, then the chest — or the other way round; the server works out which is
     * which. Sneaking on the second click unplumbs instead.
     */
    private static void handleOutletClick(Minecraft minecraft, LocalPlayer player) {
        if (!(minecraft.hitResult instanceof BlockHitResult hit)
                || minecraft.hitResult.getType() != HitResult.Type.BLOCK) {
            // Pointing at nothing is how you abandon a half-made link.
            ClientPipeTool.clear();
            player.displayClientMessage(
                    Component.translatable("hud.citiesinlife.outlet_cleared"), true);
            return;
        }
        BlockPos clicked = hit.getBlockPos();
        BlockPos pending = ClientPipeTool.pendingOutlet();
        if (pending == null) {
            ClientPipeTool.setPendingOutlet(clicked);
            player.displayClientMessage(Component.translatable(
                    "hud.citiesinlife.outlet_started",
                    clicked.getX(), clicked.getY(), clicked.getZ()), true);
            return;
        }
        CitiesInLifeNetwork.sendToServer(new LinkOutletPayload(pending, clicked));
        ClientPipeTool.clear();
    }

    /**
     * Turn the box into pavement, or take pavement away.
     *
     * <p>Sneaking flips it, the same way sneaking flips the two line tools from building to cutting.
     * One tool that does a thing and undoes it beats two tools that each do half.
     */
    private static void confirmPath(LocalPlayer player) {
        BlockPos a = ClientSelection.pointA();
        BlockPos b = ClientSelection.pointB();
        if (a == null || b == null) {
            return;
        }
        CitiesInLifeNetwork.sendToServer(new MarkPathPayload(a, b, player.isShiftKeyDown()));
        ClientSelection.cancel();
    }

    /**
     * Turn the box into road of whatever kind the brush is set to, or take road away.
     *
     * <p>Two ways to erase, and both work: the Remove brush in the panel, or sneaking while
     * confirming. Sneaking has always worked and stays, because it is the same gesture the path tool
     * and the two line tools use; the brush exists because a gesture nothing on screen mentions may
     * as well not exist, which is exactly how this ended up being reported as missing.
     */
    private static void confirmRoad(LocalPlayer player) {
        BlockPos a = ClientSelection.pointA();
        BlockPos b = ClientSelection.pointB();
        if (a == null || b == null) {
            return;
        }
        boolean remove = ClientRoadTool.erasing() || player.isShiftKeyDown();
        CitiesInLifeNetwork.sendToServer(
                new MarkRoadPayload(a, b, ClientRoadTool.flags(), remove));
        ClientSelection.cancel();
    }

    /**
     * Take the building in the box.
     *
     * <p>No structure-mode branch and no naming screen: seizing is one thing, and whether it is also
     * rewritten is already decided by the arrow keys before the box is confirmed.
     */
    private static void confirmSeizure() {
        BlockPos a = ClientSelection.pointA();
        BlockPos b = ClientSelection.pointB();
        if (a == null || b == null) {
            return;
        }
        CitiesInLifeNetwork.sendToServer(new SeizeStructurePayload(a, b, ClientWarWand.rewriteId()));
        ClientSelection.cancel();
    }

    private static void confirmSelection(Minecraft minecraft, LocalPlayer player) {
        BlockPos a = ClientSelection.pointA();
        BlockPos b = ClientSelection.pointB();
        if (a == null || b == null) {
            return;
        }

        // In structure mode a box removes registrations instead of creating one. Same gesture, and
        // the box is drawn red so there is no doubt which it is about to do.
        if (StructureMode.active()) {
            CitiesInLifeNetwork.sendToServer(new DeleteAreaPayload(a, b, false));
            ClientSelection.cancel();
            return;
        }

        StructureType type = ClientSelection.type();

        // Founding needs a name, so the one case that opens a screen is the first structure ever.
        if (type == StructureType.CITY_CORE && !ClientCityCache.hasCity()) {
            minecraft.setScreen(new NameCityScreen());
            return;
        }

        CitiesInLifeNetwork.sendToServer(new RegisterStructurePayload(
                a, b, type.id(), ClientSelection.measureMode().id(), ""));
        ClientSelection.cancel();
    }

    // --------------------------------------------------------------- lifecycle

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        // In single player the JVM outlives the world; without this the next world opens showing the
        // previous one's city and half-drawn selection.
        ClientCityCache.clear();
        ClientArmyCache.clear();
        ClientSelection.reset();
        ClientWarWand.reset();
        ClientRoadTool.reset();
        ClientPowerTool.clear();
        ClientPipeTool.clear();
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
