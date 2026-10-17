/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.biome;

import cz.eida.minecraft.openseasons.config.ClientColorSettings;
import cz.eida.minecraft.openseasons.season.Season;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FoliagePatternAndFogTest {

    @Test
    void patternIsDeterministicPatchyAndUsesAllColors() {
        int[] counts = new int[5];
        for (int qx = -200; qx < 200; qx++) {
            for (int qz = -200; qz < 200; qz++) {
                int v = FoliagePattern.variant(qx, qz, 5);
                assertEquals(v, FoliagePattern.variant(qx, qz, 5));
                counts[v]++;
                // patches of 8 × 8 blocks (2 × 2 cells) have one colour
                assertEquals(v, FoliagePattern.variant(Math.floorDiv(qx, 2) * 2, Math.floorDiv(qz, 2) * 2, 5));
            }
        }
        for (int c : counts) assertTrue(c > 160_000 / 5 / 3, java.util.Arrays.toString(counts));
        assertEquals(0, FoliagePattern.variant(5, 7, 1));
    }

    private static final ClientColorSettings.MorningFog FOG = new ClientColorSettings.MorningFog(true, 100.0, 50.0, 16);

    @Test
    void fogOnlyInSecondHalfOfFallMornings() {
        assertEquals(MorningFog.NONE, MorningFog.layerTop(FOG, "world", 10, Season.SUMMER, 90, 0));
        assertEquals(MorningFog.NONE, MorningFog.layerTop(FOG, "world", 10, Season.FALL, 40, 0));
        assertEquals(8, MorningFog.layerTop(FOG, "world", 10, Season.FALL, 60, 22500));   // 4:30
        assertEquals(16, MorningFog.layerTop(FOG, "world", 10, Season.FALL, 60, 23500));  // 5:30
        assertEquals(16, MorningFog.layerTop(FOG, "world", 10, Season.FALL, 60, 1000));   // 7:00
        assertEquals(8, MorningFog.layerTop(FOG, "world", 10, Season.FALL, 60, 3000));    // 9:00
        assertEquals(4, MorningFog.layerTop(FOG, "world", 10, Season.FALL, 60, 4000));    // 10:00
        assertEquals(MorningFog.NONE, MorningFog.layerTop(FOG, "world", 10, Season.FALL, 60, 4500));
        assertEquals(MorningFog.NONE, MorningFog.layerTop(FOG, "world", 10, Season.FALL, 60, 12000));
        ClientColorSettings.MorningFog off = new ClientColorSettings.MorningFog(false, 100.0, 50.0, 16);
        assertEquals(MorningFog.NONE, MorningFog.layerTop(off, "world", 10, Season.FALL, 60, 1000));
    }

    @Test
    void foggyDaysFollowChance() {
        int foggy = 0;
        for (long day = 0; day < 10_000; day++) {
            if (MorningFog.foggyDay("world", day, 35.0)) foggy++;
            assertEquals(MorningFog.foggyDay("world", day, 35.0), MorningFog.foggyDay("world", day, 35.0));
        }
        assertTrue(foggy > 3200 && foggy < 3800, "foggy days " + foggy);
        assertFalse(MorningFog.foggyDay("world", 1, 0.0));
        assertTrue(MorningFog.foggyDay("world", 1, 100.0));
    }
}
