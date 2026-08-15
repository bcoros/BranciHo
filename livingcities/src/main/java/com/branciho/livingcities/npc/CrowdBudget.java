package com.branciho.livingcities.npc;

import com.branciho.livingcities.building.Building;
import com.branciho.livingcities.building.Floor;
import com.branciho.livingcities.building.ZoneUse;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * How many citizens one player should be able to see, and the working shown.
 *
 * <p>Pure arithmetic: no world, no entities, no config lookups. Everything it needs is passed in, so
 * the whole crowd model can be exercised in a test with three integers and no server.
 *
 * <h2>The formula</h2>
 *
 * <pre>{@code
 *   draw(building) = residents  x RESIDENT_STREET_SHARE
 *                  + workers    x WORKER_STREET_SHARE
 *                  + visitorCells / 100 x VISITORS_PER_HUNDRED_CELLS
 *
 *   demand  = SUM draw(nearby buildings) + cityPopulation x CITY_AMBIENT_SHARE
 *   crowd   = CROWD_SCALE x demand ^ CROWD_COMPRESSION
 *   target  = clamp(round(crowd x activityDensity x npcDensityMultiplier), 0, perViewerCap)
 * }</pre>
 *
 * <h3>Why that shape</h3>
 *
 * <p><b>Demand is per building, not per city.</b> The three terms are the three reasons somebody is
 * on a given street: they live there, they work there, or they came to visit something. Reading them
 * off the buildings actually near the player is what makes a downtown block busier than a cul-de-sac
 * in the same city - a city-wide average could not tell the two apart.
 *
 * <p><b>The zone mix enters through the weights.</b> A worker is worth more street presence than a
 * resident because workers arrive, leave and step out for lunch on a schedule, while most residents
 * are indoors. Visitor-drawing floors - shops, entertainment, transport, parks, civic buildings -
 * contribute by floor area rather than by occupancy, because their crowd is people who do not live or
 * work there and so appear in neither figure. A warehouse district and a high street with identical
 * floor area therefore look completely different.
 *
 * <p><b>The city's population is a small ambient term.</b> Through-traffic that belongs to no
 * particular nearby building. Deliberately tiny per head: it is what makes the same high street feel
 * busier in a metropolis than in a village, without letting a big population drown out local detail.
 *
 * <p><b>The compression exponent is the important constant.</b> Demand grows without limit as a city
 * densifies, but the visible crowd must not, or every downtown saturates the entity cap and every
 * suburb rounds to zero - and then nowhere looks different from anywhere else. Raising demand to
 * {@value #CROWD_COMPRESSION} means a tenfold increase in local activity shows up as roughly a
 * fivefold increase in bodies: still obviously busier, still leaving headroom above it. It is also
 * what keeps the numbers gentle at the bottom, where a single small house should produce an empty
 * street rather than a rounding artefact.
 *
 * <p><b>Time of day and config multiply at the end</b>, so they scale the whole result uniformly and
 * a server owner turning {@code npcDensityMultiplier} down thins every street by the same proportion
 * instead of flattening the differences between them.
 *
 * @param target      citizens this viewer should see, already clamped to {@code perViewerCap}
 * @param demand      the uncompressed demand figure, retained for debugging and future overlays
 * @param localDraw   the part of {@code demand} attributable to nearby buildings
 * @param cityAmbient the part of {@code demand} attributable to city-wide through-traffic
 * @param activity    the day/night curve this target was evaluated against
 */
public record CrowdBudget(int target,
                          double demand,
                          double localDraw,
                          double cityAmbient,
                          CitizenActivity activity) {

    /**
     * Fraction of a building's residents plausibly outdoors near it at the busiest hour. Low, because
     * the overwhelming majority of a population is indoors, asleep or elsewhere at any given moment.
     */
    private static final double RESIDENT_STREET_SHARE = 0.020D;

    /** Same idea for workers, higher because a workplace generates arrivals, departures and errands. */
    private static final double WORKER_STREET_SHARE = 0.035D;

    /**
     * Street presence per 100 usable floor blocks of visitor-drawing frontage. Counted by area rather
     * than occupancy because these are customers, not staff, and so appear in no capacity figure.
     */
    private static final double VISITORS_PER_HUNDRED_CELLS = 1.2D;

    /** Through-traffic per head of city population. Tiny on purpose; see the class docs. */
    private static final double CITY_AMBIENT_SHARE = 0.0015D;

    /** Overall gain on the compressed crowd. Left at 1.0 so the exponent is the only tuning knob. */
    private static final double CROWD_SCALE = 1.0D;

    /** Sub-linear exponent that keeps dense cities from instantly saturating the entity cap. */
    private static final double CROWD_COMPRESSION = 0.72D;

    /**
     * Floors whose crowd is visitors rather than residents or staff.
     *
     * <p>Derived from the floor's assigned {@link ZoneUse}, so this follows whatever the player zoned
     * rather than any guess about what the build looks like.
     */
    private static final Set<ZoneUse> VISITOR_DRAWING = EnumSet.of(
            ZoneUse.COMMERCIAL,
            ZoneUse.ENTERTAINMENT,
            ZoneUse.TRANSPORT,
            ZoneUse.PUBLIC_SERVICE,
            ZoneUse.GOVERNMENT,
            ZoneUse.PARK);

    /**
     * Evaluate the budget for one viewer.
     *
     * @param cityPopulation    population of the city the viewer is standing in, or 0 if none
     * @param nearby            registered buildings close enough to contribute; may be empty
     * @param activity          the day/night curve at the viewer's time of day
     * @param densityMultiplier the server's {@code npcDensityMultiplier}
     * @param perViewerCap      hard ceiling for this viewer, derived from the global NPC cap
     */
    public static CrowdBudget evaluate(int cityPopulation,
                                       List<Building> nearby,
                                       CitizenActivity activity,
                                       double densityMultiplier,
                                       int perViewerCap) {
        double localDraw = 0.0D;
        for (int i = 0; i < nearby.size(); i++) {
            localDraw += streetDraw(nearby.get(i));
        }

        final double cityAmbient = Math.max(0, cityPopulation) * CITY_AMBIENT_SHARE;
        final double demand = localDraw + cityAmbient;
        if (demand <= 0.0D || perViewerCap <= 0) {
            return new CrowdBudget(0, demand, localDraw, cityAmbient, activity);
        }

        final double crowd = CROWD_SCALE * Math.pow(demand, CROWD_COMPRESSION);
        final double scaled = crowd * activity.densityMultiplier() * Math.max(0.0D, densityMultiplier);
        final int target = Math.clamp(Math.round(scaled), 0, perViewerCap);
        return new CrowdBudget(target, demand, localDraw, cityAmbient, activity);
    }

    /**
     * How much street presence one building generates at peak.
     *
     * <p>Exposed because the spawn director weights its choice of anchor building by exactly this
     * number: the building responsible for most of the crowd should receive most of it. Deriving both
     * from one function is what stops the count and the placement from disagreeing.
     */
    public static double streetDraw(Building building) {
        int visitorCells = 0;
        final List<Floor> floors = building.floors();
        for (int i = 0; i < floors.size(); i++) {
            final Floor floor = floors.get(i);
            if (VISITOR_DRAWING.contains(floor.use())) {
                visitorCells += floor.usableCells();
            }
        }
        return building.residents() * RESIDENT_STREET_SHARE
                + building.workers() * WORKER_STREET_SHARE
                + visitorCells / 100.0D * VISITORS_PER_HUNDRED_CELLS;
    }
}
