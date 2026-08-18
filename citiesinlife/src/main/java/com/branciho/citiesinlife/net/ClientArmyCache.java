package com.branciho.citiesinlife.net;

import com.branciho.citiesinlife.net.payload.ArmySyncPayload;

/**
 * The last army roll the server sent.
 *
 * <p>Separate from {@link ClientCityCache} for the same reason that one exists at all: it is filled
 * from a handler registered in common code, so it must not so much as mention a client class. It
 * simply sits empty on a dedicated server.
 */
public final class ClientArmyCache {

    private static ArmySyncPayload army = ArmySyncPayload.none();

    /** Bumped on every packet, so an open screen knows to rebuild its buttons. */
    private static int revision;

    private ClientArmyCache() {
    }

    public static void accept(ArmySyncPayload payload) {
        army = payload;
        revision++;
    }

    public static ArmySyncPayload army() {
        return army;
    }

    public static int revision() {
        return revision;
    }

    public static void clear() {
        army = ArmySyncPayload.none();
        revision++;
    }
}
