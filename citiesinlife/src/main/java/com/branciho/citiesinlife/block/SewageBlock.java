package com.branciho.citiesinlife.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * What comes out of the outfall, and it runs downhill.
 *
 * <p>Still a block rather than a real fluid, deliberately — a registered fluid would be a
 * FluidType, a FlowingFluid, a LiquidBlock and a bucket, and the one thing that was actually wrong
 * with the old version is that it sat in a single cube like a pane of brown glass. So it keeps its
 * own spread instead: a source at the pipe mouth, thinning outward to nothing over seven steps,
 * falling straight down where it can.
 *
 * <p>It also retracts on its own, which is the half that matters. Every non-source cell asks each
 * tick whether anything is still feeding it — sewage above, or a thicker cell beside it — and
 * removes itself if not. Turn the outfall off and the whole spill drains back to nothing without
 * the end pipe having to remember a single block of it.
 *
 * <p>You can walk through it. You should not want to.
 */
public class SewageBlock extends Block {

    /** 0 is the source at the pipe mouth; 7 is the thinnest edge before it stops. */
    public static final IntegerProperty LEVEL = IntegerProperty.create("level", 0, 7);

    public static final int MAX_LEVEL = 7;

    /** How long between spread steps. Slower than water, because it is sludge. */
    private static final int SPREAD_DELAY = 8;

    /** How often somebody standing in it is made to regret it. Two seconds. */
    private static final int SICKEN_INTERVAL = 40;

    private static final VoxelShape[] SHAPES = new VoxelShape[MAX_LEVEL + 1];

    static {
        // Deepest at the source and shallower toward the edge, so a spill reads as having a
        // direction rather than as a flat brown carpet.
        for (int i = 0; i <= MAX_LEVEL; i++) {
            double height = 6.0D - i * 0.6D;
            SHAPES[i] = Block.box(0.0D, 0.0D, 0.0D, 16.0D, height, 16.0D);
        }
    }

    public SewageBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(LEVEL, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LEVEL);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext context) {
        return SHAPES[state.getValue(LEVEL)];
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                           CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState,
                           boolean movedByPiston) {
        level.scheduleTick(pos, this, SPREAD_DELAY);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
                                   BlockPos fromPos, boolean movedByPiston) {
        level.scheduleTick(pos, this, SPREAD_DELAY);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int depth = state.getValue(LEVEL);

        // Anything that is not the source has to justify its own existence every tick. This is
        // what makes the spill drain away when the outfall stops, with nothing keeping a list.
        if (depth > 0 && !fed(level, pos, depth)) {
            level.removeBlock(pos, false);
            return;
        }

        // Downhill first, exactly as water does. A fall resets to a source so a spill off a ledge
        // does not run out of depth halfway down.
        BlockPos below = pos.below();
        if (canFlowInto(level, below)) {
            level.setBlock(below, defaultBlockState().setValue(LEVEL, 0), Block.UPDATE_ALL);
            return;
        }

        if (depth >= MAX_LEVEL) {
            return;
        }
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos next = pos.relative(side);
            if (canFlowInto(level, next)) {
                level.setBlock(next, defaultBlockState().setValue(LEVEL, depth + 1),
                        Block.UPDATE_ALL);
            }
        }
    }

    /** Whether anything upstream is still supplying this cell. */
    private static boolean fed(ServerLevel level, BlockPos pos, int depth) {
        BlockState above = level.getBlockState(pos.above());
        if (above.getBlock() instanceof SewageBlock) {
            return true;
        }
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockState beside = level.getBlockState(pos.relative(side));
            if (beside.getBlock() instanceof SewageBlock && beside.getValue(LEVEL) < depth) {
                return true;
            }
        }
        return false;
    }

    /** Somewhere sewage may go: empty, or something flimsy enough to be washed away. */
    private static boolean canFlowInto(ServerLevel level, BlockPos pos) {
        BlockState there = level.getBlockState(pos);
        if (there.getBlock() instanceof SewageBlock) {
            return false;
        }
        // Never displaces water. A sewer that ate a lake would be a very different feature.
        return there.isAir() || (there.canBeReplaced() && there.getFluidState().isEmpty());
    }

    /**
     * Wading through raw sewage does you no good.
     *
     * <p>Nausea and hunger rather than damage. It should be unpleasant and worth avoiding, not a
     * trap that kills somebody who walked across their own outfall while building it.
     */
    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level.isClientSide || !(entity instanceof LivingEntity living)) {
            return;
        }
        if (level.getGameTime() % SICKEN_INTERVAL != 0L) {
            return;
        }
        living.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 120, 0, false, false));
        living.addEffect(new MobEffectInstance(MobEffects.HUNGER, 120, 0, false, false));
    }
}
