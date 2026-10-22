package com.branciho.citiesinlife.block;

import com.branciho.citiesinlife.blockentity.MissileBlockEntity;
import com.branciho.citiesinlife.missile.MissileKind;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A rocket standing on its pad.
 *
 * <p>One block holding ten blocks of visuals, the same bargain the nuclear turbine hall makes.
 * That is not laziness: a real multiblock would mean the silo counting parts instead of counting
 * missiles, and the whole point of this block is that a silo's inventory is "how many of these are
 * standing in the box".
 *
 * <p>Which also means you build the silo around it. A missile does not collide with anything above
 * its own foot, so the hangar, the doors and the shaft are yours to build — and if you leave the
 * roof off, the rocket is simply visible from the air, which is a decision rather than a bug.
 *
 * <p>Three of these are registered from one class. What separates a warhead from an interceptor is
 * a {@link MissileKind}, not a different block: they stand the same way, they are found the same
 * way, and they leave the same way.
 */
public class MissileBlock extends BaseEntityBlock {

    public static final MapCodec<MissileBlock> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    propertiesCodec(),
                    MissileKind.CODEC.fieldOf("kind").forGetter(MissileBlock::kind))
                    .apply(instance, MissileBlock::new));

    /** Which way it is turned. Only matters for the fins; a rocket has no front. */
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    /**
     * The pad, and nothing above it.
     *
     * <p>Two blocks across and half a block high, so you can walk up to a missile and stand beside
     * it. Giving it a full ten-block column of collision would make the silo impossible to build
     * around, which is the one thing a silo has to be.
     */
    private static final VoxelShape SHAPE = Block.box(1.0D, 0.0D, 1.0D, 15.0D, 8.0D, 15.0D);

    private final MissileKind kind;

    public MissileBlock(Properties properties, MissileKind kind) {
        super(properties);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    public MissileKind kind() {
        return kind;
    }

    /** What is standing here, or null if this is not a missile at all. */
    public static @Nullable MissileKind kindAt(BlockState state) {
        return state.getBlock() instanceof MissileBlock missile ? missile.kind() : null;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext context) {
        return SHAPE;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MissileBlockEntity(pos, state);
    }
}
