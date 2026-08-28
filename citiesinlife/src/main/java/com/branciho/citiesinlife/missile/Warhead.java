package com.branciho.citiesinlife.missile;

import com.branciho.citiesinlife.city.CityData;
import com.branciho.citiesinlife.city.Demolition;
import com.branciho.citiesinlife.config.CitiesInLifeConfig;
import com.branciho.citiesinlife.nuclear.Radiation;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * What arrives.
 *
 * <p>The crater is carved by hand rather than left to a vanilla explosion, for the reason the
 * meltdown learned the hard way: a vanilla blast loses {@code (resistance + 0.3) * 0.3} of its
 * power every 0.3 blocks, and water's resistance is a hundred — so a single block of water eats an
 * entire thirty-power blast. A weapon that a pond could switch off is not a weapon. The explosion
 * still happens, at {@code ExplosionInteraction.NONE}, for the flash and the shove and the noise;
 * the ground is taken by arithmetic.
 *
 * <p>And it is taken <b>over several seconds</b>. An eighty-block sphere is about two million
 * blocks. Setting those in one tick would stop the server dead, so the crater opens as a shell per
 * tick with each shell removing the same volume as the last — which is both affordable and what an
 * expanding blast front actually does. That is why {@link #tick} exists and why the impact returns
 * before the hole is finished.
 */
public final class Warhead {

    /** How many ticks a crater takes to open. Two seconds of it visibly expanding. */
    private static final int WAVE_TICKS = 40;

    /** How high above the impact the cap sits, in cloud radii. */
    private static final double CLOUD_HEIGHT = 1.9D;

    /**
     * The largest the cloud is ever drawn, whatever the crater is.
     *
     * <p>Roughly the meltdown's own radius, which is the biggest cloud in the mod that anybody has
     * actually looked at and liked. Past this a mushroom stops looking bigger and starts looking
     * like fog.
     */
    private static final double CLOUD_CAP = 46.0D;

    /** One crater in progress: where, how big, and how far through it is. */
    private static final class Crater {

        private final ServerLevel level;
        private final BlockPos at;
        private final double radius;
        private int step;

        private Crater(ServerLevel level, BlockPos at, double radius) {
            this.level = level;
            this.at = at;
            this.radius = radius;
        }
    }

    private static final List<Crater> OPENING = new ArrayList<>();

    private Warhead() {
    }

    // ------------------------------------------------------------ the impact

    /**
     * A warhead has reached the ground.
     *
     * <p>Everything instantaneous happens here — the flash, the sound, the fallout, telling the
     * world — and the hole is queued. Whoever fired it is named so the announcement can say so;
     * null is allowed, because a missile whose city was deleted mid-flight still lands.
     */
    public static void detonate(ServerLevel level, BlockPos at, MissileKind kind,
                                @Nullable UUID firedBy) {
        double radius = kind.crater() * CitiesInLifeConfig.nuclearBlastScale();

        // The first shell also tells the road network, the path network and every registered
        // building that this ground has gone. None of those are blocks, so nothing about an
        // explosion would otherwise reach them.
        int reach = Mth.ceil(radius);
        Demolition.flatten(level, at.offset(-reach, -reach, -reach), at.offset(reach, reach, reach));
        OPENING.add(new Crater(level, at, radius));

        // NONE, not BLOCK. The ray-casting is the part water defeats, and the crater has the
        // ground; what vanilla is good at is hurling and hurting whatever is standing nearby.
        level.explode(null, at.getX() + 0.5D, at.getY() + 0.5D, at.getZ() + 0.5D,
                kind.power() * (float) CitiesInLifeConfig.nuclearBlastScale(), kind.nuclear(),
                Level.ExplosionInteraction.NONE);

        cloud(level, at, radius, kind.nuclear());
        bang(level, at, kind.nuclear() ? 8.0F : 4.0F, kind.nuclear() ? 0.28F : 0.42F);

        if (kind.nuclear()) {
            // Half a kilometre of fallout for ten minutes. Taking the ground is not the same as
            // being able to use it.
            Radiation.fallout(level, at);
        }
        announce(level.getServer(), level, at, kind, firedBy);
    }

    /** The noise, and the far-off rumble for everybody outside the blast. */
    public static void bang(ServerLevel level, BlockPos at, float volume, float pitch) {
        level.playSound(null, at.getX(), at.getY(), at.getZ(),
                SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, volume, pitch);
        level.playSound(null, at.getX(), at.getY(), at.getZ(),
                SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, volume, 0.5F);
    }

    // ------------------------------------------------------------- the crater

    /** Every server tick. Does nothing at all unless a hole is currently opening. */
    public static void tick(MinecraftServer server) {
        if (OPENING.isEmpty()) {
            return;
        }
        Iterator<Crater> craters = OPENING.iterator();
        while (craters.hasNext()) {
            Crater crater = craters.next();
            if (crater.step >= WAVE_TICKS) {
                craters.remove();
                continue;
            }
            wave(crater.level, crater.at, crater.radius, crater.step, WAVE_TICKS);
            crater.step++;
        }
    }

    /**
     * One shell of an expanding crater.
     *
     * <p>Radii by cube root, so every step removes the same volume and the wave front slows as it
     * widens — which is what an expanding shell of anything does.
     */
    private static void wave(ServerLevel level, BlockPos centre, double radius, int step,
                             int steps) {
        double inner = radius * Math.cbrt((double) step / steps);
        double outer = radius * Math.cbrt((double) (step + 1) / steps);
        carve(level, centre, inner, outer, radius);
    }

    /**
     * Take one spherical shell of ground out.
     *
     * <p>Lifted from the meltdown, including every guard on it. The loop bounds are solved from the
     * sphere equation rather than scanned as a bounding box, so a thin outer shell costs about what
     * its own volume costs; the hollow middle is jumped over rather than tested; and the rim is
     * feathered against the crater's <em>final</em> radius, not this shell's — feathering each
     * shell against its own edge eats holes through the middle, because from halfway out every
     * shell is the rim.
     */
    private static void carve(ServerLevel level, BlockPos centre, double inner, double outer,
                              double full) {
        double outerSq = outer * outer;
        double innerSq = inner * inner;
        double rim = full * 0.90D;
        double rimSq = rim * rim;
        double feather = Math.max(0.001D, full - rim);
        RandomSource random = level.random;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int reach = Mth.ceil(outer);
        int floor = level.getMinBuildHeight();
        int ceiling = level.getMaxBuildHeight() - 1;

        for (int dx = -reach; dx <= reach; dx++) {
            double leftY = outerSq - (double) dx * dx;
            if (leftY < 0.0D) {
                continue;
            }
            int spanY = (int) Math.sqrt(leftY);
            for (int dy = -spanY; dy <= spanY; dy++) {
                int y = centre.getY() + dy;
                if (y < floor || y > ceiling) {
                    continue;
                }
                double flat = (double) dx * dx + (double) dy * dy;
                double leftZ = outerSq - flat;
                if (leftZ < 0.0D) {
                    continue;
                }
                int spanZ = (int) Math.sqrt(leftZ);
                double keptZ = innerSq - flat;
                int skip = keptZ > 0.0D ? (int) Math.sqrt(keptZ) : -1;
                for (int dz = -spanZ; dz <= spanZ; dz++) {
                    if (skip >= 0 && dz > -skip && dz < skip) {
                        dz = skip - 1;
                        continue;
                    }
                    double distSq = flat + (double) dz * dz;
                    if (distSq < innerSq) {
                        continue;
                    }
                    if (distSq > rimSq) {
                        double out = (Math.sqrt(distSq) - rim) / feather;
                        if (random.nextFloat() < out * out * 0.9D) {
                            continue;
                        }
                    }
                    cursor.set(centre.getX() + dx, y, centre.getZ() + dz);
                    BlockState state = level.getBlockState(cursor);
                    if (state.isAir()) {
                        continue;
                    }
                    // Bedrock and the like stop a warhead the same way they stop a meltdown.
                    if (state.getDestroySpeed(level, cursor) < 0.0F) {
                        continue;
                    }
                    level.setBlock(cursor, Blocks.AIR.defaultBlockState(), CARVE_FLAGS);
                }
            }
        }
    }

    /**
     * UPDATE_ALL on two million blocks would be two million water-flow and light recalculations.
     */
    private static final int CARVE_FLAGS =
            Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

    // -------------------------------------------------------------- the sight

    /**
     * The mushroom, and the flash that reaches the whole render distance.
     *
     * <p>Sent per player with the force flag, because the ordinary {@code sendParticles} drops
     * anything more than thirty-two blocks from the viewer — which for an eighty-block crater
     * means the person it is aimed at is the one person who cannot see it.
     */
    private static void cloud(ServerLevel level, BlockPos at, double radius, boolean nuclear) {
        double x = at.getX() + 0.5D;
        double y = at.getY() + 0.5D;
        double z = at.getZ() + 0.5D;
        // The cloud does NOT scale with the crater, and that is the fix rather than an oversight.
        // Driven off an eighty-block radius the stem climbed nearly three hundred blocks and each
        // ring was scattered over a hundred-block cube - which is not a big mushroom cloud, it is
        // no mushroom cloud at all, because every particle is somewhere else. Capped, it reads as
        // a column with a cap on it, which is the entire job.
        double shape = Math.min(radius, CLOUD_CAP);
        for (ServerPlayer player : level.players()) {
            far(level, player, ParticleTypes.FLASH, x, y, z, 6, radius * 0.25D, 0.0D);
            far(level, player, ParticleTypes.EXPLOSION_EMITTER, x, y, z, 12, radius * 0.35D, 0.0D);
            if (!nuclear) {
                far(level, player, ParticleTypes.LARGE_SMOKE, x, y + shape * 0.4D, z,
                        40, shape * 0.4D, 0.06D);
                continue;
            }
            // The stem, then the cap: a tight column of smoke that widens sharply near the top.
            for (int i = 0; i < 26; i++) {
                double height = y + i * (shape * 0.10D);
                double spread = shape * (i < 17 ? 0.10D : 0.34D);
                far(level, player, ParticleTypes.LARGE_SMOKE, x, height, z, 24, spread, 0.05D);
            }
            far(level, player, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, x, y + shape * CLOUD_HEIGHT, z,
                    90, shape * 0.5D, 0.02D);
        }
    }

    private static void far(ServerLevel level, ServerPlayer player, SimpleParticleType type,
                            double x, double y, double z, int count, double spread, double speed) {
        level.sendParticles(player, type, true, x, y, z, count, spread, spread * 0.4D, spread,
                speed);
    }

    /**
     * Tell everybody.
     *
     * <p>A warhead going off is the whole server's business, the same way a declaration of war is.
     * The message names the target's city where there is one, because "somewhere" is not news.
     */
    private static void announce(MinecraftServer server, ServerLevel level, BlockPos at,
                                 MissileKind kind, @Nullable UUID firedBy) {
        CityData data = CityData.get(server);
        String attacker = firedBy == null || data.city(firedBy) == null
                ? Component.translatable("message.citiesinlife.unknown_city").getString()
                : data.city(firedBy).name();
        var struck = com.branciho.citiesinlife.city.Diplomacy.owner(server, level.dimension(), at);
        Component line = Component.translatable(
                struck == null
                        ? "message.citiesinlife.missile_landed_wild"
                        : "message.citiesinlife.missile_landed",
                kind.displayName(), attacker,
                struck == null ? "" : struck.name()).withStyle(ChatFormatting.RED);
        for (ServerPlayer everyone : server.getPlayerList().getPlayers()) {
            everyone.sendSystemMessage(line);
        }
    }

    /** Dropped with the world, so a new one does not inherit a half-opened crater. */
    public static void clear() {
        OPENING.clear();
    }
}
