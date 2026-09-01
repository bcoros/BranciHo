package com.branciho.citiesinlife.structure;

import com.branciho.citiesinlife.service.ServiceType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.Nullable;

/**
 * What a selected structure has been declared to be.
 *
 * <p>The mod ships no buildings. The player builds whatever they like out of whatever blocks they
 * like, draws a box around it, and picks one of these. Everything the simulation knows about a
 * building is derived from the blocks inside that box plus this choice.
 *
 * <p>Capacity is expressed as <em>cells per unit</em> rather than units per cell so the numbers read
 * as floor space: an office worker gets 14 floor cells, a factory worker 18, a shop worker 28
 * because shops are mostly aisle. Housing goes the other way — residents per cell — because a home
 * is the one thing here that should reward every single block of floor you add to it.
 */
public enum StructureType implements StringRepresentable {

    /**
     * The seat of the city. Selecting one founds a city; there is exactly one per city.
     *
     * <p>Deliberately not a block you place. The player builds a city hall that looks like a city
     * hall and then declares it to be one — the same rule as every other structure here.
     */
    CITY_CORE("city_core", 0xFFD86A, 0),

    RESIDENTIAL("residential", 0x66E576, 0),

    COMMERCIAL("commercial", 0x59A6FF, 6),

    /** Offices. Denser than shops because a desk needs less room than a shop floor. */
    BUSINESS("business", 0x4DD9E6, 3),

    FACTORY("factory", 0xFFD859, 4),

    /** Where the police come from. Sparse floors: a station is mostly cells and corridor. */
    POLICE_STATION("police_station", 0x3A6BE0, 14, ServiceType.POLICE),

    /** The fire station. Half of it is the engine bay, so it employs fewer people than it looks. */
    FIRE_STATION("fire_station", 0xE04A3A, 16, ServiceType.FIRE),

    /** A hospital. The densest of the services, because a ward is all staff. */
    HOSPITAL("hospital", 0xF2F4F7, 9, ServiceType.HOSPITAL),

    /**
     * A park.
     *
     * <p>The only registered thing in the mod that is deliberately outdoors, which is why it is not
     * measured by floors — there are none. What matters about a park is how much ground it covers,
     * and the box the player drew already says that.
     */
    PARK("park", 0x3FBF5F, 0, ServiceType.PARK, false),

    /** The depot the bin lorries go out from. */
    GARBAGE_DEPOT("garbage_depot", 0x8A7A5C, 18, ServiceType.GARBAGE),

    /**
     * The barracks.
     *
     * <p>Employs nobody, on purpose. Soldiers are hired through the Military Tool and paid out of
     * the treasury; counting them again as jobs would have the city's own economy pay for its army
     * twice over. Not measured for the same reason a power plant is not: it is a marker saying "the
     * army lives here", and its floor space means nothing.
     */
    MILITARY_BASE("military_base", 0x5A6B3A, 0, ServiceType.MILITARY, false),

    /**
     * A building the mod should look inside for power machinery.
     *
     * <p>It houses nobody and employs nobody. Its whole job is to tell the boiler where to look:
     * without it the boiler could only find a turbine sitting in the exact column above itself, which
     * is a rule that is easy to state and miserable to build around. Drawing a box round the whole
     * plant says "the turbine and the chimney are somewhere in here", and that is enough.
     *
     * <p>Solar panels need no equivalent because a panel is one block that is either wired up or not.
     * Windmills and reactors will be buildings, so they will use this too.
     */
    POWER_PLANT("power_plant", 0xE0662F, 0, null, false),

    /**
     * A nuclear power plant, and the most demanding thing the mod asks anyone to build.
     *
     * <p>Its own type rather than another kind of POWER_PLANT, because the two answer completely
     * different questions when you look inside them. A coal plant asks "which turbine is mine and
     * is there a chimney". A reactor asks whether ten hand-placed columns are all exactly the same
     * height, all flooded, all capped, plumbed into a closed loop with a pressurised pipe in the
     * right place — and refuses, by name, when any one of those is wrong.
     *
     * <p>Added at the END of the enum on purpose. Nothing is persisted by ordinal, but the planner's
     * scroll order and the war wand's cycle both come from ordinal position, so inserting anywhere
     * else would silently reshuffle a list the player has already learned.
     */
    NUCLEAR_PLANT("nuclear_plant", 0x7ED63E, 0, null, false),

    /**
     * A missile silo: a box with rockets standing in it.
     *
     * <p>Not measured, for the same reason a power plant is not — what matters is not how much
     * room is inside but what is standing in it. The box is what turns a rocket somebody placed
     * into a rocket somebody can fire: a missile outside a registered silo is scenery.
     *
     * <p>Everything else in the box is optional and additive. Sealing blocks become a roof that
     * opens; an alarm gives you the light going red while the doors travel; a siren watches for
     * what is coming the other way. A silo with none of them is a rocket on open ground, which
     * works perfectly and can be seen from the air by anybody who wants to know where to aim.
     *
     * <p>Added at the END, like the reactor before it, because the planner's scroll order and the
     * war wand's cycle both come from ordinal position and inserting anywhere else would silently
     * reshuffle a list the player has already learned.
     */
    MISSILE_SILO("missile_silo", 0xB0552F, 0, null, false);

    /**
     * Virtual residents one cell of floor houses.
     *
     * <p>Housing used to be quantised into whole dwellings — sixteen cells bought one dwelling and
     * a dwelling was ten people — which meant a house could only ever hold 0, 10, 20, 30 people and
     * nothing in between. Two builds that felt completely different to put up read the same, and a
     * modest one landed on nought because it was one cell short of the first step. Floor space you
     * added and got nothing for is the worst possible answer from a measuring tool: it reads as the
     * mod ignoring what you built.
     *
     * <p>So there is no step any more. Every cell counts, and the rate is roughly double what the
     * old one worked out at, because the old figure was quietly stingy — the quantiser threw away
     * up to fifteen cells of every building, and a large house came out smaller than it looked.
     *
     * <p>These are <em>virtual</em> citizens — a number, not entities. Physical NPCs are a small
     * visible sample of this figure, never one entity per person.
     */
    public static final double RESIDENTS_PER_CELL = 1.25D;

    /** Below this, a floor is a cupboard rather than a workplace. */
    public static final int MIN_USABLE_CELLS = 9;

    /**
     * Below this, a house is a shed.
     *
     * <p>Lower than {@link #MIN_USABLE_CELLS} on purpose. Somewhere to sleep is a smaller ask than
     * somewhere to work, and a hut that houses two people is a much better answer than a hut that
     * houses nobody. Kept separate rather than lowering the shared figure, because that one is also
     * what decides when a bombed building stops counting as a building.
     */
    public static final int MIN_HOUSING_CELLS = 4;

    private final String id;
    private final int colour;
    private final int cellsPerJob;

    /** The service this building runs, or null if it is an ordinary part of the city. */
    private final @Nullable ServiceType service;

    /** Whether looking inside this building tells us anything about how many people it holds. */
    private final boolean measured;

    StructureType(String id, int colour, int cellsPerJob) {
        this(id, colour, cellsPerJob, null, true);
    }

    StructureType(String id, int colour, int cellsPerJob, ServiceType service) {
        this(id, colour, cellsPerJob, service, true);
    }

    StructureType(String id, int colour, int cellsPerJob, @Nullable ServiceType service,
                  boolean measured) {
        this.id = id;
        this.colour = colour;
        this.cellsPerJob = cellsPerJob;
        this.service = service;
        this.measured = measured;
    }

    public String id() {
        return id;
    }

    /** Outline and panel colour, so a glance at a district tells you its make-up. */
    public int colour() {
        return colour;
    }

    public boolean housesPeople() {
        return this == RESIDENTIAL;
    }

    /**
     * Whether this is a generating station of some kind.
     *
     * <p>Exists so the half-dozen places that used to test {@code == POWER_PLANT} ask one question
     * instead of each growing their own {@code ||}. Every one of those sites was a silent failure
     * waiting to happen: a reactor that could not be built on unclaimed ground, a chimney smoking
     * off a furnace inside a reactor hall, a fire brigade that would not attend.
     */
    public boolean isPlant() {
        return this == POWER_PLANT || this == NUCLEAR_PLANT;
    }

    public boolean employsPeople() {
        return cellsPerJob > 0;
    }

    /**
     * Whether measuring the inside of this building tells us anything.
     *
     * <p>A power plant is a marker rather than a capacity, so reporting "no usable floors found" for
     * a boiler house would be advice about a problem it does not have. A park and a military base
     * are markers in the same way.
     */
    public boolean measured() {
        return measured;
    }

    /** Which service a Service NPC Spawner standing inside this building would run. */
    public @Nullable ServiceType service() {
        return service;
    }

    public Component displayName() {
        return Component.translatable("structure.citiesinlife." + id);
    }

    /**
     * How many residents this many usable floor cells houses.
     *
     * <p>Straight multiplication, rounded. Every cell you add is worth somebody, which is the only
     * behaviour that makes extending a house feel like extending a house.
     */
    public int residentsFor(int usableCells) {
        if (!housesPeople() || usableCells < MIN_HOUSING_CELLS) {
            return 0;
        }
        return Math.max(1, (int) Math.round(usableCells * RESIDENTS_PER_CELL));
    }

    public int jobsFor(int usableCells) {
        if (!employsPeople() || usableCells < MIN_USABLE_CELLS) {
            return 0;
        }
        return usableCells / cellsPerJob;
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    public static StructureType byId(String id, StructureType fallback) {
        for (StructureType type : values()) {
            if (type.id.equals(id)) {
                return type;
            }
        }
        return fallback;
    }

    /** The order the planner panel lists them in, and the order the scroll wheel cycles. */
    public static final StructureType[] SELECTABLE = values();

    public StructureType next(int direction) {
        int index = Math.floorMod(ordinal() + direction, SELECTABLE.length);
        return SELECTABLE[index];
    }
}
