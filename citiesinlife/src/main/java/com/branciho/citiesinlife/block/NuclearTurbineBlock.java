package com.branciho.citiesinlife.block;

import com.branciho.citiesinlife.blockentity.TurbineBlockEntity;
import com.branciho.citiesinlife.nuclear.ReactorReadout;
import com.branciho.citiesinlife.power.PowerBlock;
import com.branciho.citiesinlife.power.PowerGrid;
import com.branciho.citiesinlife.power.PowerRole;
import com.branciho.citiesinlife.registry.ModBlockEntities;
import com.branciho.citiesinlife.upgrade.Upgradeable;
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
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import com.branciho.citiesinlife.sound.MachineSounds;
import net.minecraft.util.RandomSource;

/**
 * The nuclear turbine: the same machine as the steam turbine, fed by something far angrier.
 *
 * <p>Deliberately <b>not</b> a subclass of {@link TurbineBlock}, and that is load-bearing rather
 * than fussy. {@code PlantSurvey} finds a coal plant's turbines with {@code instanceof TurbineBlock};
 * were this a subclass, every nuclear turbine standing in a reactor hall would also be counted as a
 * coal plant's turbine the moment somebody drew a Power Plant box anywhere near it, and a boiler
 * would start trying to drive it.
 *
 * <p>It shares the block entity, though, because everything about running a turbine — the charge
 * buffer, the coast-down, fouling, catching fire, the upgrade — is identical whatever is boiling
 * the water. What differs is the rating handed to it, and that comes from the reactor.
 */
public class NuclearTurbineBlock extends BaseEntityBlock implements PowerBlock, Upgradeable {

    public static final MapCodec<NuclearTurbineBlock> CODEC = simpleCodec(NuclearTurbineBlock::new);

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    /**
     * Dearer than the steam turbine's 2,500 by a wide margin.
     *
     * <p>Half as much again on a machine already producing thousands is worth an order of magnitude
     * more than the same bonus on one producing a hundred and fifty. Pricing them alike would make
     * this the only upgrade anybody ever bought.
     */
    private static final long UPGRADE_COST = 15_000L;

    public NuclearTurbineBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
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
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TurbineBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, ModBlockEntities.TURBINE.get(),
                        TurbineBlockEntity::clientTick)
                : createTickerHelper(type, ModBlockEntities.TURBINE.get(),
                        TurbineBlockEntity::serverTick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            player.displayClientMessage(ReactorReadout.describe(level, pos), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState,
                            boolean moved) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            PowerGrid.get(serverLevel.getServer()).removeNode(level.dimension(), pos);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public PowerRole powerRole() {
        return PowerRole.PRODUCER;
    }

    @Override
    public int linkRange() {
        return 32;
    }

    @Override
    public int powerOutput(BlockGetter getter, BlockPos pos, BlockState state) {
        return getter.getBlockEntity(pos) instanceof TurbineBlockEntity turbine
                ? turbine.output() : 0;
    }

    // ------------------------------------------------------------- upgrading

    @Override
    public int maxTier() {
        return TurbineBlockEntity.MAX_TIER;
    }

    @Override
    public int tierAt(BlockGetter level, BlockPos pos, BlockState state) {
        return level.getBlockEntity(pos) instanceof TurbineBlockEntity turbine ? turbine.tier() : 0;
    }

    @Override
    public long upgradeCost(int fromTier) {
        return UPGRADE_COST;
    }

    @Override
    public boolean upgrade(Level level, BlockPos pos, BlockState state) {
        return level.getBlockEntity(pos) instanceof TurbineBlockEntity turbine && turbine.upgrade();
    }

    @Override
    public Component describe(BlockGetter level, BlockPos pos, BlockState state) {
        int percent = level.getBlockEntity(pos) instanceof TurbineBlockEntity turbine
                ? Math.round(turbine.outputMultiplier() * 100.0F) : 100;
        return Component.translatable("message.citiesinlife.upgraded_nuclear_turbine",
                tierAt(level, pos, state) + 1, percent);
    }

    /**
     * A turbine hall, which is eleven blocks of machine and should sound like it.
     *
     * <p>Half again as loud as a coal plant's turbine, and following the dial in both volume and
     * pitch the same way the rotors follow it. Opening the throttle on a reactor should be
     * something you hear from the control room, not only something the monitor reports.
     */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        super.animateTick(state, level, pos, random);
        if (level.getBlockEntity(pos) instanceof TurbineBlockEntity turbine && turbine.running()) {
            MachineSounds.turbine(level, pos, random, turbine.throttle() / 100.0F, 1.6F);
        }
    }
}
