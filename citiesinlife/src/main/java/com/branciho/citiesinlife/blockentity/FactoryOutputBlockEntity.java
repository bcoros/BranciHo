package com.branciho.citiesinlife.blockentity;

import com.branciho.citiesinlife.city.City;
import com.branciho.citiesinlife.city.CityData;
import com.branciho.citiesinlife.menu.FactoryOutputMenu;
import com.branciho.citiesinlife.registry.ModBlockEntities;
import com.branciho.citiesinlife.structure.Structure;
import com.branciho.citiesinlife.structure.StructureType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The factory output: what makes a registered factory produce something you can hold.
 *
 * <p>Jobs and tax revenue are numbers on a screen. A crate that fills up with planks is the point at
 * which a factory stops being an abstraction and starts being a reason to build one.
 *
 * <p>The player puts one item in the template slot to say what the factory makes. That item is never
 * consumed — it is a choice, not an ingredient. Output accumulates in the crate until it is full, and
 * the block behaves as an ordinary container underneath, so hoppers, hopper minecarts and anything
 * else that pulls from a chest work without knowing this mod exists.
 */
public class FactoryOutputBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider {

    /** Storage slots, laid out nine wide like a chest. */
    public static final int STORAGE_SLOTS = 27;

    /** The slot holding the item that defines what is produced. */
    public static final int TEMPLATE_SLOT = STORAGE_SLOTS;

    public static final int TOTAL_SLOTS = STORAGE_SLOTS + 1;

    /** Ticks between production runs. Five seconds — slow enough to watch, fast enough to matter. */
    public static final int PRODUCE_INTERVAL = 100;

    /** Jobs needed for each extra item per run, so a bigger factory is worth building. */
    private static final int JOBS_PER_EXTRA_ITEM = 64;

    /** What the crate is doing, for the screen to explain. */
    public static final int STATUS_PRODUCING = 0;
    public static final int STATUS_NOT_A_FACTORY = 1;
    public static final int STATUS_NO_WORKERS = 2;
    public static final int STATUS_NO_TEMPLATE = 3;
    public static final int STATUS_FULL = 4;

    /** Indices into the two-value block of numbers the screen reads. */
    public static final int DATA_WORKERS = 0;
    public static final int DATA_STATUS = 1;
    public static final int DATA_SIZE = 2;

    private final NonNullList<ItemStack> items = NonNullList.withSize(TOTAL_SLOTS, ItemStack.EMPTY);

    /** How the screen learns what the crate is doing; kept in sync by the menu itself. */
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return index == DATA_WORKERS ? workers : status;
        }

        @Override
        public void set(int index, int value) {
            if (index == DATA_WORKERS) {
                workers = value;
            } else {
                status = value;
            }
        }

        @Override
        public int getCount() {
            return DATA_SIZE;
        }
    };

    /** Cached for the screen: what the enclosing structure offers, refreshed on each production run. */
    private int workers;
    private boolean validFactory;
    private int status = STATUS_NOT_A_FACTORY;

    public FactoryOutputBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FACTORY_OUTPUT.get(), pos, state);
    }

    // ------------------------------------------------------------- production

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  FactoryOutputBlockEntity factory) {
        if (level.getGameTime() % PRODUCE_INTERVAL != 0) {
            return;
        }
        factory.refreshFactoryState(level, pos);
        if (!factory.validFactory || factory.workers <= 0) {
            return;
        }
        ItemStack template = factory.items.get(TEMPLATE_SLOT);
        if (template.isEmpty()) {
            factory.status = STATUS_NO_TEMPLATE;
            return;
        }

        int amount = 1 + factory.workers / JOBS_PER_EXTRA_ITEM;
        if (factory.insert(template.getItem().getDefaultInstance(), amount)) {
            factory.status = STATUS_PRODUCING;
            factory.setChanged();
        } else {
            factory.status = STATUS_FULL;
        }
    }

    /**
     * Look up the structure this block stands in and see whether it is a working factory.
     *
     * <p>The important word is <em>staffed</em>. A structure's {@code jobs()} is how many people it
     * has room to employ, which is a property of the walls and has nothing to do with whether anyone
     * turned up. Reading that number directly meant a crate in an empty city kept stamping out goods
     * with nobody inside — the building was counted as its own workforce.
     *
     * <p>What actually staffs it is the city's employment: the share of the city's employed residents
     * that this factory's job slots account for. No population means no employment means no workers
     * means no output, which is what a factory with nobody in it should do.
     */
    private void refreshFactoryState(Level level, BlockPos pos) {
        validFactory = false;
        workers = 0;

        MinecraftServer server = level.getServer();
        if (server == null) {
            status = STATUS_NOT_A_FACTORY;
            return;
        }
        CityData data = CityData.get(server);
        Structure structure = data.structureAt(level.dimension(), pos);
        if (structure == null || structure.type() != StructureType.FACTORY) {
            status = STATUS_NOT_A_FACTORY;
            return;
        }
        City city = data.city(structure.cityId());
        if (city == null) {
            status = STATUS_NOT_A_FACTORY;
            return;
        }

        validFactory = true;

        int slots = structure.jobs();
        int cityJobs = city.jobs();
        if (slots <= 0 || cityJobs <= 0 || city.employed() <= 0) {
            status = STATUS_NO_WORKERS;
            return;
        }
        // Employment is tracked for the city as a whole, so split it between the workplaces in
        // proportion to how many jobs each one offers.
        workers = (int) ((long) city.employed() * slots / cityJobs);
        if (workers <= 0) {
            status = STATUS_NO_WORKERS;
        }
    }

    /**
     * Add items to the crate, stacking into part-full slots first.
     *
     * <p>Anything that will not fit is simply not made. A factory that has filled its crate stops
     * rather than voiding what it produced, so leaving one running while you are away costs nothing.
     */
    private boolean insert(ItemStack product, int amount) {
        int remaining = amount;
        for (int slot = 0; slot < STORAGE_SLOTS && remaining > 0; slot++) {
            ItemStack existing = items.get(slot);
            if (existing.isEmpty()) {
                int placed = Math.min(remaining, product.getMaxStackSize());
                ItemStack copy = product.copy();
                copy.setCount(placed);
                items.set(slot, copy);
                remaining -= placed;
            } else if (ItemStack.isSameItemSameComponents(existing, product)) {
                int room = existing.getMaxStackSize() - existing.getCount();
                int placed = Math.min(room, remaining);
                existing.grow(placed);
                remaining -= placed;
            }
        }
        return remaining < amount;
    }

    public int workers() {
        return workers;
    }

    public boolean validFactory() {
        return validFactory;
    }

    public int status() {
        return status;
    }

    // -------------------------------------------------------------- container

    @Override
    public int getContainerSize() {
        return TOTAL_SLOTS;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack removed = ContainerHelper.removeItem(items, slot, amount);
        if (!removed.isEmpty()) {
            setChanged();
        }
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        stack.limitSize(getMaxStackSize());
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        items.clear();
    }

    /**
     * Hoppers see the storage slots only.
     *
     * <p>The template must not be reachable from outside, or the first hopper pointed at the crate
     * would quietly steal the item that says what the factory makes and production would stop for no
     * visible reason.
     */
    @Override
    public int[] getSlotsForFace(Direction side) {
        int[] slots = new int[STORAGE_SLOTS];
        for (int i = 0; i < STORAGE_SLOTS; i++) {
            slots[i] = i;
        }
        return slots;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        // Output only. This is where goods come out, not a chest to dump things into.
        return false;
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot < STORAGE_SLOTS;
    }

    // ------------------------------------------------------------------- menu

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.citiesinlife.factory_output");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        // Production only runs every few seconds. Without this, opening the crate straight after
        // building it shows whatever the last run left behind - usually "not a factory" on a crate
        // that is perfectly fine.
        if (level != null) {
            refreshFactoryState(level, worldPosition);
        }
        return new FactoryOutputMenu(id, playerInventory, this, data);
    }

    // ------------------------------------------------------------ persistence

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items.clear();
        ContainerHelper.loadAllItems(tag, items, registries);
        workers = tag.getInt("workers");
        validFactory = tag.getBoolean("validFactory");
        status = tag.getInt("status");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putInt("workers", workers);
        tag.putBoolean("validFactory", validFactory);
        tag.putInt("status", status);
    }
}
