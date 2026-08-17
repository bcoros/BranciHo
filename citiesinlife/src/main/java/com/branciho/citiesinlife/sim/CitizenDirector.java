package com.branciho.citiesinlife.sim;

import com.branciho.citiesinlife.city.City;
import com.branciho.citiesinlife.city.CityData;
import com.branciho.citiesinlife.config.CitiesInLifeConfig;
import com.branciho.citiesinlife.entity.CitizenEntity;
import com.branciho.citiesinlife.registry.ModEntities;
import com.branciho.citiesinlife.structure.Structure;
import com.branciho.citiesinlife.structure.StructureType;
import com.branciho.citiesinlife.work.Workplace;
import com.branciho.citiesinlife.work.WorkplaceRegistry;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * How many people a city actually has walking about, and what they are doing.
 *
 * <p>The city's population is a number in the tens of thousands. This decides which fifteen of them
 * you can see. Everything about that is deliberately a sample rather than a simulation: nothing in
 * the economy reads how many citizens are loaded, so a player who turns them down to two — or off —
 * loses the sight of their city and nothing else about it.
 *
 * <p>A citizen needs somewhere to sleep before it exists at all. The player was explicit that a
 * residential building spawns nobody without an actual Minecraft bed in it, and that turns out to be
 * a good rule for its own sake: it makes the inside of a house matter, which nothing else in the mod
 * had managed to do.
 *
 * <p>Factories get nobody, on purpose. A factory's workers are virtual and always were; putting
 * bodies in one would mean two different answers to "how many people work here" and only one of them
 * would be the one the economy used.
 */
public final class CitizenDirector {

    /** How often this runs. Five seconds — spawning is not something to do in a hurry. */
    public static final int INTERVAL_TICKS = 100;

    /** A citizen only appears if somebody is close enough to plausibly have seen it walk in. */
    private static final int SPAWN_NEAR_PLAYER = 96;

    /** Most blocks searched for a bed in one go, so a huge apartment block cannot stall a tick. */
    private static final int MAX_BED_SCAN = 32_768;

    /** Enough beds to fill any city's cap several times over; no reason to find them all. */
    private static final int MAX_BEDS_FOUND = 24;

    /** How far a citizen will walk to a job. Beyond this it is somebody else's commute. */
    private static final int MAX_COMMUTE = 160;

    /** Roughly one till worker in three is on nights, so a shop can be open at odd hours. */
    private static final int NIGHT_SHIFT_ODDS = 3;

    private CitizenDirector() {
    }

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % INTERVAL_TICKS != 0) {
            return;
        }
        int cap = CitiesInLifeConfig.citizensPerCity();
        CityData data = CityData.get(server);

        for (City city : data.cities()) {
            ServerLevel level = server.getLevel(city.dimension());
            if (level == null) {
                continue;
            }
            List<CitizenEntity> citizens = new ArrayList<>(level.getEntities(ModEntities.CITIZEN.get(),
                    citizen -> citizen.isAlive() && city.id().equals(citizen.cityId())));

            // Turning the setting down has to take effect on the citizens already out there, not
            // just on the next one who would have been spawned.
            for (int i = citizens.size() - 1; i >= cap; i--) {
                citizens.remove(i).discard();
            }
            if (cap == 0) {
                continue;
            }

            assignJobs(server, level, data, city, citizens);

            if (citizens.size() < cap) {
                trySpawn(level, data, city, citizens);
            }
        }
    }

    // --------------------------------------------------------------- spawning

    private static void trySpawn(ServerLevel level, CityData data, City city,
                                 List<CitizenEntity> existing) {
        List<Structure> homes = new ArrayList<>();
        for (Structure structure : data.structuresOf(city)) {
            if (structure.type() == StructureType.RESIDENTIAL
                    && structure.dimension().equals(level.dimension())) {
                homes.add(structure);
            }
        }
        if (homes.isEmpty()) {
            return;
        }

        // One building per pass. Scanning every home in a city for beds, every five seconds,
        // forever, would cost more than everything else this mod does put together.
        Structure home = homes.get(level.random.nextInt(homes.size()));
        if (!level.isLoaded(home.min()) || !level.isLoaded(home.max())) {
            return;
        }

        LongArrayList beds = findBeds(level, home);
        if (beds.isEmpty()) {
            return;
        }

        for (int attempt = 0; attempt < beds.size(); attempt++) {
            BlockPos bed = BlockPos.of(beds.getLong(level.random.nextInt(beds.size())));
            if (taken(existing, bed)) {
                continue;
            }
            if (level.getNearestPlayer(bed.getX() + 0.5D, bed.getY() + 0.5D, bed.getZ() + 0.5D,
                    SPAWN_NEAR_PLAYER, false) == null) {
                return;
            }
            BlockPos spot = freeSpotNear(level, bed);
            if (spot == null) {
                continue;
            }
            spawn(level, city, bed, spot);
            return;
        }
    }

    private static boolean taken(List<CitizenEntity> citizens, BlockPos bed) {
        for (CitizenEntity citizen : citizens) {
            if (bed.equals(citizen.home())) {
                return true;
            }
        }
        return false;
    }

    private static void spawn(ServerLevel level, City city, BlockPos bed, BlockPos spot) {
        CitizenEntity citizen = ModEntities.CITIZEN.get().create(level);
        if (citizen == null) {
            return;
        }
        citizen.moveTo(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D,
                level.random.nextFloat() * 360.0F, 0.0F);
        citizen.setCityId(city.id());
        citizen.setHome(bed);
        citizen.setSkin(level.random.nextInt(CitizenEntity.SKINS));
        citizen.finalizeSpawn(level, level.getCurrentDifficultyAt(spot),
                MobSpawnType.MOB_SUMMONED, null);
        level.addFreshEntity(citizen);
    }

    /**
     * Every bed head inside a building.
     *
     * <p>Heads only. A bed is two blocks and counting both would double every house's population,
     * which is exactly the sort of arithmetic mistake that is invisible until somebody wonders why
     * their two-bed cottage has four people in it.
     */
    private static LongArrayList findBeds(ServerLevel level, Structure structure) {
        LongArrayList found = new LongArrayList();
        BlockPos min = structure.min();
        BlockPos max = structure.max();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int scanned = 0;

        for (int y = min.getY(); y <= max.getY(); y++) {
            for (int x = min.getX(); x <= max.getX(); x++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    if (++scanned > MAX_BED_SCAN) {
                        return found;
                    }
                    cursor.set(x, y, z);
                    BlockState state = level.getBlockState(cursor);
                    if (state.getBlock() instanceof BedBlock
                            && state.getValue(BedBlock.PART) == BedPart.HEAD) {
                        found.add(cursor.asLong());
                        if (found.size() >= MAX_BEDS_FOUND) {
                            return found;
                        }
                    }
                }
            }
        }
        return found;
    }

    /** Somewhere beside the bed with room to stand up in. */
    private static @Nullable BlockPos freeSpotNear(ServerLevel level, BlockPos bed) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos candidate = bed.relative(direction);
            if (roomToStand(level, candidate)) {
                return candidate;
            }
            if (roomToStand(level, candidate.above())) {
                return candidate.above();
            }
        }
        return roomToStand(level, bed.above()) ? bed.above() : null;
    }

    private static boolean roomToStand(ServerLevel level, BlockPos pos) {
        return level.isLoaded(pos)
                && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
                && !level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty();
    }

    // ------------------------------------------------------------------- jobs

    /**
     * Hand out whatever jobs are going.
     *
     * <p>Only desks in registered offices and tills in registered shops count. A desk in a field is
     * furniture, and a desk in a factory is somebody being confused about what a factory is.
     */
    private static void assignJobs(MinecraftServer server, ServerLevel level, CityData data,
                                   City city, List<CitizenEntity> citizens) {
        List<CitizenEntity> jobless = new ArrayList<>();
        for (CitizenEntity citizen : citizens) {
            if (citizen.workstation() == null) {
                jobless.add(citizen);
            }
        }
        if (jobless.isEmpty()) {
            return;
        }

        WorkplaceRegistry registry = WorkplaceRegistry.get(server);
        List<BlockPos> desks = vacancies(level, data, city,
                registry.offices(level.dimension()), StructureType.BUSINESS);
        List<BlockPos> tills = vacancies(level, data, city,
                registry.registers(level.dimension()), StructureType.COMMERCIAL);

        for (CitizenEntity citizen : jobless) {
            if (!assign(level, citizen, tills, true) && !assign(level, citizen, desks, false)) {
                // Nothing going. They will be asked again in five seconds; in the meantime they
                // have a perfectly good street to walk down.
                break;
            }
        }
    }

    private static List<BlockPos> vacancies(ServerLevel level, CityData data, City city,
                                            LongArrayList positions, StructureType required) {
        List<BlockPos> open = new ArrayList<>();
        for (long packed : positions) {
            BlockPos pos = BlockPos.of(packed);
            if (!level.isLoaded(pos)) {
                continue;
            }
            Structure structure = data.structureAt(level.dimension(), pos);
            if (structure == null || structure.type() != required
                    || !structure.cityId().equals(city.id())) {
                continue;
            }
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof Workplace place && place.staffed() < place.capacity()) {
                open.add(pos);
            }
        }
        return open;
    }

    /** Give this citizen the nearest of these jobs, if any of them are close enough to walk to. */
    private static boolean assign(ServerLevel level, CitizenEntity citizen,
                                  List<BlockPos> vacancies, boolean nightsPossible) {
        BlockPos best = null;
        double bestDistance = (double) MAX_COMMUTE * MAX_COMMUTE;
        for (BlockPos pos : vacancies) {
            double distance = citizen.blockPosition().distSqr(pos);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = pos;
            }
        }
        if (best == null) {
            return false;
        }
        if (!(level.getBlockEntity(best) instanceof Workplace place)
                || !place.checkIn(citizen.getUUID(), level.getGameTime())) {
            vacancies.remove(best);
            return false;
        }
        citizen.setWorkstation(best);
        citizen.setNightShift(nightsPossible && level.random.nextInt(NIGHT_SHIFT_ODDS) == 0);
        if (place.staffed() >= place.capacity()) {
            vacancies.remove(best);
        }
        return true;
    }

    /** How many citizens of this city are loaded right now, for the city panel to report. */
    public static int countFor(ServerLevel level, UUID cityId) {
        return level.getEntities(ModEntities.CITIZEN.get(),
                citizen -> citizen.isAlive() && cityId.equals(citizen.cityId())).size();
    }
}
