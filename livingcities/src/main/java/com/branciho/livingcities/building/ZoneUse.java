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
    UNUSED("unused", false, 0, TaxCategory.NONE),

    RESIDENTIAL("residential", true, 0, TaxCategory.RESIDENTIAL),
    COMMERCIAL("commercial", false, 28, TaxCategory.COMMERCIAL),
    OFFICE("office", false, 14, TaxCategory.OFFICE),
    INDUSTRIAL("industrial", false, 18, TaxCategory.INDUSTRIAL),

    WAREHOUSE("warehouse", false, 110, TaxCategory.INDUSTRIAL),
    GOVERNMENT("government", false, 16, TaxCategory.NONE),
    PUBLIC_SERVICE("public_service", false, 20, TaxCategory.NONE),
    ENTERTAINMENT("entertainment", false, 30, TaxCategory.COMMERCIAL),
    TRANSPORT("transport", false, 45, TaxCategory.NONE),
    UTILITY("utility", false, 60, TaxCategory.NONE),

    /** Parks earn their keep through happiness and land value, not payroll. */
    PARK("park", false, 0, TaxCategory.NONE),

    /** Anything the player wants to register but that the simulation should not score. */
    SPECIAL("special", false, 0, TaxCategory.NONE);

    private final String id;
    private final boolean housing;
    private final int cellsPerJob;
    private final TaxCategory taxCategory;

    /**
     * @param cellsPerJob usable floor blocks needed per job, or 0 for a use that employs nobody.
     *                    Expressed as area-per-job rather than jobs-per-area because that is how the
     *                    number is actually reasoned about ("a desk plus circulation is about 14 blocks")
     *                    and it keeps the constants readable integers.
     */
    ZoneUse(String id, boolean housing, int cellsPerJob, TaxCategory taxCategory) {
        this.id = id;
        this.housing = housing;
        this.cellsPerJob = cellsPerJob;
        this.taxCategory = taxCategory;
    }

    public int cellsPerJob() {
        return cellsPerJob;
    }

    public String id() {
        return id;
    }

    /** Jobs per 100 usable floor blocks. Derived from {@link #cellsPerJob()}. */
    public double jobsPerHundred() {
        return cellsPerJob <= 0 ? 0.0D : 100.0D / cellsPerJob;
    }

    public TaxCategory taxCategory() {
        return taxCategory;
    }

    public boolean providesHousing() {
        return housing;
    }

    public boolean providesJobs() {
        return cellsPerJob > 0;
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
