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

/**
 * Shape of the aurora made of particles: a row of vertical rays on the northern sky lying on a wave line,
 * slowly waving, changing height and brightness. The aurora stays at the same place relative to the player (like a real one,
 * which is so far away that it does not move while walking), and close enough so that the particles are not
 * invisibly small in the distance. Coordinates are relative to the player: x east, y up, z south (north is −z).
 */
public final class AuroraShape {

    public static final int RAYS = 48;

    /** distance of the curtain to the north, width of the curtain, height of the lower edge and the highest ray (blocks) */
    static final double DISTANCE = 38;
    static final double SPAN = 110;
    static final double BASE = 28;
    static final double MAX_HEIGHT = 26;

    /**
     * One ray: base (x, z, height), length, brightness 0–1 and whether it has a purple fringe at the top.
     */
    public record Ray(double x, double z, double baseY, double height, double intensity, boolean violetTop) {
    }

    private AuroraShape() {
    }

    /**
     * Rays at time {@code t} (ticks).
     *
     * @param seed randomness of a particular aurora (wave phase)
     */
    public static List<Ray> rays(double t, long seed) {
        double phase = Math.floorMod(seed, 1000L) / 1000.0 * Math.PI * 2;
        List<Ray> out = new ArrayList<>(RAYS);
        for (int i = 0; i < RAYS; i++) {
            double u = (i + 0.5) / RAYS;
            double x = -SPAN / 2 + u * SPAN;
            double z = -DISTANCE + 6 * Math.sin(u * 7.0 + t * 0.010 + phase);
            double baseY = BASE + 3 * Math.sin(u * 5.0 + t * 0.008);
            double height = MAX_HEIGHT * (0.6 + 0.4 * Math.sin(u * 11.0 - t * 0.017 + phase));
            // the edges of the curtain fade, each ray flickers differently
            double edge = Math.sin(Math.PI * u);
            double shimmer = 0.6 + 0.4 * Math.sin(t * 0.05 + i * 0.7 + phase);
            out.add(new Ray(x, z, baseY, height, edge * shimmer, i % 2 == 0));
        }
        return out;
    }
}
