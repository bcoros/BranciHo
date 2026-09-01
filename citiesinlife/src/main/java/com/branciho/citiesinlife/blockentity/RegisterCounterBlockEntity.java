package com.branciho.citiesinlife.blockentity;

import com.branciho.citiesinlife.block.RegisterCounterBlock;
import com.branciho.citiesinlife.city.City;
import com.branciho.citiesinlife.city.CityData;
import com.branciho.citiesinlife.menu.RegisterCounterMenu;
import com.branciho.citiesinlife.registry.ModBlockEntities;
import com.branciho.citiesinlife.structure.Structure;
import com.branciho.citiesinlife.structure.StructureType;
import com.branciho.citiesinlife.work.Workplace;
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
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The till's insides: what is for sale, what it costs, and who is behind the counter.
 *
 * <p>Demand falls off in a straight line as the price climbs, so takings are price times customers
 * and that peaks in the middle. Give it away and thousands of people buy something worth nothing;
 * charge the earth and nobody comes. Halfway is where a shop makes money, and it is a curve the
 * player can feel their way along without being told the formula.
 *
 * <p>Takings scale with the city's population because a till in a village and a till downtown are
 * not the same business, and with how many staff turned up because a queue moves twice as fast with
 * two people on it.
 */
public class RegisterCounterBlockEntity extends BlockEntity implements Container, MenuProvider, Workplace {

    /** The player asked for two workers per till, and two is also what the counter has room for. */
    public static final int CAPACITY = 2;

    private static final long CLAIM_TIMEOUT = 200L;

    /** One slot: the thing on display. Like the factory template, it is a choice, not stock. */
    public static final int SLOTS = 1;

    public static final int MIN_PRICE = 1;
    public static final int MAX_PRICE = 100;
    public static final int DEFAULT_PRICE = 30;

    /** How often takings are counted. Matched to the growth tick so money arrives in step. */
    public static final int SALES_INTERVAL = 200;

    /** Customers a fully staffed till can serve at give-it-away prices, before population scaling. */
    private static final int BASE_CUSTOMERS = 6;

    /** Extra customers per this many residents, so a big city is worth opening a shop in. */
    private static final int RESIDENTS_PER_EXTRA_CUSTOMER = 250;
    private static final int MAX_CUSTOMERS = 60;

    public static final int DATA_PRICE = 0;
    public static final int DATA_WORKERS = 1;
    public static final int DATA_CUSTOMERS = 2;
    public static final int DATA_TAKINGS = 3;
    public static final int DATA_STATUS = 4;
    public static final int DATA_SIZE = 5;

    /** What the till is doing, for the screen to explain in words rather than in zeroes. */
    public static final int STATUS_TRADING = 0;
    public static final int STATUS_NOT_A_SHOP = 1;
    public static final int STATUS_NO_STAFF = 2;
    public static final int STATUS_NO_STOCK = 3;

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);

    /** Insertion-ordered, because which worker stands where is decided by who arrived first. */
    private final Map<UUID, Long> workers = new LinkedHashMap<>();

    private int price = DEFAULT_PRICE;
    private int lastCustomers;
    private int lastTakings;
    private int status = STATUS_NO_STAFF;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_PRICE -> price;
                case DATA_WORKERS -> staffed();
                case DATA_CUSTOMERS -> lastCustomers;
                case DATA_TAKINGS -> lastTakings;
                default -> status;
            };
        }

        @Override
        public void set(int index, int value) {
            // The only thing the screen may change is the price, and it does it through here: a
            // button click arrives on the server, writes this, and the new value syncs straight back.
            if (index == DATA_PRICE) {
                setPrice(value);
            }
        }

        @Override
        public int getCount() {
            return DATA_SIZE;
        }
    };

    public RegisterCounterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.REGISTER_COUNTER.get(), pos, state);
    }

    // ---------------------------------------------------------------- trading

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  RegisterCounterBlockEntity till) {
        if (level.getGameTime() % 40L == 0L) {
            till.expire(level.getGameTime());
        }
        if (level.getGameTime() % SALES_INTERVAL != 0L) {
            return;
        }
        till.trade(level);
    }

    private void trade(Level level) {
        MinecraftServer server = level.getServer();
        if (server == null) {
            return;
        }
        lastCustomers = 0;
        lastTakings = 0;

        CityData cityData = CityData.get(server);
        Structure structure = cityData.structureAt(level.dimension(), worldPosition);
        if (structure == null || structure.type() != StructureType.COMMERCIAL) {
            status = STATUS_NOT_A_SHOP;
            return;
        }
        City city = cityData.city(structure.cityId());
        if (city == null) {
            status = STATUS_NOT_A_SHOP;
            return;
        }
        int staff = staffed();
        if (staff <= 0) {
            // The player was explicit about this: no staff, no trade. A shop is people, not a box.
            status = STATUS_NO_STAFF;
            return;
        }
        if (items.get(0).isEmpty()) {
            status = STATUS_NO_STOCK;
            return;
        }

        status = STATUS_TRADING;
        lastCustomers = customersAt(price, staff, city.population());
        lastTakings = lastCustomers * price;
        if (lastTakings > 0) {
            city.deposit(lastTakings);
            cityData.setDirty();
        }
        setChanged();
    }

    /**
     * How many people buy at this price.
     *
     * <p>Linear demand, which is the simplest shape that has a real decision in it: takings are
     * price times customers, so they peak at the middle of the range and fall away either side.
     */
    public static int customersAt(int price, int staff, int population) {
        double demand = 1.0D - (price - MIN_PRICE) / (double) (MAX_PRICE - MIN_PRICE);
        demand = Math.max(0.0D, Math.min(1.0D, demand));

        int reach = BASE_CUSTOMERS + population / RESIDENTS_PER_EXTRA_CUSTOMER;
        reach = Math.min(MAX_CUSTOMERS, reach);
        // Half the counter staffed is half the queue served.
        double served = reach * demand * (staff / (double) CAPACITY);
        return (int) Math.floor(served);
    }

    public int price() {
        return price;
    }

    public void setPrice(int newPrice) {
        int clamped = Math.max(MIN_PRICE, Math.min(MAX_PRICE, newPrice));
        if (clamped != price) {
            price = clamped;
            setChanged();
        }
    }

    // -------------------------------------------------------------- workplace

    private void expire(long gameTime) {
        workers.entrySet().removeIf(entry -> gameTime - entry.getValue() > CLAIM_TIMEOUT);
    }

    @Override
    public int capacity() {
        return CAPACITY;
    }

    @Override
    public boolean checkIn(UUID worker, long gameTime) {
        expire(gameTime);
        if (!workers.containsKey(worker) && workers.size() >= CAPACITY) {
            return false;
        }
        workers.put(worker, gameTime);
        return true;
    }

    @Override
    public boolean employs(UUID worker) {
        return workers.containsKey(worker);
    }

    @Override
    public int staffed() {
        if (level != null) {
            expire(level.getGameTime());
        }
        return workers.size();
    }

    /**
     * Where this one stands.
     *
     * <p>Both behind the counter, side by side. Two people sharing one square would read as one
     * person with a rendering fault, so the second takes the space beside the first.
     */
    @Override
    public BlockPos spotFor(UUID worker) {
        Direction facing = getBlockState().hasProperty(RegisterCounterBlock.FACING)
                ? getBlockState().getValue(RegisterCounterBlock.FACING)
                : Direction.NORTH;
        BlockPos behind = worldPosition.relative(facing.getOpposite());

        List<UUID> ordered = new ArrayList<>(workers.keySet());
        int index = ordered.indexOf(worker);
        return index <= 0 ? behind : behind.relative(facing.getClockWise());
    }

    @Override
    public boolean seated() {
        return false;
    }

    // --------------------------------------------------------------- container

    public ContainerData containerData() {
        return data;
    }

    @Override
    public int getContainerSize() {
        return SLOTS;
    }

    @Override
    public boolean isEmpty() {
        return items.get(0).isEmpty();
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
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        items.clear();
        setChanged();
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.citiesinlife.register_counter");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new RegisterCounterMenu(id, inventory, this, data);
    }

    // ------------------------------------------------------------ persistence

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items.clear();
        ContainerHelper.loadAllItems(tag, items, registries);
        price = tag.contains("price") ? tag.getInt("price") : DEFAULT_PRICE;
        lastCustomers = tag.getInt("customers");
        lastTakings = tag.getInt("takings");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putInt("price", price);
        tag.putInt("customers", lastCustomers);
        tag.putInt("takings", lastTakings);
    }
}
