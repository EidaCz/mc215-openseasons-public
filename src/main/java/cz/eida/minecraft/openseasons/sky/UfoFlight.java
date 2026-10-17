/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.sky;

import java.util.random.RandomGenerator;

/**
 * Path of one fly-over (also of a whole formation): heading,
 * slight turning, speed and height; the path is a pure function of these parameters, so the craft of a
 * formation stay aligned. In addition the craft may slow down and stop for a while in the middle of the fly-over.
 *
 * <p>The heading is in radians like the Minecraft yaw: 0 = south (+Z), π/2 = west (−X).
 */
public final class UfoFlight {

    /** point and heading of the craft in the given tick */
    public record Pose(double x, double y, double z, double heading) {

        /** yaw for an entity in degrees */
        public float yaw() {
            return (float) Math.toDegrees(heading);
        }

        /** point in craft coordinates: right, up, forward */
        public double[] local(double right, double up, double forward) {
            double fx = -Math.sin(heading);
            double fz = Math.cos(heading);
            double rx = -Math.cos(heading);
            double rz = -Math.sin(heading);
            return new double[]{x + rx * right + fx * forward, y + up, z + rz * right + fz * forward};
        }
    }

    /** how long slowing down to zero (and accelerating again) takes when stopping */
    static final int HOVER_EASE = 30;

    private final double speed;
    private final double turnRate;
    private final int duration;
    private final int hoverStart;
    private final int hoverLength;
    private double x;
    private final double y;
    private double z;
    private double heading;
    private int tick;

    UfoFlight(double x, double y, double z, double heading, double speed, double turnRate, int duration,
              int hoverStart, int hoverLength) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.heading = heading;
        this.speed = speed;
        this.turnRate = turnRate;
        this.duration = duration;
        this.hoverStart = hoverStart;
        this.hoverLength = hoverLength;
    }

    /**
     * Plans a fly-over above a point: start on a circle of radius {@code radius} around the point,
     * heading across the surroundings of the point (missing by up to a quarter of the radius), end at the same distance on the other side.
     *
     * @param height flight height above the point
     * @param hoverChance probability of stopping in the middle of the fly-over, 0–1
     */
    public static UfoFlight plan(RandomGenerator random, double anchorX, double anchorY, double anchorZ,
                                 double radius, double height, double hoverChance) {
        double angle = random.nextDouble(0, Math.PI * 2);
        // position on the circle; the heading points back to the centre
        double sx = anchorX - Math.sin(angle) * radius;
        double sz = anchorZ + Math.cos(angle) * radius;
        double miss = random.nextDouble(-0.25, 0.25) * radius;
        double toward = Math.atan2(-(anchorX - sx), anchorZ - sz) + Math.atan2(miss, radius);
        // 2.5–3.5 blocks per tick: with a view distance of 12 chunks a fly-over takes about 4–6 s (without stopping)
        double speed = random.nextDouble(2.5, 3.5);
        double turn = random.nextDouble(-0.0015, 0.0015);
        int travel = (int) Math.ceil(2 * radius / speed);
        int hoverLength = 0;
        int hoverStart = 0;
        if (random.nextDouble() < hoverChance) {
            hoverLength = 2 * HOVER_EASE + random.nextInt(40, 101);
            hoverStart = travel / 2 - hoverLength / 2;
        }
        return new UfoFlight(sx, anchorY + height, sz, toward, speed, turn, travel + hoverLength,
                hoverStart, hoverLength);
    }

    public Pose pose() {
        return new Pose(x, y, z, heading);
    }

    public int tick() {
        return tick;
    }

    public int duration() {
        return duration;
    }

    public boolean finished() {
        return tick >= duration;
    }

    /** speed in the given tick: full, smoothly to zero and back when stopping */
    double speedAt(int t) {
        if (hoverLength == 0 || t < hoverStart || t >= hoverStart + hoverLength) return speed;
        int intoHover = t - hoverStart;
        int untilEnd = hoverStart + hoverLength - t;
        double factor = Math.min(1.0, Math.min(intoHover, untilEnd) / (double) HOVER_EASE);
        return speed * (1.0 - factor);
    }

    /** moves the flight by one tick */
    public void step() {
        double v = speedAt(tick);
        heading += turnRate * (v / speed);
        x += -Math.sin(heading) * v;
        z += Math.cos(heading) * v;
        tick++;
    }
}
