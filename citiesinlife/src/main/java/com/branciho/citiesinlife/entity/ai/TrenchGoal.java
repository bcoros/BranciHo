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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * Dig in and hold.
 *
 * <p>What a soldier does while their city is the one being advanced on. The other half of the war
 * is {@link SoldierGoal}, and only one of them runs at a time: you are either marching on their
 * ground or dug into your own, and which one is decided by {@link Warfare}.
 *
 * <p>The trench is dug on the defender's own land, on the side of it the enemy is coming from, and
 * through natural ground only. That last part is not politeness - a defence that chewed a hole
 * through the city it was defending would be worse for its owner than losing the phase.
 */
public class TrenchGoal extends Goal {

    /** How long the line is, in blocks. Long enough to be a position rather than a hole. */
    private static final int LENGTH = 9;

    /** How deep the middle gets. The ends stay one shallower, and that is the way out. */
    private static final int DEPTH = 2;

    /** One block every this many ticks, so a trench appears over about ten seconds. */
    private static final int DIG_TICKS = 6;

    private static final int RETHINK_TICKS = 200;
    private static final double IN_POSITION = 3.0D;

    /**
     * What a soldier is willing to dig through.
     *
     * <p>An allowlist rather than a blocklist, and deliberately short. Anything not on it is
     * somebody's building until proven otherwise, and the cost of being wrong in that direction is
     * a hole in a player's wall that nobody asked for.
     */
    private static boolean diggable(BlockState state) {
        return state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT)
                || state.is(Blocks.COARSE_DIRT) || state.is(Blocks.ROOTED_DIRT)
                || state.is(Blocks.PODZOL) || state.is(Blocks.MYCELIUM)
                || state.is(Blocks.SAND) || state.is(Blocks.RED_SAND)
                || state.is(Blocks.GRAVEL) || state.is(Blocks.STONE)
                || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.SNOW)
                || state.is(Blocks.CLAY) || state.is(Blocks.SOUL_SAND);
    }

    private final ServiceEntity soldier;

    private @Nullable BlockPos anchor;
    private Direction along = Direction.NORTH;
    private int dug;
    private int digTimer;
    private int rethink;

    public TrenchGoal(ServiceEntity soldier) {
        this.soldier = soldier;
        setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (soldier.role() != ServiceType.MILITARY) {
            return false;
        }
        City mine = soldier.city();
        return mine != null && defending(mine);
    }

    @Override
    public boolean canContinueToUse() {
        City mine = soldier.city();
        return mine != null && defending(mine);
    }

    /** At war, and not the one advancing. */
    private boolean defending(City mine) {
        if (!(soldier.level() instanceof ServerLevel level)) {
            return false;
        }
        boolean atWar = false;
        for (City enemy : CityData.get(level.getServer()).cities()) {
            if (enemy.id().equals(mine.id())
                    || Diplomacy.stance(mine, enemy) != Relation.WAR) {
                continue;
            }
            atWar = true;
            if (Warfare.attacking(level.getServer(), mine, enemy)) {
                return false;
            }
        }
        return atWar;
    }

    @Override
    public void start() {
        anchor = null;
        dug = 0;
        rethink = 0;
    }

    @Override
    public void stop() {
        anchor = null;
        soldier.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        City mine = soldier.city();
        if (mine == null || !(soldier.level() instanceof ServerLevel level)) {
            return;
        }
        soldier.reportBusy();

        if (anchor == null || --rethink <= 0) {
            rethink = RETHINK_TICKS;
            if (anchor == null) {
                anchor = chooseLine(level, mine);
                dug = 0;
            }
        }
        if (anchor == null) {
            return;
        }

        BlockPos head = anchor.relative(along, Math.min(dug, LENGTH - 1));
        if (soldier.distanceToSqr(head.getX() + 0.5D, soldier.getY(), head.getZ() + 0.5D)
                > IN_POSITION * IN_POSITION) {
            soldier.getNavigation().moveTo(
                    head.getX() + 0.5D, head.getY(), head.getZ() + 0.5D, 1.0D);
            return;
        }

        soldier.getNavigation().stop();
        if (dug >= LENGTH) {
            // Dug in. Look the way they will come from, and let the shooting goals do the rest.
            soldier.getLookControl().setLookAt(
                    soldier.getX() + along.getClockWise().getStepX() * 8.0D,
                    soldier.getEyeY(),
                    soldier.getZ() + along.getClockWise().getStepZ() * 8.0D);
            return;
        }
        if (++digTimer < DIG_TICKS) {
            return;
        }
        digTimer = 0;
        digAt(level, anchor.relative(along, dug), dug == 0 || dug == LENGTH - 1);
        dug++;
    }

    /**
     * One block of the line.
     *
     * <p>The two ends are dug one shallower than the middle, and that is the whole of what stops a
     * trench being a grave: every step out of it is a single block, which is a step a soldier can
     * simply walk up. A flat-bottomed hole two deep would hold its own defenders in it for the rest
     * of the war.
     */
    private void digAt(ServerLevel level, BlockPos at, boolean step) {
        int depth = step ? DEPTH - 1 : DEPTH;
        BlockPos surface = level.getHeightmapPos(
                net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, at).below();
        for (int i = 0; i < depth; i++) {
            BlockPos spot = surface.below(i);
            if (diggable(level.getBlockState(spot))) {
                level.setBlock(spot, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        soldier.swing(soldier.getUsedItemHand());
    }

    /**
     * Where to dig: the soldier's own city's ground, on the side the enemy is on.
     *
     * <p>Picked from the chunk the soldier is standing in rather than from the whole city, because
     * a defender who marched across their own territory to a "better" spot every time the front
     * moved would spend the phase walking rather than holding.
     */
    private @Nullable BlockPos chooseLine(ServerLevel level, City mine) {
        CityData data = CityData.get(level.getServer());
        BlockPos here = soldier.blockPosition();

        // The nearest enemy city decides which way the line faces.
        City nearest = null;
        double best = Double.MAX_VALUE;
        for (City enemy : data.cities()) {
            if (enemy.id().equals(mine.id())
                    || Diplomacy.stance(mine, enemy) != Relation.WAR) {
                continue;
            }
            for (long chunkKey : enemy.claimedChunks()) {
                ChunkPos chunk = new ChunkPos(chunkKey);
                double distance = here.distSqr(new BlockPos(
                        chunk.getMiddleBlockX(), here.getY(), chunk.getMiddleBlockZ()));
                if (distance < best) {
                    best = distance;
                    nearest = enemy;
                    along = Direction.fromYRot(Math.toDegrees(Math.atan2(
                            chunk.getMiddleBlockZ() - here.getZ(),
                            chunk.getMiddleBlockX() - here.getX())) - 90.0D).getClockWise();
                }
            }
        }
        if (nearest == null) {
            return null;
        }
        // Centred on the soldier, so the line runs across the direction of the threat rather than
        // pointing at it.
        return here.relative(along.getOpposite(), LENGTH / 2);
    }
}
