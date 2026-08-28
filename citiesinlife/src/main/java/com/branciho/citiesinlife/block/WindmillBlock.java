package com.branciho.citiesinlife.block;

import com.branciho.citiesinlife.blockentity.WindmillBlockEntity;
import com.branciho.citiesinlife.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import org.jetbrains.annotations.Nullable;
import com.branciho.citiesinlife.sound.MachineSounds;
import net.minecraft.util.RandomSource;

/**
 * A wind turbine's nacelle: the machine at the top of a tower you built yourself.
 *
 * <p>The mod ships no tower, the same way it ships no buildings. You raise a mast out of whatever
 * you like and put this on top, and the rotor is drawn from here — three blades, fifteen blocks
 * across, turning whenever the sky is open above it.
 *
 * <p>The blades are not decoration. Anything in the disc they sweep gets torn out, and anything
 * alive that flies through gets hit hard enough to notice. That is the reason it has to go up high:
 * a windmill at ground level will demolish its own hillside and then keep going.
 *
 * <p>Like a boiler it produces nothing by itself — it drives a Turbine inside the same registered
 * plant. A plant is coal or wind, never both.
 */
public class WindmillBlock extends Block implements EntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    private final WindmillColour colour;

    public WindmillBlock(Properties properties, WindmillColour colour) {
        super(properties);
        this.colour = colour;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    public WindmillColour colour() {
        return colour;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Face the player, so the rotor is side-on and you can see it turn from where you built it.
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WindmillBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                           BlockEntityType<T> type) {
        if (type != ModBlockEntities.WINDMILL.get()) {
            return null;
        }
        @SuppressWarnings("unchecked")
        BlockEntityTicker<T> ticker = level.isClientSide
                ? (BlockEntityTicker<T>) (BlockEntityTicker<WindmillBlockEntity>) WindmillBlockEntity::clientTick
                : (BlockEntityTicker<T>) (BlockEntityTicker<WindmillBlockEntity>) WindmillBlockEntity::serverTick;
        return ticker;
    }

    /** The blades going past, which is the whole of what a wind turbine has to say. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        super.animateTick(state, level, pos, random);
        if (level.getBlockEntity(pos) instanceof WindmillBlockEntity mill && mill.turning()) {
            MachineSounds.windmill(level, pos, random);
        }
    }
}
