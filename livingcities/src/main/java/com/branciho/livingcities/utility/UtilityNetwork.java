package com.branciho.livingcities.utility;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * One connected utility network: everything reachable from a distributor through conductors.
 *
 * <p>Derived state, rebuilt from the world rather than saved. The blocks are the truth, and a saved
 * copy could only ever disagree with them after a chunk edit, a rollback, or another mod moving a block.
 */
public final class UtilityNetwork {

    private final UtilityKind kind;
    private final LongSet members = new LongOpenHashSet();
    private final List<BlockPos> distributors = new ArrayList<>();

    private int production;
    private int demand;
    private int throughput;

    public UtilityNetwork(UtilityKind kind) {
        this.kind = kind;
    }

    public UtilityKind kind() {
        return kind;
    }

    public LongSet members() {
        return members;
    }

    public List<BlockPos> distributors() {
        return distributors;
    }

    public void addMember(BlockPos pos) {
        members.add(pos.asLong());
    }

    public void addDistributor(BlockPos pos) {
        distributors.add(pos.immutable());
    }

    public void addProduction(int amount) {
        this.production += Math.max(0, amount);
    }

    public void addThroughput(int amount) {
        this.throughput += Math.max(0, amount);
    }

    public void addDemand(int amount) {
        this.demand += Math.max(0, amount);
    }

    public void resetDemand() {
        this.demand = 0;
    }

    public int production() {
        return production;
    }

    public int demand() {
        return demand;
    }

    public int throughput() {
        return throughput;
    }

    /**
     * What the network can actually deliver.
     *
     * <p>Production alone is not enough: it has to get there. A network with no transformers at all
     * delivers nothing, which is what makes a transformer a required part of a real grid rather than
     * an optional upgrade.
     */
    public int deliverable() {
        return Math.min(production, throughput);
    }

    /** 0..1. A network nobody draws from is satisfied rather than divided by zero. */
    public float satisfaction() {
        if (demand <= 0) {
            return 1.0F;
        }
        return Math.min(1.0F, deliverable() / (float) demand);
    }

    public boolean isOverloaded() {
        return demand > deliverable();
    }

    /** True when production exists but cannot be carried, which needs a different hint in the UI. */
    public boolean isThroughputLimited() {
        return production > throughput;
    }
}
