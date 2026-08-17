package com.branciho.citiesinlife.block;

import com.branciho.citiesinlife.blockentity.AlarmBlockEntity;
import com.branciho.citiesinlife.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A siren for a power plant.
 *
 * <p>The turbine already tells you it is in trouble — it smokes, then it burns, then it takes the
 * building with it. The trouble is that all of that happens inside a shed you built precisely so you
 * would not have to look at it. An alarm is how a plant shouts at you from across the city.
 *
 * <p>It answers to the box, like everything else in a plant: put one anywhere inside a registered
 * Turbine Power Plant and it watches every turbine in that box. Outside one it is a decoration.
 */
public class AlarmBlock extends BaseEntityBlock {

    public static final MapCodec<AlarmBlock> CODEC = simpleCodec(AlarmBlock::new);

    /**
     * How bad it is.
     *
     * <p>Two levels rather than one, because the fire is not the moment you can still do something
     * about it. A turbine seizes, smoulders for two minutes, and only then catches — so an alarm
     * that waits for flames is an alarm that tells you when it is nearly too late. Amber means fetch
     * the wrench; red means fetch the extinguisher and be quick.
     */
    public enum Trouble implements StringRepresentable {
        NONE("none", 0),
        FOULED("fouled", 7),
        FIRE("fire", 12);

        private final String id;
        private final int light;

        Trouble(String id, int light) {
            this.id = id;
            this.light = light;
        }

        public int light() {
            return light;
        }

        @Override
        public String getSerializedName() {
            return id;
        }
    }

    public static final EnumProperty<Trouble> TROUBLE = EnumProperty.create("trouble", Trouble.class);

    /** Flipped a few times a second while sounding. This is the flash. */
    public static final BooleanProperty FLASH = BooleanProperty.create("flash");

    private static final VoxelShape SHAPE = Block.box(3.0D, 3.0D, 3.0D, 13.0D, 13.0D, 13.0D);

    public AlarmBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(TROUBLE, Trouble.NONE)
                .setValue(FLASH, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TROUBLE, FLASH);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /**
     * The glow is steady even though the lens flashes.
     *
     * <p>Deliberate: a light level that changed several times a second would make the game relight
     * the surrounding chunk each time, for a flicker the lens already conveys on its own.
     */
    public static int lightFor(BlockState state) {
        return state.getValue(TROUBLE).light();
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AlarmBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return createTickerHelper(type, ModBlockEntities.ALARM.get(), AlarmBlockEntity::serverTick);
    }

    /** Right click to hear what it is watching, which is the only way to know it is wired to anything. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof AlarmBlockEntity alarm) {
            player.displayClientMessage(alarm.report(), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
