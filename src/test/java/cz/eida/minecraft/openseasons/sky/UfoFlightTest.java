/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.sky;

import org.junit.jupiter.api.Test;

import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UfoFlightTest {

    private static double horizontal(UfoFlight.Pose p, double x, double z) {
        return Math.hypot(p.x() - x, p.z() - z);
    }

    @Test
    void flightCrossesOverTheAnchor() {
        for (long seed = 0; seed < 200; seed++) {
            UfoFlight f = UfoFlight.plan(new SplittableRandom(seed), 100, 64, -300, 150, 90, 0.3);
            UfoFlight.Pose start = f.pose();
            assertEquals(150, horizontal(start, 100, -300), 1e-6);
            assertEquals(154, start.y(), 1e-9);

            double closest = Double.MAX_VALUE;
            while (!f.finished()) {
                f.step();
                closest = Math.min(closest, horizontal(f.pose(), 100, -300));
            }
            UfoFlight.Pose end = f.pose();
            assertTrue(closest < 150 * 0.35, "seed " + seed + ": missed by " + closest);
            assertTrue(horizontal(end, 100, -300) > 150 * 0.6, "seed " + seed + ": ended close");
            // the end is on the other side than the start
            assertTrue(Math.hypot(end.x() - start.x(), end.z() - start.z()) > 150 * 1.5, "seed " + seed);
            assertEquals(154, end.y(), 1e-9);
        }
    }

    @Test
    void hoverSlowsDownToZeroAndResumes() {
        UfoFlight f = new UfoFlight(0, 100, 0, 0, 1.0, 0, 300, 100, 2 * UfoFlight.HOVER_EASE + 50);
        assertEquals(1.0, f.speedAt(99), 1e-9);
        assertEquals(1.0, f.speedAt(100), 1e-9);
        assertEquals(0.5, f.speedAt(100 + UfoFlight.HOVER_EASE / 2), 1e-9);
        assertEquals(0.0, f.speedAt(100 + UfoFlight.HOVER_EASE + 10), 1e-9);
        assertEquals(1.0, f.speedAt(100 + 2 * UfoFlight.HOVER_EASE + 50), 1e-9);

        double z = f.pose().z();
        for (int i = 0; i < 300; i++) f.step();
        // hovering takes roughly (acceleration + deceleration)/2 + hover out of 300 blocks
        double expected = 300 - UfoFlight.HOVER_EASE - 50;
        assertEquals(expected, f.pose().z() - z, 1.0);
    }

    @Test
    void localFrameFollowsMinecraftYaw() {
        UfoFlight.Pose south = new UfoFlight.Pose(0, 0, 0, 0);
        double[] nose = south.local(0, 0, 10);
        assertEquals(10, nose[2], 1e-9);
        double[] right = south.local(5, 0, 0);
        assertEquals(-5, right[0], 1e-9);
        assertEquals(0f, south.yaw());

        UfoFlight.Pose west = new UfoFlight.Pose(0, 0, 0, Math.PI / 2);
        assertEquals(-10, west.local(0, 0, 10)[0], 1e-9);
        assertEquals(90f, west.yaw(), 1e-4);
    }

    @Test
    void flightStaysWithinSight() {
        // server view distance 12 chunks; a client with a smaller view distance limits more
        assertEquals(192, SkyPhenomena.sight(12, 16), 1e-9);
        assertEquals(96, SkyPhenomena.sight(12, 6), 1e-9);
        assertEquals(192, SkyPhenomena.sight(12, 0), 1e-9);
        assertEquals(120, SkyPhenomena.flightHeight(120, 192), 1e-9);
        // view distance 6: higher than 62 blocks the craft would be in the fog
        assertEquals(96 * SkyPhenomena.MAX_HEIGHT_SHARE, SkyPhenomena.flightHeight(150, 96), 1e-9);
        double h = SkyPhenomena.flightHeight(120, 192);
        double r = SkyPhenomena.flightRadius(h, 192);
        // the start of the path (horizontally r, vertically h) is within the view distance
        assertTrue(Math.hypot(r, h) < 192);
        assertTrue(Math.hypot(r, h) > 192 * 0.9);
    }

    @Test
    void shape() {
        assertEquals(3, UfoShape.LIGHTS.stream().filter(l -> !l.blinking()).count());
        assertEquals(3, UfoShape.FORMATION.size());
        // the wings of the formation symmetric behind the leader
        assertEquals(-UfoShape.FORMATION.get(1)[0], UfoShape.FORMATION.get(2)[0], 1e-9);
        assertTrue(UfoShape.FORMATION.get(1)[2] < 0);
        int on = 0;
        for (int t = 0; t < 30; t++) if (UfoShape.redLightOn(t)) on++;
        assertEquals(8, on);
    }
}
