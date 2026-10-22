package com.branciho.citiesinlife.client;

import com.branciho.citiesinlife.net.ClientCityCache;
import com.branciho.citiesinlife.net.payload.StructureSyncPayload;
import com.branciho.citiesinlife.sound.MachineSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;

/**
 * What a lived-in building sounds like from the street.
 *
 * <p>Every other sound in this update hangs off a block, because every other thing that makes a
 * noise <em>is</em> one. A residential block is not a block at all — it is a box the player drew
 * round a house they built by hand out of whatever they liked — so there is nothing to hang an
 * {@code animateTick} on, and this has to be driven from the client tick over the structures the
 * server has already told us are nearby.
 *
 * <p>Which turns out to be the better shape anyway: one building is one voice however many blocks
 * it is made of. Doing it per block would make a mansion forty times louder than a cottage.
 *
 * <p>An empty building is silent. Nobody lives in a house with no residents and nobody is working
 * a shop with no jobs filled, and both numbers are already in the sync — so the sound of a city
 * getting busier is something you can hear happening rather than something you read on a panel.
 */
public final class BuildingSounds {

    /** How far a building can be heard from. Roughly a street away. */
    private static final double RANGE = 46.0D;

    /** How often one of them speaks up. Averages to a voice somewhere every couple of seconds. */
    private static final int INTERVAL = 30;

    private static int countdown;

    private BuildingSounds() {
    }

    public static void tick(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null) {
            return;
        }
        if (--countdown > 0) {
            return;
        }
        RandomSource random = level.random;
        countdown = INTERVAL + random.nextInt(INTERVAL);

        // Gathered first, then one is picked. Speaking for every building in range at once would
        // turn a city centre into noise rather than into atmosphere.
        List<StructureSyncPayload.Entry> nearby = new ArrayList<>();
        for (StructureSyncPayload.Entry entry : ClientCityCache.structures()) {
            if (voice(entry) != Voice.NONE && within(player.getX(), player.getY(), player.getZ(), entry)) {
                nearby.add(entry);
            }
        }
        if (nearby.isEmpty()) {
            return;
        }
        speak(level, random, nearby.get(random.nextInt(nearby.size())));
    }

    /** Whether the player is close enough to the box to hear anything inside it. */
    private static boolean within(double x, double y, double z, StructureSyncPayload.Entry entry) {
        // Distance to the box rather than to its centre, so a long terrace is audible from the end
        // of it and a tower is audible from the pavement rather than only from halfway up.
        double dx = x - Mth.clamp(x, entry.minX(), entry.maxX() + 1);
        double dy = y - Mth.clamp(y, entry.minY(), entry.maxY() + 1);
        double dz = z - Mth.clamp(z, entry.minZ(), entry.maxZ() + 1);
        return dx * dx + dy * dy + dz * dz <= RANGE * RANGE;
    }

    private enum Voice { NONE, HOME, SHOP, OFFICE }

    /**
     * What this building has to say, if anything.
     *
     * <p>Matched on the type id rather than on the enum, because what the client is holding is the
     * flattened sync entry and re-resolving it to a {@code StructureType} on every building every
     * tick would be work to arrive back where we started.
     */
    private static Voice voice(StructureSyncPayload.Entry entry) {
        return switch (entry.typeId()) {
            case "residential" -> entry.residents() > 0 ? Voice.HOME : Voice.NONE;
            case "commercial" -> entry.jobs() > 0 ? Voice.SHOP : Voice.NONE;
            case "business" -> entry.jobs() > 0 ? Voice.OFFICE : Voice.NONE;
            default -> Voice.NONE;
        };
    }

    private static void speak(ClientLevel level, RandomSource random,
                              StructureSyncPayload.Entry entry) {
        // Somewhere inside the building rather than at its centre, so a big one sounds like a big
        // one: walk past a block of flats and the noise moves along the front of it.
        double x = entry.minX() + random.nextDouble() * (entry.maxX() - entry.minX() + 1);
        double y = entry.minY() + random.nextDouble() * (entry.maxY() - entry.minY() + 1);
        double z = entry.minZ() + random.nextDouble() * (entry.maxZ() - entry.minZ() + 1);

        switch (voice(entry)) {
            case HOME -> {
                // Muffled, because you are outside it. Pitched down and kept quiet is most of
                // what "heard through a wall" sounds like.
                MachineSounds.at(level, x, y, z, SoundEvents.VILLAGER_AMBIENT, 0.18F,
                        0.7F + random.nextFloat() * 0.2F);
                if (random.nextInt(4) == 0) {
                    MachineSounds.at(level, x, y, z, SoundEvents.WOODEN_DOOR_OPEN, 0.22F, 1.0F);
                }
            }
            case SHOP -> {
                MachineSounds.at(level, x, y, z, SoundEvents.NOTE_BLOCK_BELL.value(), 0.16F,
                        1.5F + random.nextFloat() * 0.3F);
                if (random.nextInt(2) == 0) {
                    MachineSounds.at(level, x, y, z, SoundEvents.VILLAGER_AMBIENT, 0.16F,
                            0.9F + random.nextFloat() * 0.3F);
                }
            }
            case OFFICE -> {
                int keys = 2 + random.nextInt(3);
                for (int i = 0; i < keys; i++) {
                    MachineSounds.at(level, x, y, z, SoundEvents.WOODEN_BUTTON_CLICK_ON, 0.14F,
                            1.6F + random.nextFloat() * 0.35F);
                }
            }
            default -> {
            }
        }
    }
}
