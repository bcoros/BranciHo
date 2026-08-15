package com.branciho.livingcities.building;

/**
 * What a floor is used for.
 *
 * <p>Assigned per floor rather than per building, which is what makes mixed use work: floor 1 can be
 * commercial, floors 2-6 office and 7-25 residential, all inside one physical structure the player built.
 *
 * <p>Densities here are baseline defaults per 100 usable floor blocks. The four headline uses are
 * overridable in the server config; the rest are fixed for now. When this needs to become fully
 * data-driven, this enum becomes the built-in defaults for a datapack registry and the rest of the
 * code keeps working, because everything downstream reads these accessors rather than switching on
 * the constant.
 */
public enum ZoneUse {

    /** Scanned but not yet assigned. Contributes nothing. */
    UNUSED("unused", 0.0D, 0.0D, TaxCategory.NONE),

    RESIDENTIAL("residential", 4.0D, 0.0D, TaxCategory.RESIDENTIAL),
    COMMERCIAL("commercial", 0.0D, 3.0D, TaxCategory.COMMERCIAL),
    OFFICE("office", 0.0D, 5.0D, TaxCategory.OFFICE),
    INDUSTRIAL("industrial", 0.0D, 1.5D, TaxCategory.INDUSTRIAL),

    WAREHOUSE("warehouse", 0.0D, 0.5D, TaxCategory.INDUSTRIAL),
    GOVERNMENT("government", 0.0D, 3.0D, TaxCategory.NONE),
    PUBLIC_SERVICE("public_service", 0.0D, 3.0D, TaxCategory.NONE),
    ENTERTAINMENT("entertainment", 0.0D, 2.5D, TaxCategory.COMMERCIAL),
    PARK("park", 0.0D, 0.2D, TaxCategory.NONE),
    TRANSPORT("transport", 0.0D, 1.0D, TaxCategory.NONE),
    UTILITY("utility", 0.0D, 0.8D, TaxCategory.NONE),

    /** Anything the player wants to register but that the simulation should not score. */
    SPECIAL("special", 0.0D, 0.0D, TaxCategory.NONE);

    private final String id;
    private final double residentsPerHundred;
    private final double jobsPerHundred;
    private final TaxCategory taxCategory;

    ZoneUse(String id, double residentsPerHundred, double jobsPerHundred, TaxCategory taxCategory) {
        this.id = id;
        this.residentsPerHundred = residentsPerHundred;
        this.jobsPerHundred = jobsPerHundred;
        this.taxCategory = taxCategory;
    }

    public String id() {
        return id;
    }

    public double residentsPerHundred() {
        return residentsPerHundred;
    }

    public double jobsPerHundred() {
        return jobsPerHundred;
    }

    public TaxCategory taxCategory() {
        return taxCategory;
    }

    public boolean providesHousing() {
        return residentsPerHundred > 0.0D;
    }

    public boolean providesJobs() {
        return jobsPerHundred > 0.0D;
    }

    public static ZoneUse byId(String id, ZoneUse fallback) {
        for (ZoneUse use : values()) {
            if (use.id.equalsIgnoreCase(id) || use.name().equalsIgnoreCase(id)) {
                return use;
            }
        }
        return fallback;
    }

    /** Which tax rate applies to the activity on a floor. */
    public enum TaxCategory {
        NONE,
        RESIDENTIAL,
        COMMERCIAL,
        OFFICE,
        INDUSTRIAL
    }
}
