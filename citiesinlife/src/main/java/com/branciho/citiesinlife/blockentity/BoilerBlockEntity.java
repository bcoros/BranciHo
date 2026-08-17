package com.branciho.citiesinlife.blockentity;

import com.branciho.citiesinlife.block.BoilerBlock;
import com.branciho.citiesinlife.block.TurbineBlock;
import com.branciho.citiesinlife.plant.PlantSurvey;
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
 * <p>The rule that makes this a build rather than a block is that it only runs sealed in, with
 * somewhere for the smoke to go. Emissions leave whatever happens; the steam either leaves with them
 * and is wasted, or goes into a turbine and becomes electricity. Nothing here is plumbing you route —
 * it is a room you build correctly or do not.
 *
 * <p>There are two ways it finds the turbine and the flue. The good one is to draw a box round the
 * whole building with the Planner Wand and call it a Turbine Power Plant, after which the machinery
 * can be anywhere inside it. Failing that it falls back to looking straight up, which is how this
 * worked before and is why a shaft with a turbine on top still runs.
 *
 * <p>Water is a running cost, exactly like coal. A bucket is swallowed whole and the plant runs until
 * it is gone; there is no condenser handing it back. That is what makes plumbing a plant into the
 * water network worth the trouble — an End Pipe over the boiler keeps it fed, and without one
 * somebody has to walk out there with a bucket.
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

    /** Soot forced into the turbine each tick a boiler burns with no flue of its own. */
    private static final int SOOT_PER_TICK = 1;

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
    public static final int STATUS_NO_OUTLET = 3;
    public static final int STATUS_NO_WATER = 4;
    public static final int STATUS_NO_FUEL = 5;
    public static final int STATUS_FOULING = 6;
    public static final int STATUS_NO_TURBINE = 7;
    public static final int STATUS_MIXED_PLANT = 8;
    public static final int STATUS_TURBINE_CLOGGED = 9;

    /** Indices into the block of numbers the screen reads. */
    public static final int DATA_BURN_TIME = 0;
    public static final int DATA_BURN_DURATION = 1;
    public static final int DATA_WATER = 2;
    public static final int DATA_STEAM = 3;
    public static final int DATA_STATUS = 4;
    public static final int DATA_SIZE = 5;

    private final NonNullList<ItemStack> items = NonNullList.withSize(TOTAL_SLOTS, ItemStack.EMPTY);

    private int burnTime;
    private int burnDuration;
    private int water;
    private int steam;
    private int status = STATUS_NO_FUEL;

    /** Results of the last room survey, refreshed every {@link #SURVEY_INTERVAL} ticks. */
    private boolean chamberSealed;

    /** The turbine the steam goes into, if there is one to find. */
    private @Nullable BlockPos turbinePos;

    /**
     * Whether that turbine has already seized.
     *
     * <p>Once it has, there is nothing useful left for this boiler to do: the steam has nowhere to
     * go and the soot it would add is already at the limit. Burning on regardless meant a plant that
     * had visibly stopped earning quietly ate a stack of coal while the screen still said it was
     * fouling the turbine up - which it was not, because there was nothing left to foul.
     */
    private boolean turbineClogged;

    /** Where the smoke leaves: a chimney's mouth, or the top of an open shaft above the boiler. */
    private @Nullable BlockPos smokeOut;

    /** Whether this boiler stands in a registered plant, and whether that plant is coherent. */
    private boolean insidePlant;
    private boolean mixedPlant;

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

        // A boiler with no flue still burns. The emissions have to go somewhere, and if there is no
        // chimney and no shaft they go through the turbine and foul it - which is the whole reason
        // to build a chimney rather than a rule telling you to.
        boiler.turbineClogged = boiler.turbinePos != null
                && level.getBlockEntity(boiler.turbinePos) instanceof TurbineBlockEntity turbine
                && turbine.clogged();

        boolean canRun = boiler.chamberSealed && !boiler.mixedPlant && !boiler.turbineClogged
                && (boiler.smokeOut != null || boiler.turbinePos != null);
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
     * Take the water out of the bucket in the top slot. The bucket goes with it.
     *
     * <p>It used to leave the empty bucket behind and quietly refill it after a pause, which stood in
     * for a condenser back when there was no water system to plumb a plant into. There is one now, and
     * a boiler that tops itself up for free makes the whole of it pointless — the plant never stops,
     * so nothing you build for it ever matters. A boiler now runs until the water is gone and then
     * sits there, and getting more into it is either a walk with a bucket or an End Pipe.
     */
    private void tickWater() {
        ItemStack held = items.get(WATER_SLOT);
        if (held.is(Items.WATER_BUCKET) && water + BUCKET <= WATER_CAPACITY) {
            water += BUCKET;
            held.shrink(1);
            if (held.isEmpty()) {
                items.set(WATER_SLOT, ItemStack.EMPTY);
            }
            changed = true;
        }
    }

    /** Hand the steam to the turbine if one was found; otherwise let it escape with the smoke. */
    private void pushSteam(Level level) {
        if (steam <= 0) {
            return;
        }
        if (turbinePos != null
                && level.getBlockEntity(turbinePos) instanceof TurbineBlockEntity turbine) {
            int moved = turbine.accept(steam, TurbineBlockEntity.COAL_OUTPUT);
            if (moved > 0) {
                steam -= moved;
                changed = true;
            }
            return;
        }
        steam = Math.max(0, steam - VENT_LOSS);
        changed = true;
    }

    private void updateStatus(boolean canRun) {
        int previous = status;
        if (mixedPlant) {
            status = STATUS_MIXED_PLANT;
        } else if (!chamberSealed) {
            status = STATUS_NO_CHAMBER;
        } else if (smokeOut == null && turbinePos == null) {
            status = STATUS_NO_OUTLET;
        } else if (insidePlant && turbinePos == null) {
            status = STATUS_NO_TURBINE;
        } else if (turbineClogged) {
            status = STATUS_TURBINE_CLOGGED;
        } else if (water < WATER_PER_TICK) {
            status = STATUS_NO_WATER;
        } else if (burnTime <= 0) {
            status = STATUS_NO_FUEL;
        } else if (canRun && turbinePos != null) {
            // Running, but say so differently when the smoke is going through the machine.
            status = smokeOut == null ? STATUS_FOULING : STATUS_RUNNING_TURBINE;
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
        if (!burning) {
            return;
        }
        if (smokeOut == null) {
            // No flue at all. Everything the firebox makes goes into the turbine.
            if (turbinePos != null
                    && level.getBlockEntity(turbinePos) instanceof TurbineBlockEntity turbine) {
                turbine.foul(SOOT_PER_TICK);
            }
            return;
        }
        if (ticksRun % 8 != 0 || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        double x = smokeOut.getX() + 0.5D;
        double y = smokeOut.getY() + 0.9D;
        double z = smokeOut.getZ() + 0.5D;
        serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, x, y, z, 3, 0.12D, 0.0D, 0.12D, 0.02D);
        if (turbinePos == null && steam > 0) {
            // Steam nobody is catching, going up the flue with the smoke.
            serverLevel.sendParticles(ParticleTypes.CLOUD, x, y, z, 4, 0.2D, 0.0D, 0.2D, 0.05D);
        }
    }

    // ----------------------------------------------------------------- survey

    /** Work out where the steam and the smoke go, and whether the room holds the fire in. */
    private void survey(Level level, BlockPos pos) {
        turbinePos = null;
        smokeOut = null;
        insidePlant = false;
        mixedPlant = false;

        surveyPlant(level, pos);
        if (!insidePlant) {
            // Nothing registered. Fall back to the original rule: a shaft straight up, with or
            // without a turbine capping it.
            surveyShaft(level, pos);
        }
        // Inside a registered plant a chimney is the only flue there is. There used to be a fallback
        // here that accepted open air above the boiler instead - which quietly made the whole
        // chimney rule dead, because a boiler is REQUIRED to have a hole above it to seal its
        // chamber at all. Every plant therefore had a "flue", nothing ever fouled, and a player
        // could run a chimney-less plant indefinitely with no consequence whatsoever.
        chamberSealed = surveyChamber(level, pos);
    }

    /**
     * Look inside the Turbine Power Plant this boiler stands in.
     *
     * <p>This is the reason that structure type exists. Requiring the turbine to sit in the exact
     * column above the boiler is a rule that is easy to write and unpleasant to build to — you end up
     * arranging a power station around one invisible line. Drawing a box round the building and
     * saying "the machinery is in here" is the same information and none of the fuss.
     */
    private void surveyPlant(Level level, BlockPos pos) {
        PlantSurvey plant = PlantSurvey.at(level, pos);
        if (!plant.registered()) {
            return;
        }
        insidePlant = true;
        smokeOut = plant.chimney();

        // One kind of generator per plant. A boiler and a windmill in the same box is not a hybrid,
        // it is two plants somebody forgot to draw separately, and guessing which one wins would be
        // worse than refusing.
        if (plant.kind() == PlantSurvey.Kind.MIXED) {
            mixedPlant = true;
            return;
        }
        turbinePos = plant.turbineFor(pos);
    }

    /** The original rule: look straight up for a turbine, or for open air to vent through. */
    private void surveyShaft(Level level, BlockPos pos) {
        BlockPos highestOpen = null;
        for (int up = 1; up <= VENT_REACH; up++) {
            BlockPos above = pos.above(up);
            BlockState state = level.getBlockState(above);
            if (state.getBlock() instanceof TurbineBlock) {
                if (turbinePos == null) {
                    turbinePos = above;
                }
                // A turbine caps the shaft, so its own stack is where the smoke comes out.
                if (smokeOut == null) {
                    smokeOut = above.above();
                }
                return;
            }
            if (state.blocksMotion()) {
                // A ceiling with no hole in it, and no chimney elsewhere. The boiler will not light.
                return;
            }
            highestOpen = above;
        }
        if (smokeOut == null) {
            smokeOut = highestOpen;
        }
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
            // Full buckets only. There is nothing useful an empty one could do in here now that the
            // boiler no longer hands them back.
            return stack.is(Items.WATER_BUCKET);
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
            updateStatus(chamberSealed && smokeOut != null);
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
        tag.putInt("status", status);
    }
}
