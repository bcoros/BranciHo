package com.branciho.citiesinlife.menu;

import com.branciho.citiesinlife.blockentity.RegisterCounterBlockEntity;
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

/**
 * The till's menu: one slot for what is on sale, and four buttons for what it costs.
 *
 * <p>The price lives on the block entity, not here. A button click is sent to the server, written
 * through the container data, and syncs back — so a client that lies about the price is telling the
 * server a number the server never reads.
 */
public class RegisterCounterMenu extends AbstractContainerMenu {

    public static final int PRODUCT_X = 80;
    public static final int PRODUCT_Y = 20;
    public static final int PRICE_Y = 44;
    public static final int STATUS_Y = 62;
    public static final int INVENTORY_X = 8;
    public static final int INVENTORY_Y = 84;
    public static final int HOTBAR_Y = 142;

    /** Button ids. Two step sizes because nudging from 30 to 55 one at a time is nobody's idea of fun. */
    public static final int BUTTON_MINUS_TEN = 0;
    public static final int BUTTON_MINUS_ONE = 1;
    public static final int BUTTON_PLUS_ONE = 2;
    public static final int BUTTON_PLUS_TEN = 3;

    private final Container container;
    private final ContainerData data;

    public RegisterCounterMenu(int id, Inventory playerInventory) {
        this(id, playerInventory, new SimpleContainer(RegisterCounterBlockEntity.SLOTS),
                new SimpleContainerData(RegisterCounterBlockEntity.DATA_SIZE));
    }

    public RegisterCounterMenu(int id, Inventory playerInventory, Container container, ContainerData data) {
        super(ModMenus.REGISTER_COUNTER.get(), id);
        checkContainerSize(container, RegisterCounterBlockEntity.SLOTS);
        checkContainerDataCount(data, RegisterCounterBlockEntity.DATA_SIZE);
        this.container = container;
        this.data = data;
        container.startOpen(playerInventory.player);
        addDataSlots(data);

        addSlot(new Slot(container, 0, PRODUCT_X, PRODUCT_Y));

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(playerInventory, column + row * 9 + 9,
                        INVENTORY_X + column * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column, INVENTORY_X + column * 18, HOTBAR_Y));
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        int step = switch (id) {
            case BUTTON_MINUS_TEN -> -10;
            case BUTTON_MINUS_ONE -> -1;
            case BUTTON_PLUS_ONE -> 1;
            case BUTTON_PLUS_TEN -> 10;
            default -> 0;
        };
        if (step == 0) {
            return false;
        }
        data.set(RegisterCounterBlockEntity.DATA_PRICE, price() + step);
        broadcastChanges();
        return true;
    }

    public int price() {
        return data.get(RegisterCounterBlockEntity.DATA_PRICE);
    }

    public int workers() {
        return data.get(RegisterCounterBlockEntity.DATA_WORKERS);
    }

    public int customers() {
        return data.get(RegisterCounterBlockEntity.DATA_CUSTOMERS);
    }

    public int takings() {
        return data.get(RegisterCounterBlockEntity.DATA_TAKINGS);
    }

    public int status() {
        return data.get(RegisterCounterBlockEntity.DATA_STATUS);
    }

    private static final int PRODUCT_END = 1;
    private static final int PLAYER_END = PRODUCT_END + 36;

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        if (index < PRODUCT_END) {
            if (!moveItemStackTo(stack, PRODUCT_END, PLAYER_END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, PRODUCT_END, false)) {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
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
