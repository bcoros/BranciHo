package com.branciho.livingcities.power;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * One electrically connected grid: everything reachable from a substation through conductors.
 *
 * <p>A network is derived state, rebuilt from the world rather than saved. That is deliberate - the
 * blocks are the truth, and a saved copy could only ever disagree with them after a chunk edit,
 * a rollback, or another mod moving a block.
 */
public final class PowerNetwork {

    private final LongSet members = new LongOpenHashSet();
    private final List<BlockPos> substations = new ArrayList<>();

    private int generationKw;
    private int demandKw;

    public LongSet members() {
        return members;
    }

    public List<BlockPos> substations() {
        return substations;
    }

    public void addMember(BlockPos pos) {
        members.add(pos.asLong());
    }

    public boolean contains(BlockPos pos) {
        return members.contains(pos.asLong());
    }

    public void addSubstation(BlockPos pos) {
        substations.add(pos.immutable());
    }

    public int generationKw() {
        return generationKw;
    }

    public void addGeneration(int kw) {
        this.generationKw += Math.max(0, kw);
    }

    public int demandKw() {
        return demandKw;
    }

    public void addDemand(int kw) {
        this.demandKw += Math.max(0, kw);
    }

    public void resetDemand() {
        this.demandKw = 0;
    }

    /**
     * How much of this grid's demand is actually met, 0..1.
     *
     * <p>A grid with no demand is fully satisfied rather than divided by zero, and surplus generation
     * does not push this above 1 - spare capacity is headroom, not a bonus.
     */
    public float satisfaction() {
        if (demandKw <= 0) {
            return 1.0F;
        }
        return Math.min(1.0F, generationKw / (float) demandKw);
    }

    public boolean isOverloaded() {
        return demandKw > generationKw;
    }
}
