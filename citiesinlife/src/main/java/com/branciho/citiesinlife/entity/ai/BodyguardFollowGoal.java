package com.branciho.citiesinlife.entity.ai;

import com.branciho.citiesinlife.entity.ServiceEntity;
import com.branciho.citiesinlife.service.ServiceType;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * A bodyguard walking in formation behind whoever hired them.
 *
 * <p>Not "follow the player": each guard has a station relative to their employer's back — a wedge,
 * fanned out and staggered by slot — and walks to <em>that</em> point rather than to the person.
 * Following the person is what produces the conga line every escort mob in every game produces,
 * where four guards pile into the same block and shove each other through walls. Following a point
 * on the ground means the detail keeps its shape, moves as a unit, and looks hired.
 *
 * <p>The station is behind the employer and rotates with them, so turning round turns the detail
 * round rather than making them run past you.
 */
public class BodyguardFollowGoal extends Goal {

    /** How far behind the employer the first rank stands. */
    private static final double RANK = 2.2D;

    /** How far apart, across the employer's back. */
    private static final double FILE = 1.5D;

    /** Close enough. Any tighter and they jostle each other trying to stand exactly right. */
    private static final double SETTLED = 1.4D;

    /** Beyond this they have lost their employer entirely and run rather than walk. */
    private static final double SPRINT = 8.0D;

    /** Beyond THIS, walking is hopeless and they are put back on station directly. */
    private static final double LOST = 48.0D;

    /** How often the path is redrawn. Every tick would be a pathfinder call at twenty hertz. */
    private static final int REPATH_INTERVAL = 8;

    private final ServiceEntity guard;
    private final double speed;
    private int untilRepath;

    public BodyguardFollowGoal(ServiceEntity guard, double speed) {
        this.guard = guard;
        this.speed = speed;
        setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public boolean canUse() {
        return guard.role() == ServiceType.BODYGUARD && employer() != null;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        untilRepath = 0;
    }

    @Override
    public void stop() {
        guard.getNavigation().stop();
    }

    @Override
    public void tick() {
        Player boss = employer();
        if (boss == null) {
            return;
        }
        guard.getLookControl().setLookAt(boss, 10.0F, guard.getMaxHeadXRot());

        Vec3 station = station(boss);
        double away = guard.position().distanceToSqr(station);

        // A guard who has been left behind by a boat, a portal or a very long sprint is put back
        // rather than left jogging across a continent. A detail that arrives an hour late is not a
        // detail.
        if (away > LOST * LOST) {
            guard.moveTo(station.x, station.y, station.z, boss.getYRot(), 0.0F);
            guard.getNavigation().stop();
            return;
        }
        if (away < SETTLED * SETTLED) {
            guard.getNavigation().stop();
            // On station: face the same way the boss is, which is what turns four people standing
            // near somebody into a formation.
            guard.setYRot(boss.getYRot());
            guard.setYBodyRot(boss.getYRot());
            return;
        }
        if (--untilRepath > 0) {
            return;
        }
        untilRepath = REPATH_INTERVAL;
        double urgency = away > SPRINT * SPRINT ? speed * 1.35D : speed;
        guard.getNavigation().moveTo(station.x, station.y, station.z, urgency);
    }

    /**
     * Where in the wedge this guard belongs.
     *
     * <p>Slots go 0, 1, 2, 3 as centre-right, centre-left, wide-right, wide-left, and the rank
     * steps back every two, so a four-strong detail is two ranks of two rather than a line four
     * wide that cannot get through a door.
     */
    private Vec3 station(Player boss) {
        int slot = Math.max(0, guard.formationSlot());
        int rank = 1 + slot / 2;
        double side = (slot % 2 == 0 ? 1.0D : -1.0D) * FILE * (1 + slot / 2 * 0.35D);

        float yaw = (float) Math.toRadians(boss.getYRot());
        double forwardX = -Math.sin(yaw);
        double forwardZ = Math.cos(yaw);
        // Right of the direction of travel, which on Minecraft's axes is the forward vector turned
        // ninety degrees.
        double rightX = forwardZ;
        double rightZ = -forwardX;

        return new Vec3(
                boss.getX() - forwardX * RANK * rank + rightX * side,
                boss.getY(),
                boss.getZ() - forwardZ * RANK * rank + rightZ * side);
    }

    private @org.jetbrains.annotations.Nullable Player employer() {
        return guard.employer();
    }
}
