package com.branciho.livingcities.blockentity;

import com.branciho.livingcities.city.City;
import com.branciho.livingcities.city.CityRegistry;
import com.branciho.livingcities.net.LivingCitiesNetwork;
import com.branciho.livingcities.net.ServerPayloadHandler;
import com.branciho.livingcities.net.payload.OpenCityScreenPayload;
import com.branciho.livingcities.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Anchors a city to the player's own city hall build.
 *
 * <p>Holds only a reference to the city; the city itself lives in the server-global registry. That
 * split matters: a player who breaks, moves or duplicates this block cannot fork or fabricate a city,
 * because the block is a pointer rather than the record.
 */
public class CityHallCoreBlockEntity extends BlockEntity {

    private @Nullable UUID cityId;

    public CityHallCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CITY_HALL_CORE.get(), pos, state);
    }

    public @Nullable UUID cityId() {
        return cityId;
    }

    public void setCityId(@Nullable UUID cityId) {
        this.cityId = cityId;
        setChanged();
    }

    /** Server-side reaction to a player right-clicking the core. */
    public void onInteract(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        MinecraftServer server = serverPlayer.getServer();
        if (server == null) {
            return;
        }

        CityRegistry registry = CityRegistry.get(server);
        City city = cityId == null ? null : registry.byId(cityId);

        if (city == null) {
            // Stale pointer (city disbanded, or a core that was never bound) - offer to found one.
            if (cityId != null) {
                setCityId(null);
            }
            LivingCitiesNetwork.sendTo(serverPlayer,
                    new OpenCityScreenPayload(OpenCityScreenPayload.Screen.CREATE_CITY, worldPosition));
            return;
        }

        LivingCitiesNetwork.sendTo(serverPlayer,
                new OpenCityScreenPayload(OpenCityScreenPayload.Screen.MANAGEMENT, worldPosition));
        ServerPayloadHandler.sendSummary(serverPlayer, city);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.cityId = tag.hasUUID("CityId") ? tag.getUUID("CityId") : null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (cityId != null) {
            tag.putUUID("CityId", cityId);
        }
    }
}
