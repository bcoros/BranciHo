package com.branciho.citiesinlife.blockentity;

import com.branciho.citiesinlife.city.City;
import com.branciho.citiesinlife.city.CityData;
import com.branciho.citiesinlife.entity.CitizenEntity;
import com.branciho.citiesinlife.entity.ServiceEntity;
import com.branciho.citiesinlife.plant.PlantSurvey;
import com.branciho.citiesinlife.registry.ModBlockEntities;
import com.branciho.citiesinlife.registry.ModEntities;
import com.branciho.citiesinlife.service.ServiceLevel;
import com.branciho.citiesinlife.service.ServiceType;
import com.branciho.citiesinlife.structure.Structure;
import com.branciho.citiesinlife.structure.StructureType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * The staffing of one service building.
 *
 * <p>Everything here follows from one decision: service people come out when they are needed and go
 * back in when they are not. A police station with nothing happening has nobody standing outside it,
 * and a hospital with a queue has as many doctors as its setting allows. That is what stops a city
 * with six services from being a city with twenty-four permanent extra entities in it.
 *
 * <p>Soldiers are the exception, and it is not an oversight. They are hired one at a time by the
 * player through the Military Tool and paid for out of the treasury; despawning one because there
 * was no war on would be spending the player's money and then throwing the result away.
 */
public class ServiceSpawnerBlockEntity extends BlockEntity {

    /** How often it looks at the world. Two seconds — a service is allowed a moment to arrive. */
    private static final int INTERVAL_TICKS = 40;

    /** Nobody appears unless somebody could plausibly have watched them walk out of the door. */
    private static final int SPAWN_NEAR_PLAYER = 96;

    private ServiceLevel staffing = ServiceLevel.MEDIUM;

    /** Who this station currently has out. Cleaned of anybody who has died or been discarded. */
    private final List<UUID> onDuty = new ArrayList<>();

    public ServiceSpawnerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SERVICE_SPAWNER.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  ServiceSpawnerBlockEntity spawner) {
        if (level.getGameTime() % INTERVAL_TICKS != 0 || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        spawner.run(serverLevel);
    }

    // ------------------------------------------------------------- the building

    /** The registered building this spawner is standing inside, if any. */
    public @Nullable Structure building() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        return CityData.get(serverLevel.getServer())
                .structureAt(serverLevel.dimension(), worldPosition);
    }

    /** Which service it runs, worked out from what it is standing in. */
    public @Nullable ServiceType service() {
        Structure building = building();
        return building == null ? null : building.type().service();
    }

    public ServiceLevel staffing() {
        return staffing;
    }

    public void cycleLevel() {
        staffing = staffing.next();
        setChanged();
    }

    /** What to say on the action bar when somebody clicks it. */
    public Component report() {
        ServiceType service = service();
        if (service == null) {
            return Component.translatable("hud.citiesinlife.spawner_homeless");
        }
        if (!service.staffed()) {
            return Component.translatable("hud.citiesinlife.spawner_unstaffed", service.displayName());
        }
        return Component.translatable("hud.citiesinlife.spawner_status",
                service.displayName(), staffing.displayName(), onDuty.size());
    }

    // ------------------------------------------------------------------ duty

    private void run(ServerLevel serverLevel) {
        forgetTheDead(serverLevel);

        Structure building = building();
        ServiceType service = building == null ? null : building.type().service();
        City city = building == null
                ? null
                : CityData.get(serverLevel.getServer()).city(building.cityId());

        if (service == null || !service.staffed() || city == null) {
            sendEverybodyHome();
            return;
        }

        if (service == ServiceType.MILITARY) {
            muster(serverLevel, city);
            return;
        }

        int wanted = Math.min(staffing.headcount(), needFor(serverLevel, service, city));
        if (onDuty.size() > wanted) {
            standDown(serverLevel, onDuty.size() - wanted);
        } else if (onDuty.size() < wanted) {
            deploy(serverLevel, city, service, null);
        }
    }

    /**
     * How many people this service could actually use right now.
     *
     * <p>Demand, not establishment. It is deliberately possible for this to be zero for hours at a
     * time — that is what a quiet city looks like.
     */
    private int needFor(ServerLevel serverLevel, ServiceType service, City city) {
        return switch (service) {
            case POLICE -> countCitizens(serverLevel, city, true, false);
            case HOSPITAL -> countCitizens(serverLevel, city, false, true);
            case FIRE -> countTroubledTurbines(serverLevel, city);
            case GARBAGE -> city.refuse() <= city.refuseTolerance()
                    ? 0
                    : 1 + (city.refuse() - city.refuseTolerance()) / Math.max(1, city.refuseTolerance());
            default -> 0;
        };
    }

    private int countCitizens(ServerLevel serverLevel, City city, boolean criminals, boolean hurt) {
        int found = 0;
        for (CitizenEntity citizen : serverLevel.getEntitiesOfClass(CitizenEntity.class,
                new AABB(worldPosition).inflate(64.0D))) {
            if (!citizen.isAlive() || !city.id().equals(citizen.cityId())) {
                continue;
            }
            if (criminals && citizen.criminal()) {
                found++;
            } else if (hurt && citizen.getHealth() < citizen.getMaxHealth()) {
                found++;
            }
        }
        return found;
    }

    private int countTroubledTurbines(ServerLevel serverLevel, City city) {
        int found = 0;
        for (Structure structure : CityData.get(serverLevel.getServer()).structuresOf(city)) {
            if (structure.type() != StructureType.POWER_PLANT
                    || !structure.dimension().equals(serverLevel.dimension())
                    || !serverLevel.isLoaded(structure.min())) {
                continue;
            }
            for (BlockPos turbine : PlantSurvey.of(serverLevel, structure.min(), structure.max()).turbines()) {
                if (serverLevel.getBlockEntity(turbine) instanceof TurbineBlockEntity machine
                        && (machine.burning() || machine.clogged())) {
                    found++;
                }
            }
        }
        return found;
    }

    /**
     * Put a body on the ground for every soldier on the city's books that has not got one.
     *
     * <p>The roll is the truth and this is only its shadow: hiring writes a record, and the next
     * time this runs the record acquires legs. It works the same way after a restart, which is why
     * an army survives one.
     */
    private void muster(ServerLevel serverLevel, City city) {
        List<UUID> present = new ArrayList<>();
        for (UUID id : onDuty) {
            if (serverLevel.getEntity(id) instanceof ServiceEntity soldier && soldier.soldierId() != null) {
                present.add(soldier.soldierId());
            }
        }
        for (City.Soldier record : city.army()) {
            if (present.contains(record.id())) {
                continue;
            }
            if (!deploy(serverLevel, city, ServiceType.MILITARY, record)) {
                return;
            }
        }

        // Anybody whose record has gone was fired while they were standing there.
        for (Iterator<UUID> iterator = onDuty.iterator(); iterator.hasNext(); ) {
            UUID id = iterator.next();
            if (serverLevel.getEntity(id) instanceof ServiceEntity soldier
                    && (soldier.soldierId() == null || city.soldier(soldier.soldierId()) == null)) {
                soldier.discard();
                iterator.remove();
                setChanged();
            }
        }
    }

    private boolean deploy(ServerLevel serverLevel, City city, ServiceType service,
                           @Nullable City.Soldier record) {
        BlockPos spot = doorway(serverLevel);
        if (spot == null) {
            return false;
        }
        if (serverLevel.getNearestPlayer(spot.getX() + 0.5D, spot.getY() + 0.5D, spot.getZ() + 0.5D,
                SPAWN_NEAR_PLAYER, false) == null) {
            return false;
        }

        ServiceEntity worker = ModEntities.SERVICE.get().create(serverLevel);
        if (worker == null) {
            return false;
        }
        worker.moveTo(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D,
                serverLevel.random.nextFloat() * 360.0F, 0.0F);
        worker.setRole(service);
        worker.setCityId(city.id());
        worker.setStation(worldPosition);
        if (record != null) {
            worker.setSoldierId(record.id());
            worker.setTraining(record.training());
            worker.setItemSlot(EquipmentSlot.MAINHAND, weaponOf(record));
            // Soldiers are paid for; letting the game scatter their kit on death would mean a war
            // quietly emptied the player's armoury onto the floor.
            worker.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        }
        worker.finalizeSpawn(serverLevel, serverLevel.getCurrentDifficultyAt(spot),
                MobSpawnType.MOB_SUMMONED, null);
        serverLevel.addFreshEntity(worker);
        onDuty.add(worker.getUUID());
        setChanged();
        return true;
    }

    /** Turn a stored registry id back into something to hold, or nothing if the mod has gone. */
    public static ItemStack weaponOf(City.Soldier record) {
        if (record.weapon().isEmpty()) {
            return ItemStack.EMPTY;
        }
        ResourceLocation id = ResourceLocation.tryParse(record.weapon());
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            return ItemStack.EMPTY;
        }
        Item item = BuiltInRegistries.ITEM.get(id);
        return new ItemStack(item);
    }

    private void standDown(ServerLevel serverLevel, int howMany) {
        for (Iterator<UUID> iterator = onDuty.iterator(); iterator.hasNext() && howMany > 0; ) {
            UUID id = iterator.next();
            if (serverLevel.getEntity(id) instanceof ServiceEntity worker) {
                // Only somebody who has run out of things to do goes in. Sending the officer who is
                // mid-arrest home because the count came down would be worse than having one too
                // many on the street.
                if (!worker.overstayed()) {
                    continue;
                }
                worker.discard();
            }
            iterator.remove();
            howMany--;
            setChanged();
        }
    }

    /** Everybody in, permanently — the station is gone or is no longer a station. */
    public void sendEverybodyHome() {
        if (onDuty.isEmpty() || !(level instanceof ServerLevel serverLevel)) {
            onDuty.clear();
            return;
        }
        for (UUID id : onDuty) {
            if (serverLevel.getEntity(id) instanceof ServiceEntity worker) {
                worker.discard();
            }
        }
        onDuty.clear();
        setChanged();
    }

    private void forgetTheDead(ServerLevel serverLevel) {
        boolean changed = onDuty.removeIf(id -> !(serverLevel.getEntity(id) instanceof ServiceEntity worker)
                || !worker.isAlive());
        if (changed) {
            setChanged();
        }
    }

    /** Somewhere beside the spawner with room to stand up in. */
    private @Nullable BlockPos doorway(ServerLevel serverLevel) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos candidate = worldPosition.relative(direction);
            if (roomToStand(serverLevel, candidate)) {
                return candidate;
            }
            if (roomToStand(serverLevel, candidate.above())) {
                return candidate.above();
            }
        }
        return roomToStand(serverLevel, worldPosition.above()) ? worldPosition.above() : null;
    }

    private static boolean roomToStand(ServerLevel serverLevel, BlockPos pos) {
        return serverLevel.isLoaded(pos)
                && serverLevel.getBlockState(pos).getCollisionShape(serverLevel, pos).isEmpty()
                && serverLevel.getBlockState(pos.above()).getCollisionShape(serverLevel, pos.above()).isEmpty()
                && !serverLevel.getBlockState(pos.below()).getCollisionShape(serverLevel, pos.below()).isEmpty();
    }

    // ------------------------------------------------------------ persistence

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        staffing = ServiceLevel.byId(tag.getString("level"), ServiceLevel.MEDIUM);
        onDuty.clear();
        ListTag list = tag.getList("onDuty", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            onDuty.add(list.getCompound(i).getUUID("id"));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("level", staffing.id());
        ListTag list = new ListTag();
        for (UUID id : onDuty) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", id);
            list.add(entry);
        }
        tag.put("onDuty", list);
    }
}
