package com.branciho.citiesinlife.net.payload;

import com.branciho.citiesinlife.CitiesInLife;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

/**
 * What the editor wants done to one building.
 *
 * <p>One packet for every action rather than one per button, because they all need the same three
 * checks in front of them — creative, your city, your building — and three copies of a permission
 * check is three chances to write one of them wrong.
 *
 * @param structureId which building
 * @param action      what to do to it
 * @param name        the new name, for {@link Action#RENAME}
 * @param amount      the new figure, for the two capacity actions; -1 hands it back to the scanner
 */
public record EditStructurePayload(UUID structureId, String action, String name, int amount)
        implements CustomPacketPayload {

    /** Everything the editor can ask for. Unknown ids are dropped rather than guessed at. */
    public enum Action {
        RENAME("rename"),
        SET_RESIDENTS("residents"),
        SET_JOBS("jobs"),
        /** How much health the building has in total, not how much is left of it. */
        SET_HEALTH("health"),
        /** Extra output, in whatever this building's own units are. */
        SET_BOOST("boost"),
        /** All three figures back to whatever the box measures. */
        AUTOMATIC("automatic"),
        /** Walk the box again: fresh cell count, fresh mass. */
        REMEASURE("remeasure"),
        /** Health back to full, without waiting out the peacetime trickle. */
        REPAIR("repair"),
        /** Stand the player on top of it. */
        GOTO("goto"),
        /** Put {@code amount} citizens in it. */
        SPAWN("spawn");

        private final String id;

        Action(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public static Action byId(String id) {
            for (Action action : values()) {
                if (action.id.equals(id)) {
                    return action;
                }
            }
            return null;
        }
    }

    public static final int MAX_NAME = 48;

    public static final CustomPacketPayload.Type<EditStructurePayload> TYPE =
            new CustomPacketPayload.Type<>(CitiesInLife.id("edit_structure"));

    public static final StreamCodec<FriendlyByteBuf, EditStructurePayload> STREAM_CODEC =
            StreamCodec.ofMember(EditStructurePayload::write, EditStructurePayload::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeUUID(structureId);
        buf.writeUtf(action, 24);
        buf.writeUtf(name, MAX_NAME);
        buf.writeVarInt(amount);
    }

    private static EditStructurePayload read(FriendlyByteBuf buf) {
        return new EditStructurePayload(buf.readUUID(), buf.readUtf(24),
                buf.readUtf(MAX_NAME), buf.readVarInt());
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
