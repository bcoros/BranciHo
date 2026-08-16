package com.branciho.citiesinlife.menu;

import com.branciho.citiesinlife.blockentity.BoilerBlockEntity;
import com.branciho.citiesinlife.registry.ModMenus;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The boiler's menu: water above, fire below, laid out the way a furnace is.
 *
 * <p>The arrangement is copied from the furnace on purpose. Anyone who has played Minecraft for an
 * hour already knows that fuel goes in the bottom slot, and spending that knowledge is better than
 * teaching a new layout for what is, underneath, the same idea.
 */
public class BoilerMenu extends AbstractContainerMenu {

    public static final int WATER_X = 56;
    public static final int WATER_Y = 17;
    public static final int FUEL_X = 56;
    public static final int FUEL_Y = 53;

    public static final int STATUS_Y = 72;
    public static final int PLAYER_X = 8;
    public static final int PLAYER_Y = 94;
    public static final int HOTBAR_Y = 152;

    private final Container container;
    private final ContainerData data;

    /** Client-side constructor: the real contents arrive through the menu's own syncing. */
    public BoilerMenu(int id, Inventory playerInventory) {
        this(id, playerInventory, new SimpleContainer(BoilerBlockEntity.TOTAL_SLOTS),
                new SimpleContainerData(BoilerBlockEntity.DATA_SIZE));
    }

    public BoilerMenu(int id, Inventory playerInventory, Container container, ContainerData data) {
        super(ModMenus.BOILER.get(), id);
        checkContainerSize(container, BoilerBlockEntity.TOTAL_SLOTS);
        checkContainerDataCount(data, BoilerBlockEntity.DATA_SIZE);
        this.container = container;
        this.data = data;
        container.startOpen(playerInventory.player);
        addDataSlots(data);

        // One bucket at a time. A stack of sixteen sitting in there while the boiler hands them back
        // one at a time would be baffling to watch.
        addSlot(new Slot(container, BoilerBlockEntity.WATER_SLOT, WATER_X, WATER_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(Items.WATER_BUCKET) || stack.is(Items.BUCKET);
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });

        addSlot(new Slot(container, BoilerBlockEntity.FUEL_SLOT, FUEL_X, FUEL_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return container.canPlaceItem(BoilerBlockEntity.FUEL_SLOT, stack);
            }
        });

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(playerInventory, column + row * 9 + 9,
                        PLAYER_X + column * 18, PLAYER_Y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column, PLAYER_X + column * 18, HOTBAR_Y));
        }
    }

    private static final int BOILER_END = BoilerBlockEntity.TOTAL_SLOTS;
    private static final int PLAYER_END = BOILER_END + 36;

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        if (index < BOILER_END) {
            if (!moveItemStackTo(stack, BOILER_END, PLAYER_END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.is(Items.WATER_BUCKET) || stack.is(Items.BUCKET)) {
            if (!moveItemStackTo(stack, BoilerBlockEntity.WATER_SLOT, BoilerBlockEntity.WATER_SLOT + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, BoilerBlockEntity.FUEL_SLOT, BoilerBlockEntity.FUEL_SLOT + 1, false)) {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    /** How far through the current piece of fuel the fire is, as a fraction. */
    public float burnProgress() {
        int duration = data.get(BoilerBlockEntity.DATA_BURN_DURATION);
        if (duration <= 0) {
            return 0.0F;
        }
        return Math.min(1.0F, data.get(BoilerBlockEntity.DATA_BURN_TIME) / (float) duration);
    }

    public float waterLevel() {
        return Math.min(1.0F, data.get(BoilerBlockEntity.DATA_WATER) / (float) BoilerBlockEntity.WATER_CAPACITY);
    }

    public float steamLevel() {
        return Math.min(1.0F, data.get(BoilerBlockEntity.DATA_STEAM) / (float) BoilerBlockEntity.STEAM_CAPACITY);
    }

    public int status() {
        return data.get(BoilerBlockEntity.DATA_STATUS);
    }

    @Override
    public boolean stillValid(Player player) {
        return container.stillValid(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        container.stopOpen(player);
    }
}
