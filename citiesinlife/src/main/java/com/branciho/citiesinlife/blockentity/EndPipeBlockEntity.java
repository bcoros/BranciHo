package com.branciho.citiesinlife.blockentity;

import com.branciho.citiesinlife.block.EndPipeBlock;
import com.branciho.citiesinlife.registry.ModBlockEntities;
import com.branciho.citiesinlife.registry.ModBlocks;
import com.branciho.citiesinlife.water.WaterGrid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The tap's insides: whether it is pouring, and what it is filling.
 *
 * <p>It only ever removes water it put there itself. A tap that tidied up whatever happened to be in
 * front of it would drain the lake it was standing in the moment its pump stopped.
 */
public class EndPipeBlockEntity extends BlockEntity {

    /** How often the pumps are asked. Half a second; the answer changes at the speed of building. */
    private static final int CHECK_INTERVAL = 10;

    /** Ticks per bucket. Five seconds is a steady trickle rather than a printing press. */
    private static final int BUCKET_INTERVAL = 100;

    /** How far the outlet link may reach. Enough to sit a tap over a chest, not across a room. */
    public static final int LINK_RANGE = 6;

    private @Nullable BlockPos outlet;
    private boolean pouring;
    private int supply;

    /**
     * Whether this tap is an outfall rather than a tap.
     *
     * <p>Decided by what is on the run, not by a setting: a sewage collector anywhere on the same
     * plumbing makes every end pipe on it an outfall. There is no way to have clean and dirty water
     * in one loop of pipe, which is exactly as true in a real town as it is here.
     */
    private boolean sewage;

    public EndPipeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.END_PIPE.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, EndPipeBlockEntity pipe) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (level.getGameTime() % CHECK_INTERVAL == 0L) {
            MinecraftServer server = serverLevel.getServer();
            WaterGrid grid = WaterGrid.get(server);
            pipe.supply = grid.supplyReaching(serverLevel, pos);

            boolean nowSewage = grid.sewageReaching(serverLevel, pos);
            if (nowSewage != pipe.sewage) {
                // Plumbing a sewer into a run that was pouring clean water has to take the clean
                // water away first, or the old block sits there for good with nothing left that
                // believes it put it there.
                pipe.stopPouring(level, state);
                pipe.sewage = nowSewage;
                pipe.setChanged();
            }

            // A sewer needs no pump. Water is pushed uphill from a river; sewage only has to leave,
            // so an outfall pours whenever there is a collector on the other end of it.
            if (pipe.supply > 0 || pipe.sewage) {
                pipe.startPouring(level, state);
            } else {
                pipe.stopPouring(level, state);
            }
        }

        // Buckets only from a clean run. Handing somebody a bucket of sewage and calling it water
        // would be a lie the boiler downstream has no way to catch.
        if (pipe.supply > 0 && !pipe.sewage && pipe.outlet != null
                && level.getGameTime() % BUCKET_INTERVAL == 0L) {
            pipe.fillSomething(level);
        }
    }

    // ---------------------------------------------------------------- pouring

    private void startPouring(Level level, BlockState state) {
        BlockPos spout = worldPosition.relative(state.getValue(EndPipeBlock.FACING));
        BlockState there = level.getBlockState(spout);
        BlockState poured = poured();
        if (there.is(poured.getBlock())) {
            pouring = true;
            return;
        }
        if (!there.isAir() && !there.canBeReplaced()) {
            // Aimed into a wall. Nothing to do about that but wait for somebody to move the wall.
            return;
        }
        level.setBlock(spout, poured, Block.UPDATE_ALL);
        pouring = true;
        setChanged();
    }

    /** What this tap puts in the world: water, or the brown stuff. */
    private BlockState poured() {
        return sewage
                ? ModBlocks.SEWAGE.get().defaultBlockState()
                : Blocks.WATER.defaultBlockState();
    }

    /**
     * Turn the tap off.
     *
     * <p>Only touches the cell in front, and only if it is still the source block this put there —
     * flowing water that has run off elsewhere is left alone and drains on its own, which is exactly
     * what turning a tap off looks like.
     */
    public void stopPouring(Level level, BlockState state) {
        if (!pouring) {
            return;
        }
        pouring = false;
        setChanged();

        Direction facing = state.hasProperty(EndPipeBlock.FACING)
                ? state.getValue(EndPipeBlock.FACING)
                : Direction.NORTH;
        BlockPos spout = worldPosition.relative(facing);
        BlockState there = level.getBlockState(spout);
        if (there.is(Blocks.WATER) || there.is(ModBlocks.SEWAGE.get())) {
            level.setBlock(spout, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    // ---------------------------------------------------------------- buckets

    private void fillSomething(Level level) {
        if (outlet == null || !level.isLoaded(outlet)) {
            return;
        }
        if (!(level.getBlockEntity(outlet) instanceof Container container)) {
            // Broken or replaced since it was linked.
            outlet = null;
            setChanged();
            return;
        }
        if (fill(container)) {
            container.setChanged();
        }
    }

    /**
     * Put water into whatever is on the other end.
     *
     * <p>An empty bucket already sitting there is filled first, and that ordering is the whole point
     * rather than a nicety: a coal boiler hands back an empty bucket when its water has condensed,
     * and this hands it straight back full. A plant plumbed into the mains never has to be visited
     * with a bucket again.
     */
    private static boolean fill(Container container) {
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack existing = container.getItem(slot);
            if (existing.getCount() == 1 && existing.is(Items.BUCKET)
                    && container.canPlaceItem(slot, new ItemStack(Items.WATER_BUCKET))) {
                container.setItem(slot, new ItemStack(Items.WATER_BUCKET));
                return true;
            }
        }
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack full = new ItemStack(Items.WATER_BUCKET);
            if (container.getItem(slot).isEmpty() && container.canPlaceItem(slot, full)) {
                container.setItem(slot, full);
                return true;
            }
        }
        return false;
    }

    // ----------------------------------------------------------------- outlet

    public @Nullable BlockPos outlet() {
        return outlet;
    }

    public void setOutlet(@Nullable BlockPos outlet) {
        this.outlet = outlet;
        setChanged();
    }

    /** One line: is there water, and is it filling anything. */
    public Component report() {
        if (sewage) {
            return Component.translatable("message.citiesinlife.tap_outfall");
        }
        if (supply <= 0) {
            return Component.translatable("message.citiesinlife.tap_dry");
        }
        if (outlet == null) {
            return Component.translatable("message.citiesinlife.tap_flowing", supply);
        }
        return Component.translatable("message.citiesinlife.tap_filling",
                outlet.getX(), outlet.getY(), outlet.getZ());
    }

    // ------------------------------------------------------------ persistence

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        outlet = tag.contains("outlet") ? BlockPos.of(tag.getLong("outlet")) : null;
        pouring = tag.getBoolean("pouring");
        sewage = tag.getBoolean("sewage");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (outlet != null) {
            tag.putLong("outlet", outlet.asLong());
        }
        tag.putBoolean("pouring", pouring);
        tag.putBoolean("sewage", sewage);
    }
}
