package com.branciho.citiesinlife.block;

import com.branciho.citiesinlife.city.CityData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * What the two airplane blocks have in common: they are both airfields, and you may only have twenty.
 *
 * <p>The cap is shared rather than one budget each. "Airports: 14/20" is a sentence a player can
 * hold in their head; two separate allowances for two things that look like the same thing is not.
 *
 * <p>Enforced in two places on purpose. Refusing at placement is the friendly half — you are told
 * why, and you keep the block. The half that actually holds is in the block entity, which does
 * nothing at all unless {@link CityData#airfieldOwner} knows who put it there. Neither
 * {@code getStateForPlacement} nor {@code setPlacedBy} runs for {@code /fill}, {@code /clone} or a
 * {@code setBlock} from another mod, and in a mod built around creative building, a cap that only
 * covers blocks placed by hand is not a cap.
 */
public abstract class AirfieldBlock extends BaseEntityBlock {

    protected AirfieldBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        // BaseEntityBlock defaults to INVISIBLE, with no compile error to warn you.
        return RenderShape.MODEL;
    }

    /**
     * Refuse outright at the cap rather than consuming the block.
     *
     * <p>The PowerMastBlock precedent: returning null here means nothing is placed and the item
     * stays in hand, which is the only outcome that does not feel like the game eating something.
     */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        if (context.getLevel() instanceof ServerLevel serverLevel && context.getPlayer() != null
                && !CityData.get(serverLevel.getServer()).canPlaceAirfield(
                        context.getPlayer().getUUID())) {
            context.getPlayer().displayClientMessage(Component.translatable(
                    "message.citiesinlife.airfields_full", CityData.MAX_AIRFIELDS_PER_PLAYER), true);
            return null;
        }
        return super.getStateForPlacement(context);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel serverLevel && placer instanceof Player player) {
            CityData.get(serverLevel.getServer())
                    .claimAirfield(player.getUUID(), level.dimension(), pos);
        }
    }

    /**
     * Give the allowance back when it comes down.
     *
     * <p>{@code onRemove} rather than a break event, because this is the only hook that also catches
     * explosions, pistons and {@code /setblock}. The {@code is(newState)} guard stops a mere
     * blockstate change from wiping the record.
     */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState,
                            boolean moved) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            CityData.get(serverLevel.getServer()).releaseAirfield(level.dimension(), pos);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    /** Whether this airfield is one somebody is accountable for, and may therefore work. */
    public static boolean counted(ServerLevel level, BlockPos pos) {
        return CityData.get(level.getServer()).airfieldOwner(level.dimension(), pos) != null;
    }
}
