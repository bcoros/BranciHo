package com.branciho.citiesinlife.blockentity;

import com.branciho.citiesinlife.city.City;
import com.branciho.citiesinlife.city.CityData;
import com.branciho.citiesinlife.city.CityFlag;
import com.branciho.citiesinlife.city.Diplomacy;
import com.branciho.citiesinlife.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The flag on the pole: a copy of some city's design, and which city's it is.
 *
 * <p>A copy rather than a lookup, because the renderer runs on the client where no city exists.
 * The copy is refreshed on the server every few seconds against the city it names, so redesigning
 * a flag repaints every pole flying it rather than leaving the old one up until somebody breaks and
 * replaces it.
 *
 * <p>Whose flag it flies is decided once, when it is placed: whichever city owns the ground it
 * stands on, or the placer's own if the ground is nobody's. Planting your flag on somebody else's
 * land therefore flies <em>their</em> colours, which is the honest answer and a good deal funnier
 * than refusing.
 */
public class CityFlagBlockEntity extends BlockEntity {

    /** Ten seconds. A flag is redesigned about once a save; polling it harder would be silly. */
    private static final int REFRESH_TICKS = 200;

    private byte[] flag = CityFlag.blank();
    private String cityName = "";
    private java.util.UUID cityId;

    public CityFlagBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CITY_FLAG.get(), pos, state);
    }

    public byte[] flag() {
        return flag;
    }

    public String cityName() {
        return cityName;
    }

    /** Bind this pole to whichever city has the best claim to the ground under it. */
    public void bind(ServerLevel level, java.util.UUID placer) {
        City owner = Diplomacy.owner(level.getServer(), level.dimension(), worldPosition);
        if (owner == null) {
            owner = CityData.get(level.getServer()).cityOf(placer, level.dimension());
        }
        if (owner != null) {
            cityId = owner.id();
        }
        pull(level);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  CityFlagBlockEntity flag) {
        if (level instanceof ServerLevel serverLevel && level.getGameTime() % REFRESH_TICKS == 0) {
            flag.pull(serverLevel);
        }
    }

    /** Take a fresh copy of the city's design, and tell the clients if it has changed. */
    private void pull(ServerLevel level) {
        if (cityId == null) {
            return;
        }
        City city = CityData.get(level.getServer()).city(cityId);
        if (city == null) {
            return;
        }
        byte[] latest = CityFlag.sanitise(city.flag());
        if (java.util.Arrays.equals(latest, flag) && city.name().equals(cityName)) {
            return;
        }
        flag = latest;
        cityName = city.name();
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    /**
     * Big enough to hold the pole and the cloth.
     *
     * <p>Both of them reach outside the block: the pole is two blocks tall and the cloth is a block
     * and a half of it hanging off one side. Left at the default one-block box, the whole thing
     * vanishes the moment the block itself leaves the screen - which for something you are meant to
     * see from across the map is exactly backwards.
     */
    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        return new net.minecraft.world.phys.AABB(worldPosition).inflate(3.0D);
    }

    // --------------------------------------------------------------- syncing

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putByteArray("flag", flag);
        tag.putString("cityName", cityName);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // ------------------------------------------------------------ persistence

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        flag = CityFlag.sanitise(tag.getByteArray("flag"));
        cityName = tag.getString("cityName");
        cityId = tag.hasUUID("city") ? tag.getUUID("city") : null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putByteArray("flag", flag);
        tag.putString("cityName", cityName);
        if (cityId != null) {
            tag.putUUID("city", cityId);
        }
    }
}
