package com.branciho.citiesinlife.entity;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Somebody a car can carry.
 *
 * <p>Exists because the car outgrew its passenger. It was written for citizens and hard-coded to
 * them — it asked a {@code CitizenEntity} for its activity byte and set it back to idle on arrival
 * — which was fine while a citizen was the only person in the world with anywhere to be. Police,
 * firefighters and paramedics now have somewhere to be too, and they are a different class with no
 * activity byte at all.
 *
 * <p>So the car asks for the three things it genuinely needs — which vehicle to turn up in, where
 * to write down that it is aboard, and a nudge when that changes — and stops knowing anything else
 * about who is in it. Everything implementing this is also a {@code PathfinderMob}; the car takes
 * both, which is why the methods here do not repeat what a mob already offers.
 */
public interface Motorist {

    /** The car this person is currently in, or null. */
    @Nullable UUID carId();

    void setCarId(@Nullable UUID carId);

    /**
     * Told when they get in and again when they get out.
     *
     * <p>A citizen uses it to hold its activity at "driving", which is what stops its own goals
     * wandering off mid-journey. A service worker has no equivalent and does nothing with it, and
     * that asymmetry is precisely why this is a hook rather than a field.
     */
    void ridingChanged(boolean aboard);

    /** What this person turns up in. */
    CarEntity.Livery livery();
}
