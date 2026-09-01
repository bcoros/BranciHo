package com.branciho.citiesinlife.block;

import com.branciho.citiesinlife.city.City;
import com.branciho.citiesinlife.city.CityData;
import com.branciho.citiesinlife.net.ServerActions;
import com.branciho.citiesinlife.structure.Structure;
import com.branciho.citiesinlife.structure.StructureType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A big domed button on a pedestal, of the kind everybody already knows what to do with.
 *
 * <p>The City Hall panel can already call a meeting and silence the city, and a panel is the wrong
 * place for either. The moment you actually want them is the moment you have run into the building,
 * and a control you run to and slam is a completely different object from a menu item — which is
 * the whole reason there is a physical one at all.
 *
 * <p>Two rules, both of them the hall's own. It has to be standing inside a registered city core,
 * and the city has to be yours. Neither is a courtesy: a button anyone could place on their own
 * doorstep would be a way to call meetings in a city you have never been to.
 *
 * <p>No block entity. The dome sinks for a moment and comes back on a scheduled tick, which is what
 * a vanilla button does and is all the state it needs.
 */
public abstract class CityHallButtonBlock extends Block {

    public static final BooleanProperty PRESSED = BlockStateProperties.POWERED;

    /** How long the dome stays down. Long enough to see, short enough not to feel jammed. */
    private static final int PRESS_TICKS = 12;

    /** A pedestal with a dome on top of it. */
    private static final VoxelShape SHAPE = Block.box(2.0D, 0.0D, 2.0D, 14.0D, 11.0D, 14.0D);

    protected CityHallButtonBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(PRESSED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PRESSED);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer presser) || !(level instanceof ServerLevel server)) {
            return InteractionResult.PASS;
        }

        CityData data = CityData.get(server.getServer());
        City own = data.cityOf(presser.getUUID(), server.dimension());
        if (own == null) {
            refuse(presser, "message.citiesinlife.button_no_city");
            return InteractionResult.CONSUME;
        }
        if (!inCityHall(data, server, pos, own)) {
            refuse(presser, "message.citiesinlife.button_not_in_hall");
            return InteractionResult.CONSUME;
        }

        press(server, presser, data, own);
        // Whatever it just did shows on the panel, and the panel may well be open behind the
        // player who ran in here to press this.
        ServerActions.syncCityHall(presser);
        level.setBlock(pos, state.setValue(PRESSED, true), Block.UPDATE_ALL);
        level.scheduleTick(pos, this, PRESS_TICKS);
        level.playSound(null, pos, clack(), SoundSource.BLOCKS, 0.9F, pitch());
        return InteractionResult.CONSUME;
    }

    /** The dome comes back up. */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(PRESSED)) {
            level.setBlock(pos, state.setValue(PRESSED, false), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_OFF, SoundSource.BLOCKS,
                    0.5F, pitch() * 0.9F);
        }
    }

    /**
     * Whether this button is standing inside its presser's own city hall.
     *
     * <p>The button's position and not the player's, deliberately. A player leaning through the
     * doorway to reach a button that <em>is</em> inside the hall has done nothing wrong; a player
     * standing inside the hall reaching out to a button on the lawn has.
     */
    private static boolean inCityHall(CityData data, ServerLevel level, BlockPos pos, City own) {
        Structure here = data.structureAt(level.dimension(), pos);
        return here != null && here.type() == StructureType.CITY_CORE
                && own.structures().contains(here.id());
    }

    private static void refuse(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable(key), true);
    }

    /** What this particular button is wired to. */
    protected abstract void press(ServerLevel level, ServerPlayer player, CityData data, City own);

    /** The clack it makes, so the two buttons do not sound identical. */
    protected abstract net.minecraft.sounds.SoundEvent clack();

    protected abstract float pitch();

    /**
     * The one that calls everybody in.
     *
     * <p>Opening a meeting that is already open is not an error worth a message — the panel and the
     * button are two ways to the same lever, and pressing the lever twice is a thing people do.
     */
    public static class Meeting extends CityHallButtonBlock {

        public static final com.mojang.serialization.MapCodec<Meeting> CODEC =
                simpleCodec(Meeting::new);

        public Meeting(Properties properties) {
            super(properties);
        }

        @Override
        protected com.mojang.serialization.MapCodec<? extends Block> codec() {
            return CODEC;
        }

        @Override
        protected void press(ServerLevel level, ServerPlayer player, CityData data, City own) {
            ServerActions.callMeeting(level.getServer(), player, own);
        }

        @Override
        protected net.minecraft.sounds.SoundEvent clack() {
            return SoundEvents.NOTE_BLOCK_BELL.value();
        }

        @Override
        protected float pitch() {
            return 1.4F;
        }
    }

    /** The one that shuts everything up. */
    public static class Hush extends CityHallButtonBlock {

        public static final com.mojang.serialization.MapCodec<Hush> CODEC =
                simpleCodec(Hush::new);

        public Hush(Properties properties) {
            super(properties);
        }

        @Override
        protected com.mojang.serialization.MapCodec<? extends Block> codec() {
            return CODEC;
        }

        @Override
        protected void press(ServerLevel level, ServerPlayer player, CityData data, City own) {
            ServerActions.hush(player, data, own, !own.hushed());
        }

        @Override
        protected net.minecraft.sounds.SoundEvent clack() {
            return SoundEvents.NOTE_BLOCK_BASEDRUM.value();
        }

        @Override
        protected float pitch() {
            return 0.7F;
        }
    }
}
