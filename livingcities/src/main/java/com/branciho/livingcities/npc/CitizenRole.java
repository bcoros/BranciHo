package com.branciho.livingcities.npc;

import com.branciho.livingcities.building.ZoneUse;

/**
 * What a physically spawned citizen is notionally doing right now.
 *
 * <p>In v0.1 the role only biases <em>where</em> a citizen is placed - a shopper is more likely to
 * appear outside a shop than outside a warehouse - because {@code CitizenEntity} has no behaviour to
 * drive yet beyond a stroll goal. The reason the enum carries origin/destination hints and a pace
 * multiplier anyway is that those are exactly the three things a router needs, and putting them here
 * now means the routing work is "read these fields" rather than "invent a role model".
 *
 * <p>The hints are {@link ZoneUse} values rather than building ids on purpose: zoning is per floor
 * and player-assigned, so "somewhere with office floors" is a question the building data can already
 * answer, while "the office building" is not a thing that exists.
 */
public enum CitizenRole {

    /** Moving between home and work; the reason rush hour looks different from midday. */
    COMMUTER("commuter", ZoneUse.RESIDENTIAL, ZoneUse.OFFICE, 1.15D),

    /** On the clock and near their workplace: deliveries, site work, stepping out for air. */
    WORKER("worker", ZoneUse.OFFICE, ZoneUse.INDUSTRIAL, 1.00D),

    /** Out to spend money; clusters around commercial and entertainment frontage. */
    SHOPPER("shopper", ZoneUse.RESIDENTIAL, ZoneUse.COMMERCIAL, 0.90D),

    /** Going nowhere in particular. The only role that keeps a street from being empty at 4am. */
    IDLER("idler", ZoneUse.RESIDENTIAL, ZoneUse.PARK, 0.75D);

    private final String id;
    private final ZoneUse origin;
    private final ZoneUse destination;
    private final double paceMultiplier;

    CitizenRole(String id, ZoneUse origin, ZoneUse destination, double paceMultiplier) {
        this.id = id;
        this.origin = origin;
        this.destination = destination;
        this.paceMultiplier = paceMultiplier;
    }

    public String id() {
        return id;
    }

    /** The kind of floor this role tends to come from. */
    public ZoneUse origin() {
        return origin;
    }

    /** The kind of floor this role tends to be heading for; used to bias spawn placement. */
    public ZoneUse destination() {
        return destination;
    }

    /** Walk speed relative to the entity's base movement speed. Someone late for work moves. */
    public double paceMultiplier() {
        return paceMultiplier;
    }

    public static CitizenRole byId(String id, CitizenRole fallback) {
        for (CitizenRole role : values()) {
            if (role.id.equalsIgnoreCase(id) || role.name().equalsIgnoreCase(id)) {
                return role;
            }
        }
        return fallback;
    }
}
