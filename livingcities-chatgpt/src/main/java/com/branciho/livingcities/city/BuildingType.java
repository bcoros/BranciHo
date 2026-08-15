package com.branciho.livingcities.city;

import java.util.Locale;

public enum BuildingType {
    RESIDENTIAL,
    COMMERCIAL,
    OFFICE,
    INDUSTRIAL;

    public static BuildingType parse(String value) {
        return BuildingType.valueOf(value.toUpperCase(Locale.ROOT));
    }
}
