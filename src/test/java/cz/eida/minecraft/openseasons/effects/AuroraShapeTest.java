/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.effects;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuroraShapeTest {

    @Test
    void raysHangNorthAndHigh() {
        for (int t = 0; t < 24000; t += 997) {
            List<AuroraShape.Ray> rays = AuroraShape.rays(t, 42);
            assertEquals(AuroraShape.RAYS, rays.size());
            for (AuroraShape.Ray r : rays) {
                assertTrue(r.z() < -25 && r.z() > -50, "the aurora is in the north: " + r.z());
                assertTrue(r.baseY() >= 20 && r.baseY() + r.height() <= 60, "height " + r.baseY());
                assertTrue(r.intensity() >= 0 && r.intensity() <= 1);
                assertTrue(Math.abs(r.x()) <= AuroraShape.SPAN / 2);
            }
        }
    }

    @Test
    void edgesFadeAndRaysMove() {
        List<AuroraShape.Ray> now = AuroraShape.rays(0, 7);
        assertTrue(now.getFirst().intensity() < now.get(AuroraShape.RAYS / 2).intensity() + 0.4);
        assertTrue(now.getFirst().intensity() < 0.1, "the edge should fade");
        List<AuroraShape.Ray> later = AuroraShape.rays(200, 7);
        assertTrue(Math.abs(now.get(10).z() - later.get(10).z()) > 0.01, "the curtain waves");
        assertTrue(Math.abs(now.get(10).height() - later.get(10).height()) > 0.01, "the rays change length");
    }
}
