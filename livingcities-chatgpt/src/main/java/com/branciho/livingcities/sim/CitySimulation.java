package com.branciho.livingcities.sim;

import com.branciho.livingcities.LivingCities;
import com.branciho.livingcities.city.Building;
import com.branciho.livingcities.city.City;
import com.branciho.livingcities.city.CityData;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.List;

@EventBusSubscriber(modid = LivingCities.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class CitySimulation {
    private CitySimulation() {}

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        long gameTime = server.overworld().getGameTime();
        if (gameTime % 100 != 0) return;

        CityData data = CityData.get(server);
        boolean changed = false;
        for (City city : data.cities()) {
            List<Building> buildings = data.buildingsOf(city.id());
            int housing = buildings.stream().mapToInt(Building::housing).sum();
            int jobs = buildings.stream().mapToInt(Building::jobs).sum();
            int target = Math.min(housing, Math.max(25, jobs * 2));
            int population = city.population();
            if (target > population) {
                city.setPopulation(population + Math.max(1, Math.min(target - population, Math.max(1, population / 50))));
                changed = true;
            } else if (target < population) {
                city.setPopulation(population - Math.max(1, Math.min(population - target, Math.max(1, population / 80))));
                changed = true;
            }
            double tax = city.population() * 0.04;
            if (tax > 0) {
                city.addMoney(tax);
                changed = true;
            }
        }
        if (changed) data.setDirty();
    }
}
