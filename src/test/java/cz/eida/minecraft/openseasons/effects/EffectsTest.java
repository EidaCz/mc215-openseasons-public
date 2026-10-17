/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.effects;

import cz.eida.minecraft.openseasons.calendar.CalendarDate;
import cz.eida.minecraft.openseasons.season.Season;
import cz.eida.minecraft.openseasons.season.WorldSeasonState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EffectsTest {

    private static final Optional<WorldSeasonState> SUMMER =
            Optional.of(new WorldSeasonState(true, Season.SUMMER, new CalendarDate(10, 7, 453)));

    @Test
    void rules() {
        assertTrue(EffectRules.active(true, Set.of(Season.SUMMER), SUMMER));
        assertFalse(EffectRules.active(false, Set.of(Season.SUMMER), SUMMER));
        assertFalse(EffectRules.active(true, Set.of(Season.FALL), SUMMER));
        assertFalse(EffectRules.active(true, Set.of(Season.SUMMER), Optional.empty()));
        assertFalse(EffectRules.active(true, Set.of(Season.SUMMER),
                Optional.of(new WorldSeasonState(false, Season.SUMMER, new CalendarDate(1, 7, 1)))));

        // season of the place (region with permanent-season, or empty with season-effects deny)
        assertTrue(EffectRules.activeAt(true, Set.of(Season.WINTER), Optional.of(Season.WINTER)));
        assertFalse(EffectRules.activeAt(true, Set.of(Season.WINTER), Optional.of(Season.SUMMER)));
        assertFalse(EffectRules.activeAt(true, Set.of(Season.WINTER), Optional.empty()));
        assertFalse(EffectRules.activeAt(false, Set.of(Season.WINTER), Optional.of(Season.WINTER)));

        assertFalse(EffectRules.night(6000));
        assertFalse(EffectRules.night(12999));
        assertTrue(EffectRules.night(13000));
        assertTrue(EffectRules.night(18000 + 24000L * 5));
        assertFalse(EffectRules.night(23000));

        SplittableRandom random = new SplittableRandom(1);
        for (int i = 0; i < 1000; i++) {
            assertFalse(EffectRules.roll(random, 0));
            assertTrue(EffectRules.roll(random, 100));
        }
        int hits = 0;
        for (int i = 0; i < 100_000; i++) if (EffectRules.roll(random, 5)) hits++;
        assertEquals(5000, hits, 400);
        assertTrue(EffectRules.AUTUMN_COLORS.contains(EffectRules.autumnColor(random)));
        // only brown, yellow and red: red outweighs blue and green does not beat red
        for (int c : EffectRules.AUTUMN_COLORS) {
            int r = c >> 16 & 0xFF, g = c >> 8 & 0xFF, b = c & 0xFF;
            assertTrue(r > b && r >= g, Integer.toHexString(c));
        }

        // leaves outside autumn: rare in summer, very rare in spring, never in winter; green
        assertEquals(0.0, EffectRules.offSeasonLeafFactor(Season.WINTER));
        assertTrue(EffectRules.offSeasonLeafFactor(Season.SPRING) < EffectRules.offSeasonLeafFactor(Season.SUMMER));
        assertTrue(EffectRules.offSeasonLeafFactor(Season.SUMMER) <= 0.1);
        assertTrue(EffectRules.GREEN_COLORS.contains(EffectRules.leafColor(random, false)));
        assertTrue(EffectRules.AUTUMN_COLORS.contains(EffectRules.leafColor(random, true)));

        // the whole night has one number, it changes in the evening
        assertEquals(EffectRules.nightIndex(13000), EffectRules.nightIndex(30000));
        assertEquals(EffectRules.nightIndex(13000) + 1, EffectRules.nightIndex(37000));
        assertEquals(EffectRules.nightIndex(36999), EffectRules.nightIndex(13000));
    }

    @Test
    void shootingStarFliesHighAndLeavesADenseTrail() {
        for (long seed = 0; seed < 100; seed++) {
            ShootingStar star = ShootingStar.plan(new SplittableRandom(seed), 0, 64, 0, 120, 80);
            int duration = star.duration();
            assertTrue(duration >= 7 && duration <= 20, "duration " + duration);
            double[] previous = null;
            int points = 0;
            for (int t = 0; t < duration; t++) {
                List<double[]> trail = star.trail(t);
                for (double[] p : trail) {
                    if (previous != null) {
                        double step = Math.hypot(Math.hypot(p[0] - previous[0], p[1] - previous[1]), p[2] - previous[2]);
                        assertTrue(step <= ShootingStar.TRAIL_STEP + 1e-6, "gap in the trail " + step);
                    }
                    previous = p;
                    points++;
                }
            }
            assertTrue(points >= 40, "too few points " + points);
            // starts at the flight height and descends
            assertEquals(184, star.trail(0).getFirst()[1], 1e-9);
            assertTrue(star.head(duration - 1)[1] < 184 && star.head(duration - 1)[1] > 184 - 30);
            double[] start = star.trail(0).getFirst();
            double distance = Math.hypot(start[0], start[2]);
            assertTrue(distance >= 20 && distance <= 80, "start " + distance);
        }
    }

    @Test
    void leafFallsSlowlyAndSways() {
        LeafFall leaf = LeafFall.start(new SplittableRandom(3), 10, 80, 10, 0.02, 0.0);
        double[] start = leaf.position(0);
        double[] later = leaf.position(100);
        assertTrue(later[1] < start[1] - 3 && later[1] > start[1] - 6.1, "descent " + (start[1] - later[1]));
        assertTrue(later[0] > start[0], "the wind carries it");
        for (int t = 0; t < LeafFall.MAX_TICKS; t++) {
            assertTrue(Math.abs(leaf.pitch(t)) <= 35.0001);
        }
    }
}
