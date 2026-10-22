package com.branciho.citiesinlife.city;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Anything that belongs to a city and is therefore protected by its borders.
 *
 * <p>Citizens and service workers both. The protection rules used to name the citizen class
 * directly, which meant every police officer, doctor and soldier the mod added arrived completely
 * unprotected — killable by anybody, from anywhere, regardless of whose city they worked for. One
 * interface is what stops the next kind of person having the same problem.
 */
public interface CityMember {

    /** The city that employs this one, or null if it has been orphaned. */
    @Nullable UUID cityId();
}
