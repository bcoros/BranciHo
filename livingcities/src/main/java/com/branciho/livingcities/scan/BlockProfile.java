package com.branciho.livingcities.scan;

/**
 * Everything the floor detector needs to know about one distinct {@code BlockState}, flattened into
 * primitives.
 *
 * <p>This record is the boundary between "code that talks to Minecraft" and "code that does maths".
 * {@link BlockProfiler} fills it in on the server thread, once per distinct state in a selection;
 * {@link FloorAnalyzer} then reads it millions of times from a plain array on a worker thread. It
 * holds no reference to a level, a position or a block, which is what makes that hand-off safe by
 * construction rather than by convention.
 *
 * <p>All the predicates below are pure functions of these fields. They are the vocabulary the
 * scanner reasons in, and they are the only place a judgement like "you can stand on this" is made.
 */
public record BlockProfile(
        BlockClass blockClass,
        // Highest point of the collision shape, 0..1 (above 1 for fences). 0 when there is none.
        float collisionTopY,
        // Bounding-box volume of the collision shape; a cheap "how much does this obstruct" scalar.
        float collisionVolume,
        // isFaceSturdy(UP): does the top face cover the full 1x1 area.
        boolean sturdyTop,
        boolean blocksMotion,
        boolean occludes,
        boolean solidRender,
        // Machinery and furniture marker: such a cell is occupied, not void.
        boolean hasBlockEntity,
        // The shape differed between two sampled positions, so this profile is an approximation.
        boolean positionSensitive,
        int lightEmission) {

    /**
     * A step of half a block is the threshold Minecraft itself uses for "walk up without jumping",
     * so it is also the line between "your feet are in this cell" and "your feet are in the one
     * above".
     */
    public static final float STEP_HEIGHT = 0.5F;

    /** Shared profile for air, which is the overwhelming majority of every selection. */
    public static final BlockProfile AIR =
            new BlockProfile(BlockClass.OPEN, 0.0F, 0.0F, false, false, false, false, false, false, 0);

    /**
     * Shared profile for a technical block that must not shape the building at all.
     *
     * <p>Named {@code TECHNICAL} rather than {@code EXCLUDED} so it can never be confused with the
     * {@link BlockClass#EXCLUDED} case label a few lines below.
     */
    public static final BlockProfile TECHNICAL =
            new BlockProfile(BlockClass.EXCLUDED, 0.0F, 0.0F, false, false, false, false, false, false, 0);

    /**
     * Fallback when a block throws while being profiled.
     *
     * <p>Deliberately pessimistic: it blocks movement (so it cannot silently inflate usable area)
     * but supports and seals nothing (so it cannot silently invent a floor either).
     */
    public static BlockProfile unknown() {
        return new BlockProfile(BlockClass.UNKNOWN, 1.0F, 1.0F, false, true, false, false, false, false, 0);
    }

    public BlockProfile withPositionSensitive(boolean sensitive) {
        return sensitive == positionSensitive ? this : new BlockProfile(blockClass, collisionTopY,
                collisionVolume, sturdyTop, blocksMotion, occludes, solidRender, hasBlockEntity, sensitive,
                lightEmission);
    }

    // ------------------------------------------------------------------ cell predicates

    /**
     * Where a player's feet end up if they stand on the block at {@code y}.
     *
     * <p>This one rule is what makes stairs, slabs and carpets work without naming any of them:
     * stand on top of a stair or an upper slab and you are in the cell above; walk onto a carpet or
     * a pressure plate and you are in that same cell, supported by whatever is beneath it.
     */
    public int footY(int y) {
        return collisionTopY >= STEP_HEIGHT ? y + 1 : y;
    }

    /** Does this block form a real standing surface? */
    public boolean supports() {
        return switch (blockClass) {
            case SOLID_SUPPORT, TRANSPARENT_SOLID, TOP_SUPPORT -> true;
            // Lower slabs and chair-like furniture: a full top face or a half-block step is enough.
            case PARTIAL -> sturdyTop || collisionTopY >= STEP_HEIGHT;
            default -> false;
        };
    }

    /** Can a body occupy this cell? */
    public boolean headClear() {
        return switch (blockClass) {
            case OPEN, DECOR_PASSABLE, EXCLUDED -> true;
            default -> !blocksMotion && collisionTopY < STEP_HEIGHT;
        };
    }

    /**
     * Does this block close the building envelope?
     *
     * <p>Doors count regardless of whether they are open, which is the single most important
     * exception in the whole algorithm - see {@link BlockClass#DOOR_LIKE}.
     */
    public boolean seals() {
        return switch (blockClass) {
            case SOLID_SUPPORT, TRANSPARENT_SOLID, DOOR_LIKE -> true;
            // Fences, panes and walls do not fill their cell but you cannot walk through them.
            case TOP_SUPPORT, PARTIAL -> blocksMotion;
            default -> false;
        };
    }

    /**
     * How good a standing surface this is, 0..1.
     *
     * <p>A landing made of stairs is real floor but it is not as usable as a plank floor, and this
     * is what stops a staircase from out-voting an actual storey in the height histogram.
     */
    public float surfaceWeight() {
        return switch (blockClass) {
            case SOLID_SUPPORT, TRANSPARENT_SOLID -> 1.00F;
            case TOP_SUPPORT -> 0.90F;
            case PARTIAL -> 0.60F;
            default -> 0.00F;
        };
    }

    /**
     * Does this cell hold something that occupies working or living space without being standable -
     * a desk, a bed, a machine?
     *
     * <p>Reserved for the furnished-area refinement: furnishing a building must never reduce its
     * capacity, or the mod punishes exactly the players it is built for.
     */
    public boolean furnishing() {
        return !supports() && (blockClass == BlockClass.PARTIAL || hasBlockEntity);
    }

    /** True when two samples of the same state agree on geometry, i.e. the state is position-stable. */
    public boolean geometryEquals(BlockProfile other) {
        return blockClass == other.blockClass
                && collisionTopY == other.collisionTopY
                && collisionVolume == other.collisionVolume
                && sturdyTop == other.sturdyTop
                && blocksMotion == other.blocksMotion
                && occludes == other.occludes
                && solidRender == other.solidRender;
    }
}
