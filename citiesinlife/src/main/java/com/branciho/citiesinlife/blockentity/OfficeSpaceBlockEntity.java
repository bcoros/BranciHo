package com.branciho.citiesinlife.blockentity;

import com.branciho.citiesinlife.block.OfficeSpaceBlock;
import com.branciho.citiesinlife.registry.ModBlockEntities;
import com.branciho.citiesinlife.work.Workplace;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Who is at this desk.
 *
 * <p>Nothing here is saved. A claim is worth about ten seconds and the worker renews it constantly,
 * so writing it to disk would only mean loading a world into a desk that believes it is occupied by
 * somebody who is three chunks away and may not exist any more. An empty desk that fills itself back
 * up two seconds after the chunk loads is both simpler and more correct.
 */
public class OfficeSpaceBlockEntity extends BlockEntity implements Workplace {

    /** One desk, one person. The player asked for exactly this. */
    public static final int CAPACITY = 1;

    /** How long a claim survives without being renewed. The worker renews every two seconds. */
    private static final long CLAIM_TIMEOUT = 200L;

    private final Map<UUID, Long> workers = new HashMap<>();

    public OfficeSpaceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.OFFICE_SPACE.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  OfficeSpaceBlockEntity desk) {
        if (level.getGameTime() % 40L == 0L) {
            desk.expire(level.getGameTime());
        }
    }

    private void expire(long gameTime) {
        workers.entrySet().removeIf(entry -> gameTime - entry.getValue() > CLAIM_TIMEOUT);
    }

    @Override
    public int capacity() {
        return CAPACITY;
    }

    @Override
    public boolean checkIn(UUID worker, long gameTime) {
        expire(gameTime);
        if (!workers.containsKey(worker) && workers.size() >= CAPACITY) {
            return false;
        }
        workers.put(worker, gameTime);
        return true;
    }

    @Override
    public boolean employs(UUID worker) {
        return workers.containsKey(worker);
    }

    @Override
    public int staffed() {
        if (level != null) {
            expire(level.getGameTime());
        }
        return workers.size();
    }

    /** The cell in front of the desk: where the chair is drawn and where the worker stands. */
    @Override
    public BlockPos spotFor(UUID worker) {
        Direction facing = getBlockState().hasProperty(OfficeSpaceBlock.FACING)
                ? getBlockState().getValue(OfficeSpaceBlock.FACING)
                : Direction.NORTH;
        return worldPosition.relative(facing);
    }

    @Override
    public boolean seated() {
        return true;
    }
}
