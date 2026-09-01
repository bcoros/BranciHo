package com.branciho.citiesinlife.missile;

import com.branciho.citiesinlife.block.SealingBlock;
import com.branciho.citiesinlife.city.City;
import com.branciho.citiesinlife.city.CityData;
import com.branciho.citiesinlife.city.Diplomacy;
import com.branciho.citiesinlife.city.Sirens;
import com.branciho.citiesinlife.city.Relation;
import com.branciho.citiesinlife.entity.MissileEntity;
import com.branciho.citiesinlife.structure.Structure;
import com.branciho.citiesinlife.structure.StructureType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Everything between pressing launch and the warhead leaving the ground, and everything the other
 * side gets to do about it.
 *
 * <p>A launch is not an instant. The doors have to travel, and while they do the alarm is red, the
 * panels grind back a step at a time and anybody watching the silo knows exactly what is about to
 * happen. That delay is the only thing that makes a silo a <em>place</em> rather than a button, and
 * it is why the sealing blocks were worth reusing.
 *
 * <p>Nothing here is saved. A launch in progress is a few seconds; a server that stops in the
 * middle of one comes back with the doors open and no rocket, which the next tick notices and
 * closes. That is a better failure than persisting a half-launch and getting it wrong.
 */
public final class MissileDirector {

    /**
     * How long the whole roof takes to travel, however many panels it is made of.
     *
     * <p>A fixed duration rather than a fixed rate. The plate now crosses one panel at a time, so
     * a rate would make a wide silo take half a minute to open and a narrow one take two seconds;
     * dividing the duration by the distance gives both of them the same three-second door.
     */
    private static final int DOOR_TRAVEL_TICKS = 60;

    /** Floor and ceiling on that division, so neither extreme becomes silly. */
    private static final int MIN_STEP_TICKS = 1;
    private static final int MAX_STEP_TICKS = 12;

    /** How long the doors stay open after the rocket has gone. */
    private static final int LINGER_TICKS = 40;

    /** How often the sky is swept for things that are coming. */
    private static final int SCAN_INTERVAL = 10;

    /** The chance an interceptor that reaches its quarry actually stops it. */
    private static final float INTERCEPT_CHANCE = 0.5F;

    /** One silo in the middle of doing something. */
    private static final class Launch {

        private final UUID siloId;
        private final MissileKind kind;
        private final Vec3 target;
        private final UUID cityId;

        /**
         * How far the roof has slid, in quarter-panels: 0 shut, {@code travel(survey)} clear.
         *
         * <p>Not a per-panel step any more. The roof is one plate and this is its position, which
         * is the whole of why it now opens like a lid instead of like a comb.
         */
        private int doors;
        private int untilStep = 1;

        /** Set once the rocket is away; from then on this record only closes the doors. */
        private boolean fired;
        private int linger = LINGER_TICKS;

        private Launch(UUID siloId, MissileKind kind, Vec3 target, UUID cityId) {
            this.siloId = siloId;
            this.kind = kind;
            this.target = target;
            this.cityId = cityId;
        }
    }

    /** An interceptor on its way up, and what it is trying to reach. */
    private record Shot(UUID interceptor, UUID quarry, int rollAt) {
    }

    private static final Map<UUID, Launch> LAUNCHING = new HashMap<>();
    private static final List<Shot> SHOTS = new ArrayList<>();

    /** Incoming missiles a defender has already thrown something at. One try each. */
    private static final Set<UUID> ANSWERED = new HashSet<>();

    private MissileDirector() {
    }

    // ------------------------------------------------------------ the request

    /**
     * Somebody has pressed launch.
     *
     * <p>Every rule is checked here and nowhere else, because the client asked for this and a
     * client may ask for anything. Returns a translation key explaining the refusal, or null when
     * the silo is now opening its doors.
     */
    public static @Nullable String launch(MinecraftServer server, ServerPlayer player,
                                          UUID siloId, int chunkX, int chunkZ, MissileKind kind) {
        if (!kind.offensive()) {
            return "message.citiesinlife.missile_not_offensive";
        }
        CityData data = CityData.get(server);
        Structure silo = data.structure(siloId);
        if (silo == null || silo.type() != StructureType.MISSILE_SILO) {
            return "message.citiesinlife.missile_no_silo";
        }
        City mine = data.city(silo.cityId());
        if (mine == null || !mine.owner().equals(player.getUUID())) {
            return "message.citiesinlife.missile_not_yours";
        }
        if (LAUNCHING.containsKey(siloId)) {
            return "message.citiesinlife.missile_busy";
        }
        ServerLevel level = server.getLevel(silo.dimension());
        if (level == null || !level.isLoaded(silo.min())) {
            return "message.citiesinlife.missile_silo_unloaded";
        }

        long chunkKey = ChunkPos.asLong(chunkX, chunkZ);
        City struck = data.cityAtChunk(silo.dimension(), chunkKey);
        if (struck != null) {
            if (struck.id().equals(mine.id())) {
                // The one rule with no exceptions worth arguing about.
                return "message.citiesinlife.missile_not_yourself";
            }
            if (Diplomacy.stance(struck, mine) != Relation.WAR) {
                return "message.citiesinlife.missile_not_at_war";
            }
        }

        SiloSurvey survey = SiloSurvey.of(level, silo);
        if (survey.tooLarge()) {
            return "message.citiesinlife.missile_silo_too_large";
        }
        if (survey.next(kind) == null) {
            return "message.citiesinlife.missile_none_loaded";
        }

        ChunkPos chunk = new ChunkPos(chunkKey);
        Vec3 target = new Vec3(chunk.getMiddleBlockX() + 0.5D,
                level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,
                        chunk.getMiddleBlockX(), chunk.getMiddleBlockZ()),
                chunk.getMiddleBlockZ() + 0.5D);

        LAUNCHING.put(siloId, new Launch(siloId, kind, target, mine.id()));
        sound(level, silo.min(), SoundEvents.IRON_DOOR_OPEN, 2.0F, 0.5F);
        return null;
    }

    // -------------------------------------------------------------- the tick

    public static void tick(MinecraftServer server) {
        if (!LAUNCHING.isEmpty()) {
            stepDoors(server);
        }
        if (server.getTickCount() % SCAN_INTERVAL == 0) {
            watchTheSky(server);
        }
        resolveShots(server);
    }

    /** Walk every silo that is mid-launch on one step of its sequence. */
    private static void stepDoors(MinecraftServer server) {
        CityData data = CityData.get(server);
        Iterator<Map.Entry<UUID, Launch>> pending = LAUNCHING.entrySet().iterator();
        while (pending.hasNext()) {
            Launch launch = pending.next().getValue();
            Structure silo = data.structure(launch.siloId);
            ServerLevel level = silo == null ? null : server.getLevel(silo.dimension());
            if (silo == null || level == null || !level.isLoaded(silo.min())) {
                // The silo was demolished, or the ground it stands on went away. Nothing left to
                // open and nothing left to fire.
                pending.remove();
                continue;
            }
            // Counted every tick, not every step. The step gate is however long one quarter of a
            // panel takes, which now depends on how wide the roof is - so leaving the wait on the
            // far side of it made the doors stand open for sixteen seconds over a narrow silo and
            // two over a wide one, for no reason the player could see.
            if (launch.fired && launch.linger > 0) {
                launch.linger--;
                continue;
            }
            if (--launch.untilStep > 0) {
                continue;
            }

            SiloSurvey survey = SiloSurvey.of(level, silo);
            int travel = travel(survey);
            launch.untilStep = stepTicks(travel);
            if (!launch.fired) {
                if (launch.doors < travel) {
                    launch.doors++;
                    paintDoors(level, survey, launch.doors);
                    // Once per panel crossed, not once per quarter of one: the grind is the plate
                    // reaching the next panel, and four of them a panel is a rattle.
                    if (launch.doors % SealingBlock.FULLY_OPEN == 1) {
                        sound(level, silo.min(), SoundEvents.PISTON_EXTEND, 1.6F, 0.55F);
                    }
                    continue;
                }
                fire(server, level, silo, survey, launch);
                launch.fired = true;
                continue;
            }
            if (launch.doors > 0) {
                launch.doors--;
                paintDoors(level, survey, launch.doors);
                if (launch.doors % SealingBlock.FULLY_OPEN == 0) {
                    sound(level, silo.min(), SoundEvents.PISTON_CONTRACT, 1.6F, 0.55F);
                }
                continue;
            }
            sound(level, silo.min(), SoundEvents.IRON_DOOR_CLOSE, 2.0F, 0.5F);
            pending.remove();
        }
    }

    /** Take the rocket off the pad and put it in the air. */
    private static void fire(MinecraftServer server, ServerLevel level, Structure silo,
                             SiloSurvey survey, Launch launch) {
        BlockPos pad = survey.next(launch.kind);
        if (pad == null) {
            // Somebody mined it while the doors were travelling. Fair enough.
            return;
        }
        MissileEntity rocket = MissileEntity.create(level);
        if (rocket == null) {
            return;
        }
        level.setBlock(pad, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        Vec3 origin = new Vec3(pad.getX() + 0.5D, pad.getY(), pad.getZ() + 0.5D);
        rocket.aim(launch.kind, origin, launch.target, launch.cityId);
        level.addFreshEntity(rocket);

        sound(level, pad, SoundEvents.FIREWORK_ROCKET_LAUNCH, 6.0F, 0.5F);
        sound(level, pad, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 6.0F, 0.4F);

        City mine = CityData.get(server).city(launch.cityId);
        if (mine != null) {
            Component line = Component.translatable("message.citiesinlife.missile_launched",
                    mine.name(), launch.kind.displayName()).withStyle(ChatFormatting.RED);
            for (ServerPlayer everyone : server.getPlayerList().getPlayers()) {
                everyone.sendSystemMessage(line);
            }
        }
    }

    // ------------------------------------------------------------ the defence

    /**
     * Find everything in the air and decide who should be worried about it.
     *
     * <p>Walked over the level's whole entity list rather than a box, because there is no box: a
     * missile might be anywhere between two cities. It is affordable because it runs once every ten
     * ticks and because there are almost never any missiles — the list is empty in the ordinary
     * case, which is every case except a war.
     */
    private static void watchTheSky(MinecraftServer server) {
        CityData data = CityData.get(server);
        Set<UUID> threatened = new HashSet<>();

        for (ServerLevel level : server.getAllLevels()) {
            List<? extends MissileEntity> flying = level.getEntities(
                    EntityTypeTest.forClass(MissileEntity.class),
                    rocket -> rocket.isAlive() && rocket.kind().offensive());
            for (MissileEntity rocket : flying) {
                BlockPos aim = BlockPos.containing(rocket.target());
                City struck = Diplomacy.owner(server, level.dimension(), aim);
                if (struck == null) {
                    continue;
                }
                threatened.add(struck.id());
                warn(server, struck, rocket);
                if (ANSWERED.add(rocket.getUUID())) {
                    answer(server, data, level, struck, rocket);
                }
            }
        }

        // Handed over rather than acted on. Something in the air is one of four reasons a city's
        // sirens are up and this sweep is the only code that can know about that one, so it says
        // so and stops there - it is not the missile director's business whether the reactor is
        // also on fire.
        Sirens.threaten(threatened);
    }

    /** Tell the owner what is coming and how long they have. */
    private static void warn(MinecraftServer server, City struck, MissileEntity rocket) {
        ServerPlayer owner = server.getPlayerList().getPlayer(struck.owner());
        if (owner == null) {
            return;
        }
        int seconds = rocket.ticksToImpact() / 20;
        // Once every two seconds while it is in the air. Often enough to be a countdown, rarely
        // enough not to be spam.
        if (server.getTickCount() % 40 != 0) {
            return;
        }
        BlockPos aim = BlockPos.containing(rocket.target());
        owner.sendSystemMessage(Component.translatable("message.citiesinlife.missile_incoming",
                rocket.kind().displayName(), aim.getX(), aim.getZ(), seconds)
                .withStyle(ChatFormatting.RED));
    }

    /**
     * Throw something back, automatically.
     *
     * <p>Nobody presses a button for this. An interceptor exists to answer while you are asleep,
     * and asking a player to notice a countdown and react to it would make the whole system a
     * reflex test between two people who may not even be online at the same time.
     */
    private static void answer(MinecraftServer server, CityData data, ServerLevel level,
                               City struck, MissileEntity incoming) {
        for (Structure silo : data.structuresOf(struck)) {
            if (silo.type() != StructureType.MISSILE_SILO
                    || !silo.dimension().equals(level.dimension())
                    || !level.isLoaded(silo.min())) {
                continue;
            }
            SiloSurvey survey = SiloSurvey.of(level, silo);
            BlockPos pad = survey.tooLarge() ? null : survey.next(MissileKind.INTERCEPTOR);
            if (pad == null) {
                continue;
            }
            MissileEntity shot = MissileEntity.create(level);
            if (shot == null) {
                return;
            }
            // Consumed whether or not it connects. That is what makes stocking them a decision.
            level.setBlock(pad, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            Vec3 origin = new Vec3(pad.getX() + 0.5D, pad.getY(), pad.getZ() + 0.5D);
            shot.chase(origin, incoming);
            level.addFreshEntity(shot);
            sound(level, pad, SoundEvents.FIREWORK_ROCKET_LAUNCH, 4.0F, 0.8F);

            SHOTS.add(new Shot(shot.getUUID(), incoming.getUUID(),
                    server.getTickCount() + shot.ticksToImpact()));
            return;
        }
    }

    /** Roll for every interceptor that has reached the point it was aimed at. */
    private static void resolveShots(MinecraftServer server) {
        if (SHOTS.isEmpty()) {
            return;
        }
        Iterator<Shot> shots = SHOTS.iterator();
        while (shots.hasNext()) {
            Shot shot = shots.next();
            if (server.getTickCount() < shot.rollAt()) {
                continue;
            }
            shots.remove();
            if (server.overworld().random.nextFloat() >= INTERCEPT_CHANCE) {
                continue;
            }
            for (ServerLevel level : server.getAllLevels()) {
                if (level.getEntity(shot.quarry()) instanceof MissileEntity quarry) {
                    quarry.intercepted();
                    ANSWERED.remove(shot.quarry());
                    break;
                }
            }
        }
    }

    // ------------------------------------------------------------- the blocks

    /**
     * Slide the roof back.
     *
     * <p>The panels are not a row of separate shutters that each shrink where they stand. They are
     * <b>one plate</b>, and {@code travel} is how far it has slid, measured in quarter-panels. A
     * panel is whole until the plate's trailing edge reaches it, spends four steps being crossed,
     * and is gone once the edge is past — so what you see is a solid roof with one straight edge
     * sweeping across it and open sky behind.
     *
     * <p>Setting every panel to the same step is what it used to do, and it is why the roof came
     * apart into a comb of bars with daylight between them: each panel was opening its own little
     * door in the middle of the ceiling instead of all of them being the same door.
     *
     * <p>The plate travels towards -Z, because that is the direction the models shorten in. Which
     * way a silo faces is not asked, and does not need to be: a lid has to go somewhere, and every
     * lid in the mod going the same way is one fewer blockstate on thirty blocks.
     */
    private static void paintDoors(ServerLevel level, SiloSurvey survey, int travel) {
        int back = back(survey);
        for (BlockPos at : survey.seals()) {
            BlockState was = level.getBlockState(at);
            if (!was.hasProperty(SealingBlock.OPEN)) {
                continue;
            }
            int step = Mth.clamp(travel - (back - at.getZ()) * SealingBlock.FULLY_OPEN,
                    0, SealingBlock.FULLY_OPEN);
            if (was.getValue(SealingBlock.OPEN) != step) {
                level.setBlock(at, was.setValue(SealingBlock.OPEN, step), Block.UPDATE_CLIENTS);
            }
        }
    }

    /** The trailing edge of the roof: the panel row the plate starts from. */
    private static int back(SiloSurvey survey) {
        int back = Integer.MIN_VALUE;
        for (BlockPos at : survey.seals()) {
            back = Math.max(back, at.getZ());
        }
        return back;
    }

    /** How far the plate has to go to clear the shaft, in quarter-panels. */
    private static int travel(SiloSurvey survey) {
        if (survey.seals().isEmpty()) {
            // An open pad. There is nothing to move and nothing to wait for.
            return 0;
        }
        int back = Integer.MIN_VALUE;
        int front = Integer.MAX_VALUE;
        for (BlockPos at : survey.seals()) {
            back = Math.max(back, at.getZ());
            front = Math.min(front, at.getZ());
        }
        return (back - front + 1) * SealingBlock.FULLY_OPEN;
    }

    /** Spread the door's travel over a fixed few seconds, whatever distance it has to cover. */
    private static int stepTicks(int travel) {
        return Mth.clamp(DOOR_TRAVEL_TICKS / Math.max(1, travel), MIN_STEP_TICKS, MAX_STEP_TICKS);
    }


    private static void sound(Level level, BlockPos at, net.minecraft.sounds.SoundEvent event,
                              float volume, float pitch) {
        level.playSound(null, at, event, SoundSource.BLOCKS, volume, pitch);
    }

    /** Whether this silo is currently opening, firing or closing. */
    public static boolean busy(UUID siloId) {
        return LAUNCHING.containsKey(siloId);
    }

    /** Dropped with the world, so a new one does not inherit a half-open silo. */
    public static void clear() {
        LAUNCHING.clear();
        SHOTS.clear();
        ANSWERED.clear();
    }
}
