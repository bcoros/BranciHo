package com.branciho.citiesinlife.block;

import com.branciho.citiesinlife.blockentity.OfficeSpaceBlockEntity;
import com.branciho.citiesinlife.registry.ModBlockEntities;
import com.branciho.citiesinlife.work.WorkplaceRegistry;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import com.branciho.citiesinlife.entity.CitizenEntity;
import com.branciho.citiesinlife.sound.MachineSounds;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;

/**
 * A desk. One person works here.
 *
 * <p>The block is the desk; the chair is drawn in the cell in front of it and is not part of the
 * block at all. That is deliberate — a citizen cannot pathfind into a block it collides with, so if
 * the seat were inside this one nobody could ever reach it. Instead the worker walks into the empty
 * cell in front, and the chair happens to be drawn there.
 *
 * <p>Which means the one rule for building an office is: leave the space in front of each desk
 * clear. That is also how a real office is laid out, so it tends to happen by itself.
 */
public class OfficeSpaceBlock extends BaseEntityBlock {

    public static final MapCodec<OfficeSpaceBlock> CODEC = simpleCodec(OfficeSpaceBlock::new);

    /** Which way the desk faces — the worker sits on this side, looking back at the screen. */
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    private static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 13.0D, 16.0D);

    public OfficeSpaceBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        // The desk faces the player who placed it, so the chair lands where they are standing.
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new OfficeSpaceBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return createTickerHelper(type, ModBlockEntities.OFFICE_SPACE.get(),
                OfficeSpaceBlockEntity::serverTick);
    }

    /**
     * Tell the registry a job exists here.
     *
     * <p>On placement rather than on {@code setPlacedBy}, because a desk that arrived through a
     * command or a structure block is still a desk and there is no reason it should be the one nobody
     * can be assigned to.
     */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (!oldState.is(state.getBlock()) && level instanceof ServerLevel serverLevel) {
            WorkplaceRegistry.get(serverLevel.getServer()).addOffice(level.dimension(), pos);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            WorkplaceRegistry.get(serverLevel.getServer()).remove(level.dimension(), pos);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    /** Right click to find out whether anybody actually works here. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof OfficeSpaceBlockEntity desk) {
            player.displayClientMessage(Component.translatable(
                    "message.citiesinlife.desk_status", desk.staffed(), desk.capacity()), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * Typing, and only when somebody is actually at the desk.
     *
     * <p>Asked of the world rather than of the block entity, and that is the whole trick. Who has
     * checked in at a desk is server-side bookkeeping that the client is never told about, so a
     * client asking {@code staffed()} is always told nobody - but the worker itself is an entity
     * standing in the chair, which the client can see perfectly well. Looking for the person
     * rather than the record is both the only thing that works here and the more honest answer:
     * the typing you hear is coming from somebody you can see.
     */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        super.animateTick(state, level, pos, random);
        if (random.nextInt(3) != 0) {
            return;
        }
        BlockPos seat = pos.relative(state.hasProperty(FACING)
                ? state.getValue(FACING) : Direction.NORTH);
        if (occupied(level, new AABB(seat).inflate(0.35D))) {
            MachineSounds.typing(level, pos, random);
        }
    }

    /** Whether one of this city's people is standing in that space. */
    private static boolean occupied(Level level, AABB where) {
        return !level.getEntitiesOfClass(CitizenEntity.class, where).isEmpty();
    }
}
