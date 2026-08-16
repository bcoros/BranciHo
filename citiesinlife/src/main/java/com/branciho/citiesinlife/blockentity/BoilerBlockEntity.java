package com.branciho.citiesinlife.blockentity;

import com.branciho.citiesinlife.block.BoilerBlock;
import com.branciho.citiesinlife.block.TurbineBlock;
import com.branciho.citiesinlife.menu.BoilerMenu;
import com.branciho.citiesinlife.registry.ModBlockEntities;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The boiler's insides: a firebox, a water tank, and the survey of the room it stands in.
 *
 * <p>The rule that makes this a build rather than a block is that it only runs inside a sealed
 * chamber with a single opening directly above it. That opening is the whole design: emissions leave
 * through it whatever happens, and steam either leaves through it and is wasted or goes into a
 * turbine sitting on it and becomes electricity. Nothing here is plumbing you route — it is a room
 * you build correctly or do not.
 *
 * <p>Water is not consumed in the long run. The empty bucket left behind fills itself back up after a
 * while, which stands in for the condenser a real plant would need. Coal is the running cost; water
 * is a delay.
 */
public class BoilerBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider {

    public static final int WATER_SLOT = 0;
    public static final int FUEL_SLOT = 1;
    public static final int TOTAL_SLOTS = 2;

    /** Millibuckets the tank holds, and what one bucket puts in it. */
    public static final int WATER_CAPACITY = 2000;
    private static final int BUCKET = 1000;

    /** Water boiled and steam raised per tick while the fire is going. */
    private static final int WATER_PER_TICK = 1;
    private static final int STEAM_PER_TICK = 1;

    /** How much steam the boiler can hold before the rest escapes. */
    public static final int STEAM_CAPACITY = 200;

    /** Steam lost per tick when there is no turbine to take it. */
    private static final int VENT_LOSS = 2;

    /** Ticks an empty bucket sits in the slot before the condensate refills it. */
    private static final int CONDENSE_TICKS = 300;

    /** How often the room is re-surveyed. Every two seconds; it is a flood fill, not a lookup. */
    private static final int SURVEY_INTERVAL = 40;

    /**
     * Biggest room that still counts as a chamber, and how far up the vent may run.
     *
     * <p>Five hundred is a room about eight blocks on a side, which is a generous boiler house. The
     * ceiling matters because an unsealed boiler makes the fill run until it hits this number, and it
     * does that every couple of seconds forever.
     */
    private static final int MAX_CHAMBER = 512;
    private static final int VENT_REACH = 5;

    /** What the boiler is doing, for the screen to explain. */
    public static final int STATUS_RUNNING_TURBINE = 0;
    public static final int STATUS_RUNNING_VENTING = 1;
    public static final int STATUS_NO_CHAMBER = 2;
    public static final int STATUS_VENT_BLOCKED = 3;
    public static final int STATUS_NO_WATER = 4;
    public static final int STATUS_NO_FUEL = 5;

    /** Indices into the block of numbers the screen reads. */
    public static final int DATA_BURN_TIME = 0;
    public static final int DATA_BURN_DURATION = 1;
    public static final int DATA_WATER = 2;
    public static final int DATA_STEAM = 3;
    public static final int DATA_STATUS = 4;
    public static final int DATA_SIZE = 5;

    /** Where the steam goes once it leaves the boiler. */
    private enum Vent {
        /** Sealed above: the boiler will not run, because the smoke has nowhere to go. */
        BLOCKED,
        /** An opening to the sky. Emissions and steam both escape and the steam is wasted. */
        OPEN,
        /** A turbine sits on the opening. The steam goes into it. */
        TURBINE
    }

    private final NonNullList<ItemStack> items = NonNullList.withSize(TOTAL_SLOTS, ItemStack.EMPTY);

    private int burnTime;
    private int burnDuration;
    private int water;
    private int steam;
    private int refillTimer;
    private int status = STATUS_NO_FUEL;

    /** Results of the last room survey, refreshed every {@link #SURVEY_INTERVAL} ticks. */
    private boolean chamberSealed;
    private Vent vent = Vent.BLOCKED;
    private @Nullable BlockPos turbinePos;
    private @Nullable BlockPos ventOut;

    private int ticksRun;
    private boolean changed;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_BURN_TIME -> burnTime;
                case DATA_BURN_DURATION -> burnDuration;
                case DATA_WATER -> water;
                case DATA_STEAM -> steam;
                default -> status;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case DATA_BURN_TIME -> burnTime = value;
                case DATA_BURN_DURATION -> burnDuration = value;
                case DATA_WATER -> water = value;
                case DATA_STEAM -> steam = value;
                default -> status = value;
            }
        }

        @Override
        public int getCount() {
            return DATA_SIZE;
        }
    };

    public BoilerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BOILER.get(), pos, state);
    }

    // ---------------------------------------------------------------- running

    public static void serverTick(Level level, BlockPos pos, BlockState state, BoilerBlockEntity boiler) {
        if (boiler.ticksRun++ % SURVEY_INTERVAL == 0) {
            boiler.survey(level, pos);
        }
        boiler.tickWater();

        boolean canRun = boiler.chamberSealed && boiler.vent != Vent.BLOCKED;
        if (canRun && boiler.burnTime <= 0 && boiler.water >= WATER_PER_TICK) {
            boiler.lightFire();
        }

        boolean burning = false;
        if (canRun && boiler.burnTime > 0 && boiler.water >= WATER_PER_TICK) {
            // The fire only advances while there is water to boil, so running dry parks the coal
            // rather than burning it away for nothing.
            boiler.burnTime--;
            boiler.water -= WATER_PER_TICK;
            boiler.steam = Math.min(STEAM_CAPACITY, boiler.steam + STEAM_PER_TICK);
            burning = true;
            boiler.changed = true;
        }

        boiler.pushSteam(level);
        boiler.updateStatus(canRun);
        boiler.emit(level, burning);

        if (state.getValue(BoilerBlock.LIT) != burning) {
            level.setBlock(pos, state.setValue(BoilerBlock.LIT, burning), Block.UPDATE_ALL);
        }
        if (boiler.changed) {
            boiler.changed = false;
            boiler.setChanged();
        }
    }

    /** Take one piece of fuel out of the fuel slot and start it burning. */
    private void lightFire() {
        ItemStack fuel = items.get(FUEL_SLOT);
        if (fuel.isEmpty()) {
            return;
        }
        int duration = fuel.getBurnTime(RecipeType.SMELTING);
        if (duration <= 0) {
            return;
        }
        burnTime = duration;
        burnDuration = duration;

        // A lava bucket is the one fuel that hands a container back. Eating it silently would be a
        // nasty way to lose a bucket, so it stays in the slot the fuel came out of.
        boolean leavesBucket = fuel.is(Items.LAVA_BUCKET);
        fuel.shrink(1);
        if (fuel.isEmpty() && leavesBucket) {
            items.set(FUEL_SLOT, new ItemStack(Items.BUCKET));
        }
        changed = true;
    }

    /**
     * Move water out of the bucket in the top slot, and put it back once it has condensed.
     *
     * <p>This is the whole water system for now: one bucket, refilled for free after a pause. It is
     * not free time — the boiler stalls while the bucket is empty, which is what makes a real water
     * supply worth building later.
     */
    private void tickWater() {
        ItemStack held = items.get(WATER_SLOT);
        if (held.is(Items.WATER_BUCKET) && water + BUCKET <= WATER_CAPACITY) {
            water += BUCKET;
            items.set(WATER_SLOT, new ItemStack(Items.BUCKET));
            refillTimer = CONDENSE_TICKS;
            changed = true;
        } else if (held.is(Items.BUCKET)) {
            if (refillTimer > 0) {
                refillTimer--;
            } else {
                items.set(WATER_SLOT, new ItemStack(Items.WATER_BUCKET));
                changed = true;
            }
        }
    }

    /** Hand the steam to a turbine if one is sitting on the vent; otherwise let it escape. */
    private void pushSteam(Level level) {
        if (steam <= 0) {
            return;
        }
        if (vent == Vent.TURBINE && turbinePos != null
                && level.getBlockEntity(turbinePos) instanceof TurbineBlockEntity turbine) {
            int moved = turbine.acceptSteam(steam);
            if (moved > 0) {
                steam -= moved;
                changed = true;
            }
        }
        if (steam > 0 && vent != Vent.TURBINE) {
            steam = Math.max(0, steam - VENT_LOSS);
            changed = true;
        }
    }

    private void updateStatus(boolean canRun) {
        int previous = status;
        if (!chamberSealed) {
            status = STATUS_NO_CHAMBER;
        } else if (vent == Vent.BLOCKED) {
            status = STATUS_VENT_BLOCKED;
        } else if (water < WATER_PER_TICK) {
            status = STATUS_NO_WATER;
        } else if (burnTime <= 0) {
            status = STATUS_NO_FUEL;
        } else if (canRun && vent == Vent.TURBINE) {
            status = STATUS_RUNNING_TURBINE;
        } else {
            status = STATUS_RUNNING_VENTING;
        }
        if (status != previous) {
            changed = true;
        }
    }

    /**
     * Smoke out of the opening, and a plume of steam with it when nothing is catching the steam.
     *
     * <p>Sent from the server so every player sees the same chimney, which matters because the
     * emissions are the one part of the plant that is visible from outside the building.
     */
    private void emit(Level level, boolean burning) {
        if (!burning || ticksRun % 8 != 0 || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        BlockPos out = vent == Vent.TURBINE && turbinePos != null ? turbinePos.above(2) : ventOut;
        if (out == null) {
            return;
        }
        double x = out.getX() + 0.5D;
        double y = out.getY() + 0.5D;
        double z = out.getZ() + 0.5D;
        serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, x, y, z, 3, 0.12D, 0.0D, 0.12D, 0.02D);
        if (vent == Vent.OPEN && steam > 0) {
            serverLevel.sendParticles(ParticleTypes.CLOUD, x, y, z, 4, 0.2D, 0.0D, 0.2D, 0.05D);
        }
    }

    // ----------------------------------------------------------------- survey

    /** Work out where the steam goes and whether the room around the boiler holds it in. */
    private void survey(Level level, BlockPos pos) {
        surveyVent(level, pos);
        chamberSealed = surveyChamber(level, pos);
    }

    private void surveyVent(Level level, BlockPos pos) {
        vent = Vent.BLOCKED;
        turbinePos = null;
        ventOut = null;

        for (int up = 1; up <= VENT_REACH; up++) {
            BlockPos above = pos.above(up);
            BlockState state = level.getBlockState(above);
            if (state.getBlock() instanceof TurbineBlock) {
                vent = Vent.TURBINE;
                turbinePos = above;
                return;
            }
            if (state.blocksMotion()) {
                // A ceiling with no hole in it. The boiler will not light.
                return;
            }
            ventOut = above;
        }
        vent = Vent.OPEN;
    }

    /**
     * Flood the air around the boiler and see whether it stays put.
     *
     * <p>The column directly overhead is left out of the fill, because that column <em>is</em> the
     * opening. Any other gap and the fill runs off into the world and the room does not count — which
     * is precisely the rule "sealed, with one hole above it" written as code.
     */
    private boolean surveyChamber(Level level, BlockPos pos) {
        final LongOpenHashSet visited = new LongOpenHashSet();
        final LongArrayList queue = new LongArrayList();
        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (Direction direction : Direction.values()) {
            BlockPos start = pos.relative(direction);
            if (!isVentColumn(pos, start) && !level.getBlockState(start).blocksMotion()
                    && visited.add(start.asLong())) {
                queue.add(start.asLong());
            }
        }

        while (!queue.isEmpty()) {
            if (visited.size() > MAX_CHAMBER) {
                return false;
            }
            long current = queue.removeLong(queue.size() - 1);
            cursor.set(BlockPos.getX(current), BlockPos.getY(current), BlockPos.getZ(current));

            for (Direction direction : Direction.values()) {
                BlockPos next = cursor.relative(direction);
                if (next.equals(pos) || isVentColumn(pos, next)) {
                    continue;
                }
                if (level.getBlockState(next).blocksMotion()) {
                    continue;
                }
                if (visited.add(next.asLong())) {
                    queue.add(next.asLong());
                }
            }
        }
        return true;
    }

    /** Everything straight above the boiler, which is the one way out the fill may not use. */
    private static boolean isVentColumn(BlockPos boiler, BlockPos pos) {
        return pos.getX() == boiler.getX() && pos.getZ() == boiler.getZ() && pos.getY() > boiler.getY();
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
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot == WATER_SLOT) {
            return stack.is(Items.WATER_BUCKET) || stack.is(Items.BUCKET);
        }
        return stack.getBurnTime(RecipeType.SMELTING) > 0;
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        items.clear();
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        // Coal from the sides or below, water from above: the same shape as a furnace, so an existing
        // hopper habit works here without being taught anything.
        return side == Direction.UP ? new int[]{WATER_SLOT} : new int[]{FUEL_SLOT};
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        // Nothing is pulled out automatically. A hopper under the boiler would otherwise walk off
        // with the bucket the moment it finished condensing, and the plant would stop for no reason
        // the player could see.
        return false;
    }

    // ------------------------------------------------------------------- menu

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.citiesinlife.boiler");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        if (level != null) {
            survey(level, worldPosition);
            updateStatus(chamberSealed && vent != Vent.BLOCKED);
        }
        return new BoilerMenu(id, playerInventory, this, data);
    }

    // ------------------------------------------------------------ persistence

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items.clear();
        ContainerHelper.loadAllItems(tag, items, registries);
        burnTime = tag.getInt("burnTime");
        burnDuration = tag.getInt("burnDuration");
        water = tag.getInt("water");
        steam = tag.getInt("steam");
        refillTimer = tag.getInt("refill");
        status = tag.getInt("status");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putInt("burnTime", burnTime);
        tag.putInt("burnDuration", burnDuration);
        tag.putInt("water", water);
        tag.putInt("steam", steam);
        tag.putInt("refill", refillTimer);
        tag.putInt("status", status);
    }
}
