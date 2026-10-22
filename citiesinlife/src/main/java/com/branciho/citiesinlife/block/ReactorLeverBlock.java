package com.branciho.citiesinlife.block;

import com.branciho.citiesinlife.blockentity.ReactorLeverBlockEntity;
import com.branciho.citiesinlife.nuclear.ReactorLever;
import com.branciho.citiesinlife.nuclear.ReactorReadout;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/**
 * One of the reactor's four controls: a throw switch on the wall.
 *
 * <p>All four share one block class, one property and one model, and differ only in what the
 * simulation reads them for. That uniformity is the point — the cooler, the heat and the relief
 * valve are two-position switches, and the turbine dial has five, but they are visibly the same
 * piece of equipment, so a player who learns one has learned all four.
 *
 * <p>{@code POSITION} runs 0 to 4 for every one of them. The two-position switches only ever hold 0
 * or 4; the turbine dial uses all five. Encoding both as one property means one blockstate file,
 * one model, and one animation, instead of a boolean variant and an integer variant that would
 * inevitably drift apart.
 *
 * <p>A missing or broken lever reads as 0 — off. That is deliberate and it is a safety property:
 * you can shut down a reactor with a pickaxe.
 */
public class ReactorLeverBlock extends BaseEntityBlock {

    public static final MapCodec<ReactorLeverBlock> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    propertiesCodec(),
                    ReactorLever.CODEC.fieldOf("lever").forGetter(ReactorLeverBlock::lever))
                    .apply(instance, ReactorLeverBlock::new));

    /** Which way the face of the switch points. It hangs on a wall like the real thing. */
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    /** 0 is up-and-red, 4 is down-and-green. The dial uses the three positions between. */
    public static final IntegerProperty POSITION = IntegerProperty.create("position", 0, 4);

    public static final int MAX_POSITION = 4;

    /** A shallow plate on the wall: it is bolted on, not sunk in. */
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        SHAPES.put(Direction.NORTH, Block.box(3.0D, 2.0D, 11.0D, 13.0D, 14.0D, 16.0D));
        SHAPES.put(Direction.SOUTH, Block.box(3.0D, 2.0D, 0.0D, 13.0D, 14.0D, 5.0D));
        SHAPES.put(Direction.WEST, Block.box(11.0D, 2.0D, 3.0D, 16.0D, 14.0D, 13.0D));
        SHAPES.put(Direction.EAST, Block.box(0.0D, 2.0D, 3.0D, 5.0D, 14.0D, 13.0D));
    }

    private final ReactorLever lever;

    public ReactorLeverBlock(Properties properties, ReactorLever lever) {
        super(properties);
        this.lever = lever;
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(POSITION, 0));
    }

    public ReactorLever lever() {
        return lever;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        // The housing is a normal model; the arm is drawn by the block-entity renderer on top of it.
        return RenderShape.MODEL;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POSITION);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(POSITION, 0);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ReactorLeverBlockEntity(pos, state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext context) {
        return SHAPES.getOrDefault(state.getValue(FACING), SHAPES.get(Direction.NORTH));
    }

    /**
     * Throw the switch.
     *
     * <p>A two-position control snaps between the ends. The turbine dial steps up one notch at a
     * time and wraps to off from the top, so shutting a reactor down is never more than five clicks
     * and is always reachable by continuing to press the same button.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        int was = state.getValue(POSITION);
        int now = lever.stepped()
                ? (was + 1) % (MAX_POSITION + 1)
                : (was > 0 ? 0 : MAX_POSITION);

        if (level.getBlockEntity(pos) instanceof ReactorLeverBlockEntity arm) {
            arm.beginSwing(was, level.getGameTime());
        }
        level.setBlock(pos, state.setValue(POSITION, now), Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.5F,
                now > was ? 0.7F : 0.55F);

        player.displayClientMessage(ReactorReadout.describe(level, pos), true);
        return InteractionResult.CONSUME;
    }

    /** What this control is set to, read straight from the world. Absent reads as off. */
    public static int positionAt(BlockGetter level, @Nullable BlockPos pos) {
        if (pos == null) {
            return 0;
        }
        BlockState state = level.getBlockState(pos);
        return state.hasProperty(POSITION) ? state.getValue(POSITION) : 0;
    }
}
