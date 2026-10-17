/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.effects;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Path of a shooting star: a fast, almost horizontal flight slightly descending. The star leaves
 * glowing points every {@link #TRAIL_STEP} blocks, which then fade out by themselves.
 */
public final class ShootingStar {

    static final double TRAIL_STEP = 1.2;

    private final double x;
    private final double y;
    private final double z;
    private final double dx;
    private final double dy;
    private final double dz;
    private final double speed;
    private final double length;

    ShootingStar(double x, double y, double z, double dx, double dy, double dz, double speed, double length) {
        double norm = Math.sqrt(dx * dx + dy * dy + dz * dz);
        this.x = x;
        this.y = y;
        this.z = z;
        this.dx = dx / norm;
        this.dy = dy / norm;
        this.dz = dz / norm;
        this.speed = speed;
        this.length = length;
    }

    /**
     * A star above the player: start at {@code height} above {@code baseY}, horizontally 20 blocks to
     * {@code maxDistance} from the player, random direction, descending at 10–25 %.
     */
    public static ShootingStar plan(RandomGenerator random, double playerX, double baseY, double playerZ,
                                    double height, double maxDistance) {
        double angle = random.nextDouble(0, Math.PI * 2);
        double distance = random.nextDouble(20, Math.max(21, maxDistance));
        double heading = random.nextDouble(0, Math.PI * 2);
        return new ShootingStar(
                playerX + Math.cos(angle) * distance, baseY + height, playerZ + Math.sin(angle) * distance,
                Math.cos(heading), -random.nextDouble(0.10, 0.25), Math.sin(heading),
                random.nextDouble(5.0, 8.0), random.nextDouble(50, 100));
    }

    /** number of ticks of the flight */
    public int duration() {
        return (int) Math.ceil(length / speed);
    }

    /** trail points the star covers in the given tick (from 0) */
    public List<double[]> trail(int tick) {
        List<double[]> points = new ArrayList<>();
        double from = tick * speed;
        double to = Math.min(length, (tick + 1) * speed);
        for (double d = from; d < to; d += TRAIL_STEP) points.add(at(d));
        return points;
    }

    /** head of the star at the end of the tick */
    public double[] head(int tick) {
        return at(Math.min(length, (tick + 1) * speed));
    }

    private double[] at(double distance) {
        return new double[]{x + dx * distance, y + dy * distance, z + dz * distance};
    }
}
