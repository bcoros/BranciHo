package com.branciho.citiesinlife.entity.ai;

import com.branciho.citiesinlife.city.City;
import com.branciho.citiesinlife.city.CityData;
import com.branciho.citiesinlife.city.Diplomacy;
import com.branciho.citiesinlife.city.Relation;
import com.branciho.citiesinlife.city.Warfare;
import com.branciho.citiesinlife.entity.ServiceEntity;
import com.branciho.citiesinlife.service.ServiceType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TntBlock;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * March on the next chunk, stand in it until it falls, then march on the next one.
 *
 * <p>The whole shape of a war in this mod. Ground is taken by having somebody standing on it, which
 * means an army that walks in and leaves takes nothing, and a defender who kills the occupier stops
 * the clock. Advancing to the chunk nearest whatever the attacker already holds keeps a front line
 * rather than letting one soldier teleport about the map picking off corners.
 *
 * <p>The actual taking is counted by the war director, once a second, for every soldier standing
 * where they should be. This goal is only the walking — and the demolition, because a soldier
 * standing in an enemy city with nothing to shoot at will set about the buildings.
 */
public class SoldierGoal extends Goal {

    /** How often to reconsider where the front is. */
    private static final int RETHINK_TICKS = 60;

    /** How often a soldier in enemy ground plants a charge, and how far from itself. */
    private static final int DEMOLITION_TICKS = 300;
    private static final int DEMOLITION_RANGE = 3;

    /** How close counts as being in position. */
    private static final double IN_POSITION = 6.0D;

    /** How long a charge sits there looking like a charge before it goes off. */
    private static final int FUSE_TICKS = 30;

    private final ServiceEntity soldier;
    private @Nullable BlockPos objective;
    private int rethink;
    private int demolition;

    /** A block of TNT this soldier has put down and is about to light. */
    private @Nullable BlockPos charge;
    private int fuse;

    public SoldierGoal(ServiceEntity soldier) {
        this.soldier = soldier;
        setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    /**
     * Marching is for the side that is attacking.
     *
     * <p>This used to ask {@code !mine.wars().isEmpty()}, which is not "am I at war" - it is "did I
     * declare one". {@link City#wars()} only ever holds entries for the city that declared, so for
     * the side that was declared on the answer was always no. Their soldiers never marched, never
     * picked an objective, and only fought when somebody walked into them. Half of every war stood
     * still, and the half that stood still was always the same half.
     */
    @Override
    public boolean canUse() {
        if (soldier.role() != ServiceType.MILITARY) {
            return false;
        }
        City mine = soldier.city();
        return mine != null && onTheOffensive(mine) && pickObjective(mine) != null;
    }

    @Override
    public boolean canContinueToUse() {
        City mine = soldier.city();
        return objective != null && mine != null && onTheOffensive(mine);
    }

    /** Whether this soldier's city is currently the attacker in any war it is part of. */
    private boolean onTheOffensive(City mine) {
        if (!(soldier.level() instanceof ServerLevel level)) {
            return false;
        }
        for (City enemy : CityData.get(level.getServer()).cities()) {
            if (!enemy.id().equals(mine.id())
                    && Warfare.attacking(level.getServer(), mine, enemy)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void stop() {
        objective = null;
        rethink = 0;
        soldier.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        City mine = soldier.city();
        if (mine == null) {
            return;
        }
        soldier.reportBusy();

        if (--rethink <= 0) {
            rethink = RETHINK_TICKS;
            objective = pickObjective(mine);
        }
        if (objective == null) {
            return;
        }

        double distance = soldier.distanceToSqr(
                objective.getX() + 0.5D, soldier.getY(), objective.getZ() + 0.5D);
        if (distance > IN_POSITION * IN_POSITION) {
            soldier.getNavigation().moveTo(
                    objective.getX() + 0.5D, objective.getY(), objective.getZ() + 0.5D, 1.0D);
            return;
        }

        // In position. Hold it — the director counts the ground taken — and start pulling the place
        // apart while waiting.
        soldier.getNavigation().stop();
        lightAnythingPlanted();
        if (++demolition >= DEMOLITION_TICKS) {
            demolition = 0;
            layCharge(mine);
        }
    }

    /**
     * Light the charge from the last round.
     *
     * <p>Planting and lighting are a moment apart on purpose. A block of TNT that appears and primes
     * in the same tick is a bang out of nowhere; one that sits there for a second and a half is
     * somebody demolishing a building, which is what is actually happening.
     */
    private void lightAnythingPlanted() {
        if (charge == null || !(soldier.level() instanceof ServerLevel level)) {
            return;
        }
        if (--fuse > 0) {
            return;
        }
        BlockPos spot = charge;
        charge = null;
        if (level.getBlockState(spot).is(Blocks.TNT)) {
            level.removeBlock(spot, false);
            TntBlock.explode(level, spot);
        }
    }

    /**
     * The nearest chunk of an enemy city, preferring ground that is already half taken.
     *
     * <p>Half-taken first is what makes an army finish what it started: without it, a soldier that
     * wandered would keep restarting sieges a chunk at a time and never finish one.
     */
    private @Nullable BlockPos pickObjective(City mine) {
        if (!(soldier.level() instanceof ServerLevel level)) {
            return null;
        }
        CityData data = CityData.get(level.getServer());
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;

        for (City enemy : data.cities()) {
            if (enemy.id().equals(mine.id()) || !enemy.dimension().equals(level.dimension())
                    || Diplomacy.stance(enemy, mine) != Relation.WAR) {
                continue;
            }
            // Only ground belonging to somebody we are currently advancing on. Being at war with
            // two cities while defending against one of them should not send the army marching
            // into that one anyway.
            if (!Warfare.attacking(level.getServer(), mine, enemy)) {
                continue;
            }
            for (long chunkKey : enemy.claimedChunks()) {
                ChunkPos chunk = new ChunkPos(chunkKey);
                BlockPos centre = new BlockPos(chunk.getMiddleBlockX(),
                        soldier.getBlockY(), chunk.getMiddleBlockZ());
                double score = soldier.distanceToSqr(
                        centre.getX() + 0.5D, soldier.getY(), centre.getZ() + 0.5D);
                CityData.Siege siege = data.siege(level.dimension(), chunkKey);
                if (siege != null && siege.attacker().equals(mine.id())) {
                    // Worth crossing the city for: this one is nearly ours.
                    score -= siege.progress() * 64.0D;
                }
                if (score < bestScore) {
                    bestScore = score;
                    best = centre;
                }
            }
        }
        return best;
    }

    /**
     * Plant a charge and light it.
     *
     * <p>Only where the city's own rules already allow it — war is the thing that makes an enemy's
     * blocks fair game, and this asks the same authority a player's pickaxe does rather than
     * inventing a second answer.
     */
    private void layCharge(City mine) {
        if (!(soldier.level() instanceof ServerLevel level)) {
            return;
        }
        BlockPos spot = soldier.blockPosition()
                .offset(level.random.nextInt(DEMOLITION_RANGE * 2 + 1) - DEMOLITION_RANGE, 0,
                        level.random.nextInt(DEMOLITION_RANGE * 2 + 1) - DEMOLITION_RANGE);
        City owner = Diplomacy.owner(level.getServer(), level.dimension(), spot);
        if (owner == null || owner.id().equals(mine.id())
                || Diplomacy.stance(owner, mine) != Relation.WAR) {
            return;
        }
        // Charges are an attacker's business. A defender blowing craters in the ground they are
        // trying to hold is not a defence, and it is the one thing that would make losing a phase
        // worse than losing the war.
        if (!Warfare.attacking(level.getServer(), mine, owner)) {
            return;
        }
        if (!level.getBlockState(spot).canBeReplaced()
                || !level.getBlockState(spot.below()).isFaceSturdy(level, spot.below(), Direction.UP)) {
            return;
        }
        level.setBlockAndUpdate(spot, Blocks.TNT.defaultBlockState());
        soldier.swing(soldier.getUsedItemHand());
        charge = spot;
        fuse = FUSE_TICKS;
    }
}
