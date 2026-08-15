package com.branciho.livingcities.city;

import net.minecraft.nbt.CompoundTag;

/**
 * The aggregate simulation state of a city.
 *
 * <p>This is the "virtual population" layer. A city of 50,000 people is these numbers, not 50,000
 * entities. Physical NPCs are a separate, purely cosmetic layer that reads from here.
 *
 * <p>Happiness is stored in permille (0-1000) rather than a float so that saved data and network
 * packets stay exact and comparable.
 */
public final class CityStats {

    private int population;
    private int housingCapacity;
    private int jobs;
    private int employed;
    private int happinessPermille = 700;

    /** 0-1000: demand-weighted share of the city's electrical demand its grids are meeting. */
    private int powerSatisfactionPermille = 1000;

    private long dailyIncomeCents;
    private long dailyExpenseCents;

    /** Fractional population carried between simulation steps so slow growth is not rounded away. */
    private double populationRemainder;

    public int population() {
        return population;
    }

    public void setPopulation(int population) {
        this.population = Math.max(0, population);
    }

    public double populationRemainder() {
        return populationRemainder;
    }

    public void setPopulationRemainder(double remainder) {
        this.populationRemainder = remainder;
    }

    public int housingCapacity() {
        return housingCapacity;
    }

    public void setHousingCapacity(int housingCapacity) {
        this.housingCapacity = Math.max(0, housingCapacity);
    }

    public int jobs() {
        return jobs;
    }

    public void setJobs(int jobs) {
        this.jobs = Math.max(0, jobs);
    }

    public int employed() {
        return employed;
    }

    public void setEmployed(int employed) {
        this.employed = Math.max(0, employed);
    }

    public int happinessPermille() {
        return happinessPermille;
    }

    public void setHappinessPermille(int happinessPermille) {
        this.happinessPermille = Math.clamp(happinessPermille, 0, 1000);
    }

    public int powerSatisfactionPermille() {
        return powerSatisfactionPermille;
    }

    public void setPowerSatisfactionPermille(int powerSatisfactionPermille) {
        this.powerSatisfactionPermille = Math.clamp(powerSatisfactionPermille, 0, 1000);
    }

    public long dailyIncomeCents() {
        return dailyIncomeCents;
    }

    public void setDailyIncomeCents(long dailyIncomeCents) {
        this.dailyIncomeCents = dailyIncomeCents;
    }

    public long dailyExpenseCents() {
        return dailyExpenseCents;
    }

    public void setDailyExpenseCents(long dailyExpenseCents) {
        this.dailyExpenseCents = dailyExpenseCents;
    }

    /** Working-age residents, i.e. the pool that can fill jobs. */
    public int workforce() {
        return (int) (population * WORKFORCE_PARTICIPATION);
    }

    public static final double WORKFORCE_PARTICIPATION = 0.61D;

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Population", population);
        tag.putInt("HousingCapacity", housingCapacity);
        tag.putInt("Jobs", jobs);
        tag.putInt("Employed", employed);
        tag.putInt("Happiness", happinessPermille);
        tag.putInt("PowerSatisfaction", powerSatisfactionPermille);
        tag.putLong("DailyIncome", dailyIncomeCents);
        tag.putLong("DailyExpense", dailyExpenseCents);
        tag.putDouble("PopulationRemainder", populationRemainder);
        return tag;
    }

    public void load(CompoundTag tag) {
        this.population = tag.getInt("Population");
        this.housingCapacity = tag.getInt("HousingCapacity");
        this.jobs = tag.getInt("Jobs");
        this.employed = tag.getInt("Employed");
        this.happinessPermille = tag.contains("Happiness") ? tag.getInt("Happiness") : 700;
        this.powerSatisfactionPermille = tag.contains("PowerSatisfaction") ? tag.getInt("PowerSatisfaction") : 1000;
        this.dailyIncomeCents = tag.getLong("DailyIncome");
        this.dailyExpenseCents = tag.getLong("DailyExpense");
        this.populationRemainder = tag.getDouble("PopulationRemainder");
    }
}
