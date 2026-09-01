package com.branciho.citiesinlife.block;

import com.branciho.citiesinlife.blockentity.SirenBlockEntity;
import com.branciho.citiesinlife.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A siren: the only warning a city gets.
 *
 * <p>Without one, a missile arrives. That is the whole of it — the first you know is the crater,
 * and there is no version of that which is a game rather than a punishment. A siren turns an
 * incoming warhead into a minute of knowing, which is enough time to get out of the way, watch it
 * come in, or find out whether your interceptors work. It answers to three more things besides:
 * an alert declared at the city hall, a reactor of your own past saving, and fallout still coming
 * off a crater on your ground.
 *
 * <p>It answers to territory rather than to a wire, and — since this version — to territory alone.
 * It used to be switched on by a sweep over the city's registered building boxes, which meant that
 * a siren on a pole in the street, where sirens go, belonged to no box and therefore never made a
 * sound in its life. Now every siren asks for itself, once a second, from wherever it is standing.
 *
 * <p>A block entity, and not only for that. The mast is three and a half blocks tall and the horn
 * cluster turns while it sounds, neither of which a block model can do.
 */
public class SirenBlock extends BaseEntityBlock {

    public static final MapCodec<SirenBlock> CODEC = simpleCodec(SirenBlock::new);

    public static final BooleanProperty WAILING = BooleanProperty.create("wailing");

    /**
     * The foot of the mast, and only the foot.
     *
     * <p>Everything above the first block is drawn rather than built, and left walk-through on
     * purpose: a lattice tower you can shelter under reads as a tower, and a three-block column of
     * collision on a one-block item does not.
     */
    private static final VoxelShape SHAPE = Block.box(3.0D, 0.0D, 3.0D, 13.0D, 16.0D, 13.0D);

    public SirenBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(WAILING, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(WAILING);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext context) {
        return SHAPE;
    }

    /** All of it is drawn by the renderer, so the block model itself is an empty particle source. */
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    public static boolean wailing(BlockState state) {
        return state.hasProperty(WAILING) && state.getValue(WAILING);
    }

    /** The lamp on top, lit only while it is sounding. */
    public static int lightFor(BlockState state) {
        return wailing(state) ? 12 : 0;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SirenBlockEntity(pos, state);
    }

    /**
     * Ticked on both sides, which is unusual here and deliberate.
     *
     * <p>The server tick is the decision — am I in a city, and is that city sounding. The client
     * tick is the turning and the wail, which are played locally so the machine-volume setting can
     * reach them.
     */
    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, ModBlockEntities.SIREN.get(),
                        SirenBlockEntity::clientTick)
                : createTickerHelper(type, ModBlockEntities.SIREN.get(),
                        SirenBlockEntity::serverTick);
    }
}
