/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.effects;

import java.util.random.RandomGenerator;

/**
 * Fall of a big leaf: slow descent, drifting with the wind and swaying from side to side; the leaf
 * turns and tilts meanwhile. The position is a pure function of the time since it came off.
 */
public final class LeafFall {

    /** at most this many ticks, then the leaf vanishes even if it has not landed */
    public static final int MAX_TICKS = 300;

    private final double x;
    private final double y;
    private final double z;
    private final double windX;
    private final double windZ;
    private final double fallSpeed;
    private final double swayPhase;
    private final double spin;

    LeafFall(double x, double y, double z, double windX, double windZ, double fallSpeed, double swayPhase, double spin) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.windX = windX;
        this.windZ = windZ;
        this.fallSpeed = fallSpeed;
        this.swayPhase = swayPhase;
        this.spin = spin;
    }

    /** leaf come off below a leaf block; the wind is shared by all leaves nearby */
    public static LeafFall start(RandomGenerator random, double x, double y, double z, double windX, double windZ) {
        return new LeafFall(x, y, z, windX, windZ, random.nextDouble(0.035, 0.06), random.nextDouble(0, Math.PI * 2),
                random.nextDouble(-6, 6));
    }

    /** position at tick {@code t} */
    public double[] position(int t) {
        double sway = Math.sin(swayPhase + t * 0.12) * 0.6;
        return new double[]{
                x + windX * t + sway * Math.cos(swayPhase),
                y - fallSpeed * t,
                z + windZ * t + sway * Math.sin(swayPhase)};
    }

    /** rotation around the vertical axis in degrees */
    public float yaw(int t) {
        return (float) (Math.toDegrees(swayPhase) + spin * t);
    }

    /** tilt of the leaf in degrees, swaying with the swing */
    public float pitch(int t) {
        return (float) (Math.cos(swayPhase + t * 0.12) * 35);
    }
}
