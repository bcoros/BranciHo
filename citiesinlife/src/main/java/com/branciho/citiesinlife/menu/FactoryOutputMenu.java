package com.branciho.citiesinlife.menu;

import com.branciho.citiesinlife.blockentity.FactoryOutputBlockEntity;
import com.branciho.citiesinlife.registry.ModMenus;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The factory output crate's menu: one template slot and twenty-seven slots of storage.
 *
 * <p>The template slot is the whole interface. Put an oak log in it and the factory makes oak logs;
 * the log itself stays where it is, because it is a choice rather than an ingredient.
 */
public class FactoryOutputMenu extends AbstractContainerMenu {

    public static final int TEMPLATE_X = 80;
    public static final int TEMPLATE_Y = 20;
    public static final int STORAGE_X = 8;
    public static final int STORAGE_Y = 42;
    public static final int PLAYER_Y = 108;
    public static final int HOTBAR_Y = 168;

    private final Container container;

    /** Client-side constructor: the real contents arrive through the menu's own syncing. */
    public FactoryOutputMenu(int id, Inventory playerInventory) {
        this(id, playerInventory, new SimpleContainer(FactoryOutputBlockEntity.TOTAL_SLOTS));
    }

    public FactoryOutputMenu(int id, Inventory playerInventory, Container container) {
        super(ModMenus.FACTORY_OUTPUT.get(), id);
        checkContainerSize(container, FactoryOutputBlockEntity.TOTAL_SLOTS);
        this.container = container;
        container.startOpen(playerInventory.player);

        addSlot(new Slot(container, FactoryOutputBlockEntity.TEMPLATE_SLOT, TEMPLATE_X, TEMPLATE_Y));

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(container, column + row * 9,
                        STORAGE_X + column * 18, STORAGE_Y + row * 18));
            }
        }

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(playerInventory, column + row * 9 + 9,
                        STORAGE_X + column * 18, PLAYER_Y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column, STORAGE_X + column * 18, HOTBAR_Y));
        }
    }

    private static final int TEMPLATE_END = 1;
    private static final int STORAGE_END = TEMPLATE_END + FactoryOutputBlockEntity.STORAGE_SLOTS;
    private static final int PLAYER_END = STORAGE_END + 36;

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        if (index < STORAGE_END) {
            // Out of the crate and into the player.
            if (!moveItemStackTo(stack, STORAGE_END, PLAYER_END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, TEMPLATE_END, false)) {
            // Shift-clicking from the inventory sets the template rather than filling the crate,
            // since the crate is an output and filling it by hand makes no sense.
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
