package com.branciho.livingcities.scan;

import com.branciho.livingcities.LivingCities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Classifies one {@code BlockState} into a {@link BlockProfile}.
 *
 * <p><b>Server thread only.</b> Every call here reaches into block behaviour, and a modded
 * {@code getCollisionShape} is free to look up a {@code BlockEntity} or read a level field. Doing
 * that off the main thread is a data race in someone else's code that would be reported as our
 * crash. Since there are only a few hundred distinct states in even a large selection, running all
 * of it on the main thread costs almost nothing.
 *
 * <p>The classification is intentionally built out of geometry rather than block identity. There is
 * no list of vanilla blocks here and there must never be one: a modded glass, a modded stair and a
 * modded hatch have to sort themselves correctly on the first launch of a pack nobody tested.
 */
public final class BlockProfiler {

    /**
     * Escape hatch for pathological blocks. Treated as empty space, so a barrier maze or a mod's
     * invisible marker block cannot invent walls or floors. An undefined tag is simply empty.
     */
    public static final TagKey<Block> SCAN_EXCLUDED =
            TagKey.create(Registries.BLOCK, LivingCities.id("scan_excluded"));

    /** Escape hatch the other way: force a block to behave as a wall for the enclosure fill. */
    public static final TagKey<Block> SEALS_BUILDING =
            TagKey.create(Registries.BLOCK, LivingCities.id("seals_building"));

    /** Above this the shape is a step rather than something you walk over without noticing. */
    private static final float DECOR_MAX_TOP = 0.25F;

    /** Paired with the height gate so a thin-but-wide rug counts as decor and a fence post does not. */
    private static final float DECOR_MAX_VOLUME = 0.30F;

    /** Floating point slack when asking "is the collision top at the block ceiling". */
    private static final float FULL_TOP_EPSILON = 0.99F;

    private BlockProfiler() {
    }

    /**
     * Profile {@code state} as it occurs at {@code pos}.
     *
     * <p>{@code pos} must be a position where this state genuinely exists: some shapes are queried
     * against the level, and handing them a position holding a different block produces garbage.
     *
     * @throws RuntimeException whatever a misbehaving block throws; callers are expected to catch
     *         and fall back to {@link BlockProfile#unknown()}
     */
    public static BlockProfile profile(BlockGetter level, BlockPos pos, BlockState state) {
        // Technical blocks are handled before any geometry is looked at, because the whole point is
        // that their geometry must not be reasoned about.
        if (state.is(SCAN_EXCLUDED)) {
            return BlockProfile.TECHNICAL;
        }
        if (state.isAir()) {
            return BlockProfile.AIR;
        }

        // CollisionContext.empty() and not of(entity): entity-sensitive shapes (powder snow, leaves
        // for a wandering trader) must not make a building's capacity depend on who walks past.
        final VoxelShape collision = state.getCollisionShape(level, pos, CollisionContext.empty());
        final boolean shapeEmpty = collision.isEmpty();
        final float topY = shapeEmpty ? 0.0F : (float) collision.max(Direction.Axis.Y);
        final float volume = shapeEmpty ? 0.0F : shapeVolume(collision);

        final boolean fullBlock = state.isCollisionShapeFullBlock(level, pos);
        // The three-argument overload is defined as SupportType.FULL, which is exactly what "can you
        // lay a floor on this" means, and it avoids depending on the SupportType enum's members.
        final boolean sturdyTop = state.isFaceSturdy(level, pos, Direction.UP);
        final boolean motion = state.blocksMotion();
        final boolean occludes = state.canOcclude();
        final boolean solidRender = state.isSolidRender(level, pos);
        final boolean blockEntity = state.hasBlockEntity();
        final int light = state.getLightEmission();
        final FluidState fluid = state.getFluidState();

        final BlockClass type = classify(state, fullBlock, shapeEmpty, topY, volume, sturdyTop, solidRender, fluid);
        return new BlockProfile(type, topY, volume, sturdyTop, motion, occludes, solidRender, blockEntity,
                false, light);
    }

    private static BlockClass classify(BlockState state, boolean fullBlock, boolean shapeEmpty, float topY,
                                       float volume, boolean sturdyTop, boolean solidRender, FluidState fluid) {
        if (isDoorLike(state, fullBlock)) {
            return BlockClass.DOOR_LIKE;
        }
        // Fluids are tested after solids on purpose: a waterlogged stair is a stair, not water.
        if (shapeEmpty && !fluid.isEmpty()) {
            return BlockClass.FLUID;
        }
        if (fullBlock) {
            // isSolidRender is "occludes AND full cube", so glass separates itself out for free.
            return solidRender ? BlockClass.SOLID_SUPPORT : BlockClass.TRANSPARENT_SOLID;
        }
        if (shapeEmpty) {
            return BlockClass.OPEN;
        }
        if (topY <= DECOR_MAX_TOP && volume <= DECOR_MAX_VOLUME) {
            return BlockClass.DECOR_PASSABLE;
        }
        if (sturdyTop && topY >= FULL_TOP_EPSILON) {
            return BlockClass.TOP_SUPPORT;
        }
        return BlockClass.PARTIAL;
    }

    /**
     * Door detection without naming a single door.
     *
     * <p>The {@code OPEN} property plus the vanilla door tags catch every door, trapdoor, gate and
     * hatch in the game and in essentially every mod. The full-cube exclusion is what keeps barrels
     * and other openable containers out: they also carry {@code OPEN}, but a barrel in a wall is
     * still a wall and a barrel in a floor is still floor, so classifying it as a door would quietly
     * delete usable area.
     */
    private static boolean isDoorLike(BlockState state, boolean fullBlock) {
        if (state.is(SEALS_BUILDING)) {
            return true;
        }
        if (fullBlock) {
            return false;
        }
        return state.hasProperty(BlockStateProperties.OPEN)
                || state.is(BlockTags.DOORS)
                || state.is(BlockTags.TRAPDOORS)
                || state.is(BlockTags.FENCE_GATES);
    }

    /**
     * Volume of the collision shape's bounding box.
     *
     * <p>The exact union-of-boxes volume would be more precise, but this value is only used as a
     * tiebreak for "is this thin enough to walk over", where the height test already does the real
     * work - and min/max per axis are the shape APIs least likely to move between versions.
     */
    private static float shapeVolume(VoxelShape shape) {
        final double dx = shape.max(Direction.Axis.X) - shape.min(Direction.Axis.X);
        final double dy = shape.max(Direction.Axis.Y) - shape.min(Direction.Axis.Y);
        final double dz = shape.max(Direction.Axis.Z) - shape.min(Direction.Axis.Z);
        return (float) Math.max(0.0D, dx * dy * dz);
    }
}
