package com.branciho.citiesinlife.block;

import com.branciho.citiesinlife.power.MastBlock;
import com.branciho.citiesinlife.power.PowerBlock;
import com.branciho.citiesinlife.power.PowerGrid;
import com.branciho.citiesinlife.power.PowerRole;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import com.branciho.citiesinlife.sound.MachineSounds;
import net.minecraft.util.RandomSource;

/**
 * A wooden power mast, three blocks tall — the kind that lines a road in half of Europe.
 *
 * <p>Three blocks because a line has to clear the buildings it runs past. It is one node on the
 * network occupying three positions, so clicking any segment with the line tool resolves to the foot
 * and a line never attaches to the middle of one mast and the base of another.
 *
 * <p>Masts carry power and nothing else. Their long reach is the reason a solar farm can sit out in
 * the desert and still feed a city.
 */
public class PowerMastBlock extends Block implements PowerBlock, MastBlock {

    /** 0 is the foot, 2 is the crossarm at the top. */
    public static final IntegerProperty SEGMENT = IntegerProperty.create("segment", 0, 2);

    /**
     * Which way the crossarm points.
     *
     * <p>Every mast used to face north no matter how it was placed, so a line running east to west
     * met the arms end-on and looked wrong from every angle. The post is symmetrical, but the arm at
     * the top is not, and that is the part you actually see against the sky.
     */
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public static final int HEIGHT = 3;

    /** How far one mast can throw a line to another. */
    public static final int MAST_RANGE = 64;

    private static final VoxelShape POST = Block.box(6.0D, 0.0D, 6.0D, 10.0D, 16.0D, 10.0D);
    private static final VoxelShape CROSSARM_X = Block.box(1.0D, 6.0D, 6.0D, 15.0D, 16.0D, 10.0D);
    private static final VoxelShape CROSSARM_Z = Block.box(6.0D, 6.0D, 1.0D, 10.0D, 16.0D, 15.0D);

    public PowerMastBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(SEGMENT, 0)
                .setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SEGMENT, FACING);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        // Refuse rather than place a stump: a one-block mast that looks broken is worse than being
        // told there is no room.
        for (int offset = 1; offset < HEIGHT; offset++) {
            if (!level.getBlockState(pos.above(offset)).canBeReplaced()) {
                return null;
            }
        }
        if (pos.getY() + HEIGHT > level.getMaxBuildHeight()) {
            return null;
        }
        return defaultBlockState()
                .setValue(SEGMENT, 0)
                .setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable net.minecraft.world.entity.LivingEntity placer,
                            net.minecraft.world.item.ItemStack stack) {
        // The upper segments have to carry the same facing, or the crossarm ends up pointing a
        // different way from the post it is standing on.
        for (int offset = 1; offset < HEIGHT; offset++) {
            level.setBlock(pos.above(offset), state.setValue(SEGMENT, offset), Block.UPDATE_ALL);
        }
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        int segment = state.getValue(SEGMENT);
        if (segment == 0) {
            return true;
        }
        BlockState below = level.getBlockState(pos.below());
        return below.getBlock() instanceof PowerMastBlock && below.getValue(SEGMENT) == segment - 1;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction,
                                     BlockState neighbour, LevelAccessor level,
                                     BlockPos pos, BlockPos neighbourPos) {
        if (!canSurvive(state, level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return state;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        // Break any segment and the whole mast goes: leaving a floating crossarm behind would be a
        // structure the player has no obvious way to remove.
        BlockPos base = baseOf(level, pos, state);
        for (int offset = 0; offset < HEIGHT; offset++) {
            BlockPos segmentPos = base.above(offset);
            BlockState segmentState = level.getBlockState(segmentPos);
            if (segmentState.getBlock() instanceof PowerMastBlock && !segmentPos.equals(pos)) {
                level.setBlock(segmentPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && state.getValue(SEGMENT) == 0
                && level instanceof ServerLevel serverLevel) {
            PowerGrid.get(serverLevel.getServer()).removeNode(level.dimension(), pos);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (state.getValue(SEGMENT) != HEIGHT - 1) {
            return POST;
        }
        return state.getValue(FACING).getAxis() == Direction.Axis.X ? CROSSARM_Z : CROSSARM_X;
    }

    @Override
    public PowerRole powerRole() {
        return PowerRole.RELAY;
    }

    @Override
    public int linkRange() {
        return MAST_RANGE;
    }

    @Override
    public int mastHeight() {
        return HEIGHT;
    }

    @Override
    public BlockPos networkPos(BlockGetter level, BlockPos pos, BlockState state) {
        return baseOf(level, pos, state);
    }

    /** The foot of the mast this position belongs to. */
    private static BlockPos baseOf(BlockGetter level, BlockPos pos, BlockState state) {
        int segment = state.getBlock() instanceof PowerMastBlock ? state.getValue(SEGMENT) : 0;
        return pos.below(segment);
    }

    /**
     * The buzz off a line.
     *
     * <p>Only from the crossarm. A mast is one machine occupying three blocks, and letting all
     * three of them sing would make a single pole three times as loud as it should be and a row of
     * them along a road unbearable.
     */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        super.animateTick(state, level, pos, random);
        if (state.hasProperty(SEGMENT) && state.getValue(SEGMENT) == HEIGHT - 1) {
            MachineSounds.mast(level, pos, random, 1.0F);
        }
    }
}
